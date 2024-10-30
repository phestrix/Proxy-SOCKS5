package server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.use
import io.ktor.utils.io.joinTo
import kotlinx.coroutines.*
import server.socks.SOCKSConfig
import server.socks.SOCKSHandshake
import utils.useWithChannels
import kotlin.coroutines.CoroutineContext

class ServerSocks internal constructor(private val config: SOCKSConfig, val context: CoroutineContext) :
    CoroutineScope {
    private val selector = ActorSelectorManager(Dispatchers.IO)
    override val coroutineContext = context + SupervisorJob(context[Job]) + CoroutineName("Server")
    private val logger = KtorSimpleLogger("Server")

    fun start() = runBlocking {
        val serverSocket = config.networkAddress
        logger.info("Server started at ${serverSocket.localAddress}")
        while(true){
            val clientSocket = serverSocket.accept()
            val clientName = clientSocket.remoteAddress
            launchClientJob(clientSocket).invokeOnCompletion {
                println("Closed $clientName")
            }
        }
    }

    private fun launchClientJob(clientSocket: Socket) = launch {
        clientSocket.useWithChannels { _, reader, writer ->
            val handshake = SOCKSHandshake(reader, writer, config, selector)
            handshake.negotiate()
            handshake.hostSocket.useWithChannels { _, hostReader, hostWriter ->
                coroutineScope {
                    relayApplicationData(reader, hostWriter)
                    relayApplicationData(hostReader, writer)
                }
            }
        }
    }

    private fun relayApplicationData(src: ByteReadChannel, dst: ByteWriteChannel) {
        launch {
            try {
                src.joinTo(dst, false)
            } catch (_: Throwable) {
                ///
            }

        }
    }
}