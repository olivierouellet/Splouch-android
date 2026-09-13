package app.splouch.core.session

/** The one HTTP verb the contract needs, behind an interface for OkHttp and for tests. */
interface HttpClient {
    /** Resolves for any HTTP status; throws only when no response arrived at all. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

data class HttpResponse(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) {
    val isSuccess: Boolean get() = status in 200..299
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

class HttpFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
