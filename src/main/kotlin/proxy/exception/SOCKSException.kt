package proxy.exception

import io.ktor.utils.io.errors.IOException

class SOCKSException(message: String, cause: Throwable? = null): IOException(message, cause)