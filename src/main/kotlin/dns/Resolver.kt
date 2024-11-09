package dns

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import java.net.InetAddress

class Resolver(private val dnsServer: String, private val selectorManager: SelectorManager) {
    suspend fun resolve(domain: String): String? {
        val dnsSocket = aSocket(selectorManager).udp().connect(InetSocketAddress(dnsServer, 53))
        val dnsQuery = QueryBuilder.build(domain)
        val dnsOutput = dnsSocket.openWriteChannel(autoFlush = true)
        dnsOutput.writeFully(dnsQuery, 0, dnsQuery.size)

        val dnsInput = dnsSocket.openReadChannel()
        val response = ByteArray(512)
        dnsInput.readFully(response, 0, response.size)

        return parseDnsResponse(response)
    }

    fun parseDnsResponse(response: ByteArray): String? {
        val ipStart = response.indexOf(0xC0.toByte()) + 2
        if (ipStart == 1) return null
        val ipAddress = response.slice(ipStart until ipStart + 4).toByteArray()
        return InetAddress.getByAddress(ipAddress).hostAddress
    }
}