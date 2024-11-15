package proxy

import dns.Resolver
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import proxy.types.AddressType
import proxy.types.CommandType
import proxy.types.VersionType
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext

class ClientHandler(
    private val resolver: Resolver,
    private val selectorManager: SelectorManager,
    private val context: CoroutineContext = Dispatchers.IO
) {
    suspend fun handle(clientSocket: Socket) {
        val input = clientSocket.openReadChannel()
        val output = clientSocket.openWriteChannel(autoFlush = true)

        println("Client connected: ${clientSocket.remoteAddress}")

        val version = VersionType.byCode(input.readByte())
        val nMethods = input.readByte().toInt()
        val methods = ByteArray(nMethods) { input.readByte() }

        if (version != VersionType.SOCKS5 || SOCKS5_NO_AUTH_METHOD !in methods) {
            sendGreeting(output, success = false)
            clientSocket.closeInContext(context)
            println("Unsupported SOCKS version or authentication method")
            return
        }

        sendGreeting(output, success = true)

        input.readByte() //read reserved byte
        val commandCode = CommandType.byCode(input.readByte())
        input.readByte()
        val addressType = AddressType.byCode(input.readByte())
        var targetHost: String
        when (addressType) {
            AddressType.IPV4 -> {
                val address = ByteArray(4)
                input.readFully(address, 0, address.size)
                targetHost = withContext(context) {
                    InetAddress.getByAddress(address)
                }.hostAddress
                println("ipv4 - $targetHost")
            }

            AddressType.DOMAIN -> {
                val len = input.readByte()
                val domainName = ByteArray(len.toInt())
                input.readFully(domainName, 0, domainName.size)
                targetHost = String(domainName, StandardCharsets.UTF_8)
                val targetHostAddress = resolver.resolve(targetHost)
                if (targetHostAddress == null) {
                    println("DNS resolution failed for $targetHost")
                    sendSocksResponse(output, replyCode = VersionType.SOCKS5.unreachableHostCode)
                    clientSocket.closeInContext(context)
                    return
                }
                targetHost = targetHostAddress
                println("domain - $targetHost")
            }

            AddressType.IPV6 -> {
                println("Unsupported address type")
                sendSocksResponse(output, replyCode = SOCKS5_ADDR_NOT_SUPPORTED)
                clientSocket.closeInContext(context)
                return
            }
        }

        val targetPort: Int = input.readShort(ByteOrder.BIG_ENDIAN).toInt()

        when (commandCode) {
            CommandType.CONNECT -> {
                try {
                    println("CONNECT command: connecting to $targetHost:$targetPort")
                    val targetSocket = aSocket(selectorManager).tcp().connect(targetHost, targetPort)

                    sendSocksResponse(output, replyCode = VersionType.SOCKS5.successCode, localAddress = targetSocket.localAddress)

                    println("starting messaging")
                    withContext(context) {
                        val clientToTarget =
                            async {
                                println("clientToTarget")
                                input.copyToWithLogging(targetSocket.openWriteChannel(autoFlush = true))
                            }
                        val targetToClient =
                            async {
                                println("targetToClient")
                                targetSocket.openReadChannel().copyToWithLogging(output)
                            }
                        clientToTarget.await()
                        targetToClient.await()
                    }
                    println("Connection closed with $targetHost:$targetPort")
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendSocksResponse(output, replyCode = SOCKS5_GENERAL_FAILURE)
                }
            }

            CommandType.BIND -> {
                try {
                    println("BIND command: binding to $targetHost:$targetPort")
                    val serverSocket = aSocket(selectorManager).tcp().bind()

                    sendSocksResponse(output, replyCode = VersionType.SOCKS5.successCode, localAddress = serverSocket.localAddress)

                    val incomingSocket = serverSocket.accept()
                    withContext(context) {
                        val clientToIncoming =
                            async {
                                println("clientToIncoming")
                                input.copyToWithLogging(incomingSocket.openWriteChannel(autoFlush = true))
                            }
                        val incomingToClient =
                            async {
                                println("incomingToClient")
                                incomingSocket.openReadChannel().copyToWithLogging(output)
                            }
                        clientToIncoming.await()
                        incomingToClient.await()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendSocksResponse(output, replyCode = SOCKS5_GENERAL_FAILURE)
                }

            }

            CommandType.UDP_ASSOCIATE -> {
                println("unsupported command")
                sendSocksResponse(output, replyCode = SOCKS5_UNSUPPORTED)
                clientSocket.closeInContext(context)
                return
            }
        }
        clientSocket.closeInContext(context)
    }

    private suspend fun sendGreeting(output: ByteWriteChannel, success: Boolean) {
        output.writeByte(VersionType.SOCKS5.code)
        output.writeByte(if (success) VersionType.SOCKS5.successCode else SOCKS5_NO_ACCEPTABLE_METHODS)
    }

    private suspend fun sendSocksResponse(
        output: ByteWriteChannel,
        replyCode: Byte,
        localAddress: SocketAddress? = null
    ) {
        output.writeByte(VersionType.SOCKS5.code)
        output.writeByte(replyCode)
        output.writeByte(SOCKS5_RESERVED)
        output.writeByte(SOCKS5_IPV4_TYPE)


        if (localAddress is InetSocketAddress) {
            val addressBytes = withContext(Dispatchers.IO) {
                InetAddress.getByName(localAddress.hostname)
            }.address
            output.writeFully(addressBytes, 0, addressBytes.size)
            output.writeShort(localAddress.port.toShort())
        } else {
            output.writeFully(byteArrayOf(0, 0, 0, 0), 0, 4)
            output.writeShort(0)
        }
    }

    private suspend fun ByteReadChannel.copyToWithLogging(output: ByteWriteChannel) {
        val buffer = ByteArray(8192 * 8)
        while (!isClosedForRead) {
            val bytesRead = readAvailable(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                println("Read $bytesRead bytes")
                output.writeFully(buffer, 0, bytesRead)
                output.flush()
                println("Wrote $bytesRead bytes")
            }
        }
    }

    private suspend fun Socket.closeInContext(context: CoroutineContext) {
        withContext(context) {
            close()
        }
    }
}

private const val SOCKS5_RESERVED: Byte = 0x00
private const val SOCKS5_UNSUPPORTED: Byte = 0x07
private const val SOCKS5_NO_ACCEPTABLE_METHODS: Byte = 0xFF.toByte()
private const val SOCKS5_NO_AUTH_METHOD: Byte = 0x00
private const val SOCKS5_ADDR_NOT_SUPPORTED: Byte = 0x08
private const val SOCKS5_GENERAL_FAILURE: Byte = 0x01
private const val SOCKS5_IPV4_TYPE: Byte = 0x01