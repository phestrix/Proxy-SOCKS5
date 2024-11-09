package proxy

import dns.Resolver
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.ByteOrder
import io.ktor.utils.io.readShort
import kotlinx.coroutines.*
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class ClientHandler(
    private val resolver: Resolver,
    private val selectorManager: SelectorManager
) {
    suspend fun handle(clientSocket: Socket) {
        val input = clientSocket.openReadChannel()
        val output = clientSocket.openWriteChannel(autoFlush = true)

        println("Client connected: ${clientSocket.remoteAddress}")

        val version = input.readByte().toInt()
        val nMethods = input.readByte().toInt()
        val methods = ByteArray(nMethods) { input.readByte() }

        if (version != 5 || 0.toByte() !in methods) {
            sendGreeting(output, success = false)
            clientSocket.close()
            println("Unsupported SOCKS version or authentication method")
            return
        }

        sendGreeting(output, success = true)

        val commandVersion = input.readByte().toInt()
        val commandCode = input.readByte().toInt()
        input.readByte()
        val addressType = input.readByte().toInt()
        var targetHost: String
        when (addressType) {
            1 -> {
                val address = ByteArray(4)
                input.readFully(address, 0, address.size)
                targetHost = InetAddress.getByAddress(address).hostAddress
                println("ipv4 - $targetHost")
            }

            3 -> {
                val len = input.readByte()
                val domainName = ByteArray(len.toInt())
                input.readFully(domainName, 0, domainName.size)
                targetHost = String(domainName, StandardCharsets.UTF_8)
                val targetHostAddress = resolver.resolve(targetHost)
                if (targetHostAddress == null) {
                    println("DNS resolution failed for $targetHost")
                    sendSocksResponse(output, replyCode = 0x04)
                    clientSocket.close()
                    return
                }
                targetHost = targetHostAddress
                println("domain - $targetHost")
            }

            else -> {
                println("Unsupported address type")
                sendSocksResponse(output, replyCode = 0x08)
                clientSocket.close()
                return
            }
        }

        val targetPort: Int = input.readShort(ByteOrder.BIG_ENDIAN).toInt()

        when (commandCode) {
            0x01 -> {
                try {
                    println("CONNECT command: connecting to $targetHost:$targetPort")
                    val targetSocket = aSocket(selectorManager).tcp().connect(targetHost, targetPort)

                    sendSocksResponse(output, replyCode = 0x00, localAddress = targetSocket.localAddress)

                    println("starting messaging")
                    withContext(Dispatchers.IO) {
                        val clientToTarget =
                            async { println("clientToTarget");
                                input.copyToWithLogging(targetSocket.openWriteChannel(autoFlush = true)) }
                        val targetToClient =
                            async { println("targetToClient");
                                targetSocket.openReadChannel().copyToWithLogging(output) }
                        clientToTarget.await()
                        targetToClient.await()
                    }
                    println("Connection closed with $targetHost:$targetPort")
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendSocksResponse(output, replyCode = 0x01)
                }
            }

            0x02 -> {
                try {
                    println("BIND command: binding to $targetHost:$targetPort")
                    val serverSocket = aSocket(selectorManager).tcp().bind()

                    sendSocksResponse(output, replyCode = 0x00, localAddress = serverSocket.localAddress)

                    val incomingSocket = serverSocket.accept()
                    withContext(Dispatchers.IO) {
                        val clientToIncoming =
                            async { println("clientToIncoming");
                                input.copyToWithLogging(incomingSocket.openWriteChannel(autoFlush = true)) }
                        val incomingToClient =
                            async {println("incomingToClient");
                                incomingSocket.openReadChannel().copyToWithLogging(output) }
                        clientToIncoming.await()
                        incomingToClient.await()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendSocksResponse(output, replyCode = 0x01)
                }

            }

            else -> {
                println("unsupported command")
                sendSocksResponse(output, replyCode = 0x07)
                clientSocket.close()
                return
            }
        }
        clientSocket.close()
    }

    suspend fun sendGreeting(output: ByteWriteChannel, success: Boolean) {
        output.writeByte(0x05)
        output.writeByte(if (success) 0x00.toByte() else 0xFF.toByte())
    }

    suspend fun sendSocksResponse(output: ByteWriteChannel, replyCode: Int, localAddress: SocketAddress? = null) {
        output.writeByte(0x05)
        output.writeByte(replyCode.toByte())
        output.writeByte(0x00)
        output.writeByte(0x01)


        if (localAddress is InetSocketAddress) {
            val addressBytes = InetAddress.getByName(localAddress.hostname).address
            output.writeFully(addressBytes, 0, addressBytes.size)
            output.writeShort(localAddress.port.toShort())
        } else {
            output.writeFully(byteArrayOf(0, 0, 0, 0), 0, 4)
            output.writeShort(0)
        }
    }
    suspend fun ByteReadChannel.copyToWithLogging(output: ByteWriteChannel) {
        val buffer = ByteArray(8192*8)
        while (!isClosedForRead) {
            val bytesRead = readAvailable(buffer,0,buffer.size)
            if (bytesRead > 0) {
                println("Read $bytesRead bytes")
                output.writeFully(buffer, 0, bytesRead)
                output.flush()
                println("Wrote $bytesRead bytes")
            }
        }
    }
}