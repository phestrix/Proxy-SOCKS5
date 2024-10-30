package server.socks


import server.socks.types.SOCKSAddressType
import server.socks.types.SOCKSCommandType
import server.socks.types.SOCKSVersion
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import io.ktor.util.network.address
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import server.socks.exception.SOCKSException
import utils.withPort
import java.lang.Byte.toUnsignedInt
import java.lang.Short.toUnsignedInt
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress


@Suppress("BlockingMethodInNonBlockingContext")
internal class SOCKSHandshake(
    private val reader: ByteReadChannel,
    private val writer: ByteWriteChannel,
    private val config: SOCKSConfig,
    private val selector: SelectorManager
) {
    private lateinit var selectedVersion: SOCKSVersion
    lateinit var hostSocket: Socket
    private val emptyAddress = InetSocketAddress("0,0,0,0", 0)
    private val logger: Logger = KtorSimpleLogger("SOCKSHandshake")

    suspend fun negotiate() {
        logger.info("Negotiating with client")
        selectedVersion = reader.readVersion()
        when (selectedVersion) {
            SOCKSVersion.SOCKS5 -> {
                handleAuthentication()
                check(reader.readVersion() == selectedVersion) { "Inconsistent version" }

            }

            SOCKSVersion.SOCKS4 -> {
                if (!config.allowSOCKS4) {
                    sendFullReply(91.toByte())
                    throw SOCKSException("SOCKS4 connection not allowed")
                }
            }
        }
        val request = receiveRequest()
        logger.info("Received request: $request")
        when (request.command) {
            SOCKSCommandType.CONNECT -> connect(request)
            SOCKSCommandType.BIND -> bind(request)
            SOCKSCommandType.UDP_ASSOCIATE -> {
                check(selectedVersion == SOCKSVersion.SOCKS5) { "UDP associate is only supported in SOCKS5 and not in our task" }
                sendFullReply(7.toByte())
                throw SOCKSException("UDP associate is not supported")
            }

        }
    }

    private suspend fun receiveRequest(): SOCKSRequest {
        val command = reader.readCommand()
        reader.readByte()
        val addressWithOutPort = reader.readAddress()
        val port = reader.readShort()
        val address = InetSocketAddress(addressWithOutPort.hostname, port.toInt())
        return SOCKSRequest(command, address, toUnsignedInt(port))
    }

    private suspend fun handleAuthentication() {
        val methodsCount = reader.readByte()
        val methods = List(methodsCount.toInt()) { toUnsignedInt(reader.readByte()) }
        val commonMethod = config.authMethods.firstOrNull { it.code in methods }
        if (commonMethod == null) {
            sendPartialReply(SOCKS5_NO_ACCEPTABLE_METHODS)
            throw SOCKSException("No common authentication method found")
        } else {
            sendPartialReply(commonMethod.code.toByte())
            commonMethod.negotiate(reader, writer)
        }
    }

    private suspend fun connect(request: SOCKSRequest) {
        val host = InetSocketAddress(request.address.toJavaAddress().address, request.port)
        logger.info("Connecting to $host")
        hostSocket = try {
            aSocket(selector).tcp().connect(host)
        } catch (cause: Throwable) {
            sendFullReply(selectedVersion.unreachableHostCode)
            throw SOCKSException("Failed to connect to host", cause)
        }
        try {
            sendFullReply(selectedVersion.successCode, host)
        } catch (cause: Throwable) {
            hostSocket.close()
            throw cause
        }
    }


    private suspend fun bind(request: SOCKSRequest) {
        hostSocket = coroutineScope {
            val address = config.networkAddress.withPort(selector, 0).localAddress
            logger.info("Binding to $address")
            aSocket(selector).tcp().bind().use { serverSocket ->
                val socketJob = async {
                    try {
                        withTimeout(TIME_TO_WAIT) {
                            serverSocket.accept()
                        }
                    } catch (cause: Throwable) {
                        sendFullReply(selectedVersion.unreachableHostCode)
                        throw SOCKSException("Failed to accept connection", cause)
                    }
                }
                sendFullReply(selectedVersion.successCode, address)
                socketJob.await()
            }
        }

        val hostAddress = hostSocket.localAddress
        if (hostAddress != request.address) {
            sendFullReply(selectedVersion.connectionRefusedCode)
            hostSocket.close()
            throw SOCKSException("Host address mismatch requested: ${request.address}, actual: $hostAddress")
        }
        try {
            sendFullReply(selectedVersion.successCode, hostAddress)
        } catch (cause: Throwable) {
            hostSocket.close()
            throw cause
        }
    }

    private suspend fun sendPartialReply(code: Byte, writeAdditionalData: suspend BytePacketBuilder.() -> Unit = {}) {
        writer.writePacket {
            writeByte(selectedVersion.replyVersion)
            writeByte(code)
            writeAdditionalData()
        }
        writer.flush()
    }

    private suspend fun sendFullReply(code: Byte, address: SocketAddress = emptyAddress) {
        sendPartialReply(code) {
            if (selectedVersion == SOCKSVersion.SOCKS5) writeByte(SOCKS_RESERVED)
            writeAddress(address)
        }
    }

    private suspend fun ByteReadChannel.readVersion(): SOCKSVersion {
        val version = readByte()
        return SOCKSVersion.byCode(version)

    }

    private suspend fun ByteReadChannel.readCommand(): SOCKSCommandType {
        val code = readByte()
        return SOCKSCommandType.byCode(code)
    }

    private suspend fun ByteReadChannel.readAddress(): InetSocketAddress {
        val addressType = when (selectedVersion) {
            SOCKSVersion.SOCKS4 -> SOCKSAddressType.IPv4
            SOCKSVersion.SOCKS5 -> SOCKSAddressType.byCode(readByte())
        }
        return when (addressType) {
            SOCKSAddressType.IPv4 -> {
                val data = readPacket(4)
                InetSocketAddress(data.readBytes().joinToString(".") { toUnsignedInt(it).toString() }, 0)
            }

            SOCKSAddressType.IPv6 -> {
                val data = readPacket(16)
                InetSocketAddress(data.readBytes().joinToString(".") { toUnsignedInt(it).toString() }, 0)
            }

            SOCKSAddressType.Domain -> {
                val size = toUnsignedInt(readByte())
                val data = readPacket(size)
                InetSocketAddress(data.readBytes().decodeToString(), 0)
            }
        }
    }

    private fun BytePacketBuilder.writeAddress(address: SocketAddress) {
        val port = address.toJavaAddress().port.toShort()
        var ip = address.toJavaAddress().address
        when (selectedVersion) {
            SOCKSVersion.SOCKS4 -> {}
            SOCKSVersion.SOCKS5 -> {
                when (ip) {
                    is Inet4Address -> {
                        writeByte(SOCKSAddressType.IPv4.code)
                    }

                    is Inet6Address -> {
                        writeByte(SOCKSAddressType.IPv6.code)
                    }

                    else -> {
                        writeByte(SOCKSAddressType.Domain.code)
                        ip = InetAddress.getByName(ip.toString()).hostAddress.toString()
                    }
                }
                writeFully(ip.toByteArray())
                writeShort(port)
            }
        }
    }


    private data class SOCKSRequest(
        val command: SOCKSCommandType,
        val address: InetSocketAddress,
        val port: Int
    )
}

const val SOCKS_RESERVED = 0x00.toByte()
const val TIME_TO_WAIT = 120000.toLong()
const val SOCKS5_NO_ACCEPTABLE_METHODS = 0xFF.toByte()