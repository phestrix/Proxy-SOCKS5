package utils

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun relayData(clientSocket: Socket, remoteSocket: Socket) = coroutineScope {
    val clientRemote = launch {
        clientSocket.openReadChannel().copyAndClose(remoteSocket.openWriteChannel(autoFlush = true))
    }
    val remoteClient = launch {
        remoteSocket.openReadChannel().copyAndClose(clientSocket.openWriteChannel(autoFlush = true))
    }
    clientRemote.join()
    remoteClient.join()
}

suspend fun ByteReadChannel.copyAndClose(dest: ByteWriteChannel) {
    try {
        while (!isClosedForRead) {
            val packet = readRemaining(DEFAULT_BUFFER_SIZE.toLong()).readBytes()
            dest.writeFully(packet, 0, packet.size)
        }
    } finally {
        dest.close()
    }
}

internal fun ServerSocket.withPort(selector: SelectorManager, port: Int) =
    aSocket(selector).tcp().bind("localhost", port)

internal inline fun <C : ReadWriteSocket, R> C.useWithChannels(
    autoFlush: Boolean = false,
    block: (C, ByteReadChannel, ByteWriteChannel) -> R
): R {
    val reader = openReadChannel()
    val writer = openWriteChannel(autoFlush)
    var cause: Throwable? = null
    return try {
        block(this, reader, writer)
    } catch (e: Throwable) {
        cause = e
        throw e
    } finally {
        reader.cancel(cause)
        writer.close(cause)
        close()
    }
}