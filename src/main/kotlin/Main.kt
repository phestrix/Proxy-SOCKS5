import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import server.ServerSocks
import server.socks.SOCKSConfigBuilder

fun main() {
    val server = ServerSocks(
        config = SOCKSConfigBuilder().build(
            selector = SelectorManager(Dispatchers.IO)
        ),
        context = Dispatchers.IO
    )
    server.start()

}