package server.socks

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.lang.Byte.toUnsignedInt
import server.socks.exception.SOCKSException


interface SOCKSAuthenticationMethod{
    val code: Int
    suspend fun negotiate(reader: ByteReadChannel, writer: ByteWriteChannel)
}

object NoAuthenticationRequired: SOCKSAuthenticationMethod {
    override val code: Int = 0

    override suspend fun negotiate(reader: ByteReadChannel, writer: ByteWriteChannel) {}
}


//unused
abstract class UserPasswordAuthentication(val username: String, val password: String) : SOCKSAuthenticationMethod {
    override val code: Int = 2

    abstract fun verify(username: String, password: String): Boolean

    override suspend fun negotiate(reader: ByteReadChannel, writer: ByteWriteChannel) {
        val version = reader.readByte()
        if (version != VERSION) {
            throw SOCKSException("Invalid Username/Password authentication version: $version")
        }

        val usernameSize = toUnsignedInt(reader.readByte())
        val username = reader.readPacket(usernameSize).readBytes().decodeToString()
        val passwordSize = toUnsignedInt(reader.readByte())
        val password = reader.readPacket(passwordSize).readBytes().decodeToString()

        if (verify(username, password)) {
            writer.writeResponse(SUCCESS)
        } else {
            writer.writeResponse(FAILURE)
            throw SOCKSException("Username/Password authentication failed")
        }
    }

    private suspend fun ByteWriteChannel.writeResponse(status: Byte) {
        writePacket {
            writeByte(VERSION)
            writeByte(status)
        }
        flush()
    }

    private companion object {
        private const val VERSION = 1.toByte()
        private const val SUCCESS = 0.toByte()
        private const val FAILURE = 1.toByte()
    }

}