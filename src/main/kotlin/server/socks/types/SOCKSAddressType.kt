package server.socks.types

import server.socks.exception.SOCKSException

internal enum class SOCKSAddressType(val code: Byte) {
    IPv4(1),
    Domain(3),
    IPv6(4);

    companion object {
        fun byCode(code: Byte): SOCKSAddressType = SOCKSAddressType.entries.find { it.code == code }
            ?: throw SOCKSException("Invalid SOCKS address type: $code")
    }
}