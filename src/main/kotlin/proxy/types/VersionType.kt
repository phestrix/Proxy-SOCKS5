package proxy.types

import proxy.exception.SOCKSException

enum class VersionType(
    val code: Byte,
    val replyVersion: Byte,
    val successCode: Byte,
    val unreachableHostCode: Byte,
    val connectionRefusedCode: Byte
) {
    SOCKS5(5, replyVersion = 5, successCode = 0, unreachableHostCode = 4, connectionRefusedCode =  5);

    companion object {
        fun byCode(code: Byte): VersionType =
            VersionType.entries.find { it.code == code } ?: throw SOCKSException("Invalid SOCKS version")
    }
}