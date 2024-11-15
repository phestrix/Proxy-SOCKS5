package proxy.types

import proxy.exception.SOCKSException

enum class CommandType(val code: Byte) {
    CONNECT(1),
    BIND(2),
    UDP_ASSOCIATE(3);

    companion object {
        fun byCode(code: Byte): CommandType =
            CommandType.entries.find { it.code == code } ?: throw SOCKSException("Invalid command")
    }
}