package org.example.ai.chatbot

internal actual suspend fun postChatRequest(url: String, payload: String): BackendHttpResponse {
    throw BackendRequestException(
        "Backend chat is not yet supported on the Wasm target. Use Android, Desktop (JVM), or JS web target."
    )
}

internal actual fun configuredBackendBaseUrl(): String? = null
