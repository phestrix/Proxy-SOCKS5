package proxy

import dns.Resolver
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun startProxy(host: String, port: Int) {
    val selectorManager = ActorSelectorManager(Dispatchers.IO)
    val serverSocket = aSocket(selectorManager).tcp().bind(hostname = host, port = port)
    val clientHandler = ClientHandler(Resolver("1.1.1.1", selectorManager), selectorManager)
    println("SOCKS5 Proxy Server is listening on $host:$port")

    coroutineScope {
        while (true) {
            val clientSocket = serverSocket.accept()
            println("Accepted connection from ${clientSocket.remoteAddress}")
            launch {
                try {
                    clientHandler.handle(clientSocket)
                } catch (e: Exception) {
                    println("Error handling client: ${e.message}")
                }
            }
        }
    }
}