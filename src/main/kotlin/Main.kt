import kotlinx.coroutines.runBlocking
import proxy.startProxy


fun main() = runBlocking{
    startProxy("localhost", 5001)
}