package server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.relayData


//unused
class ClientHandler(private val clientSocket: Socket) {
    suspend fun handle() {
        withContext(Dispatchers.IO) {
            val input = clientSocket.openReadChannel()
            val output = clientSocket.openWriteChannel()


            val rawData = ByteArray(2)
            input.readFully(rawData)
            println("Received greeting: ${rawData.joinToString()}")
            val version = rawData[0]
            val numberOfMethods = rawData[1].toUByte()
            val methods = ByteArray(numberOfMethods.toInt())
            input.readFully(methods)

            println("version = $version, numberOfMethods = $numberOfMethods, methods = ${methods.joinToString()}")

            output.writeByte(0x05)
            output.writeByte(0x00)

            val requestVersion = input.readByte()
            val command = input.readByte()
            val reserved = input.readByte()
            val addressType = input.readByte()

            println("requestVersion = $requestVersion, command = $command, reserved = $reserved, addressType = $addressType")

            val address = when (addressType.toInt()) {
                0x01 -> {
                    val addressBytes = ByteArray(4)
                    input.readFully(addressBytes)
                    InetSocketAddress(
                        addressBytes.joinToString(".") { it.toUByte().toString() },
                        input.readShort().toInt()
                    )
                }

                0x03 -> {
                    val domainLength = input.readByte().toInt()
                    val domainBytes = ByteArray(domainLength)
                    input.readFully(domainBytes)
                    InetSocketAddress(String(domainBytes), input.readShort().toInt())
                }

                0x04 -> {
                    val addressBytes = ByteArray(16)
                    input.readFully(addressBytes)
                    InetSocketAddress(
                        addressBytes.joinToString(":") { it.toUByte().toString() },
                        input.readShort().toInt()
                    )
                }

                else -> throw IllegalArgumentException("Unsupported address type: $addressType")
            }

            when (command.toInt()) {
                0x01 -> {
                    val remoteSocket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(address)
                    output.writeByte(0x05)
                    output.writeByte(0x00)
                    output.writeByte(0x00)
                    output.writeByte(0x01)
                    output.writeFully(ByteArray(6), 0, 6)


                    relayData(clientSocket, remoteSocket)
                }

                else -> throw IllegalArgumentException("Unsupported command: $command")
            }
        }
    }

    private fun checkGreetingBytes(greetingBytes: ByteArray) {
        if (greetingBytes[0].toInt() != 0x05) {
            throw IllegalArgumentException("Unsupported SOCKS version: ${greetingBytes[0]}")
        }

        if (greetingBytes[1].toInt() != 0x01) {
            throw IllegalArgumentException("Unsupported number of methods: ${greetingBytes[1]}")
        }
    }
}