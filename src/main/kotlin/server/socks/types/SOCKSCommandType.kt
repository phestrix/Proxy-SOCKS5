package server.socks.types

import server.socks.exception.SOCKSException

enum class SOCKSCommandType(val code: Byte) {
    CONNECT(1),
    BIND(2),
    UDP_ASSOCIATE(3);
    companion object {
        fun byCode(byte: Byte): SOCKSCommandType {
            return SOCKSCommandType.entries.find { it.code == byte }
                ?: throw SOCKSException("Invalid SOCKS command type: $byte")
        }
    }
}