package org.example.ai.chatbot

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit

internal actual suspend fun postChatRequest(url: String, payload: String): BackendHttpResponse {
    val response = window.fetch(
        input = url,
        init = RequestInit(
            method = "POST",
            body = payload,
            headers = js("({ 'Content-Type': 'application/json; charset=utf-8' })"),
        ),
    ).await()

    val responseBody = response.text().await()
    return BackendHttpResponse(
        statusCode = response.status.toInt(),
        body = responseBody,
    )
}

internal actual fun configuredBackendBaseUrl(): String? = null
