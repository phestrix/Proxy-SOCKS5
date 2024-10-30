package server.socks.exception

import io.ktor.utils.io.errors.IOException

class SOCKSException: IOException {
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}