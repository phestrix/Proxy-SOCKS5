package server.socks

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*


class SOCKSConfig(
    val allowSOCKS4: Boolean,
    val authMethods: List<SOCKSAuthenticationMethod>,
    val networkAddress: ServerSocket
)

class SOCKSConfigBuilder {
    val authenticationMethods: MutableList<SOCKSAuthenticationMethod> = mutableListOf()
    var allowSOCKS4: Boolean = true
    var networkAddress: ServerSocket? = null
    var hostname: String = "0.0.0.0"
    var port: Int = 5001

    fun build(selector: SelectorManager): SOCKSConfig = SOCKSConfig(
        allowSOCKS4 = false,
        authenticationMethods.ifEmpty { mutableListOf(NoAuthenticationRequired) },
        networkAddress = aSocket(selector).tcp().bind(hostname, port)
    )
}