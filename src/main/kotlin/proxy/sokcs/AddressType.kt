package proxy.sokcs

enum class AddressType(val code: Byte) {
    IPV4(1),
    IPV6(4),
    DOMAIN(3);

    companion object {
        fun byCode(code: Byte): AddressType =
            AddressType.entries.find { it.code == code } ?: throw SOCKSException("Invalid address")
    }
}