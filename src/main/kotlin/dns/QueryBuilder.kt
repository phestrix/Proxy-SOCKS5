package dns

import java.io.ByteArrayOutputStream

object QueryBuilder {
    fun build(domainName: String): ByteArray{
        val query = ByteArrayOutputStream()
        query.write(0x00)
        query.write(0x00)
        query.write(0x00)
        query.write(0x01)
        query.write(0x00)
        query.write(0x00)
        query.write(0x00)


        domainName.split(".").forEach {
            query.write(it.length)
            query.write(it.toByteArray())
        }
        query.write(0x00)


        query.write(0x00)
        query.write(0x01)


        query.write(0x00)
        query.write(0x01)

        return query.toByteArray()

    }
}