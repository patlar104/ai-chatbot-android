package org.example.ai.chatbot

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit

internal actual suspend fun postChatRequest(
    url: String,
    payload: String,
    headers: Map<String, String>,
): BackendHttpResponse {
    val headerObject = js("{}")
    headerObject["Content-Type"] = "application/json; charset=utf-8"
    headers.forEach { (name, value) ->
        headerObject[name] = value
    }

    val response = window.fetch(
        input = url,
        init = RequestInit(
            method = "POST",
            body = payload,
            headers = headerObject,
        ),
    ).await()

    val responseBody = response.text().await()
    return BackendHttpResponse(
        statusCode = response.status.toInt(),
        body = responseBody,
    )
}

internal actual fun configuredBackendBaseUrl(): String? = null

internal actual suspend fun backendAuthHeaders(): Map<String, String> {
    val dynamicWindow = window.asDynamic()
    val idToken = dynamicWindow.__FIREBASE_ID_TOKEN__ as? String
        ?: runCatching { window.localStorage.getItem("FIREBASE_ID_TOKEN") }.getOrNull()
    val appCheckToken = dynamicWindow.__FIREBASE_APP_CHECK_TOKEN__ as? String
        ?: runCatching { window.localStorage.getItem("FIREBASE_APP_CHECK_TOKEN") }.getOrNull()

    if (idToken.isNullOrBlank() && appCheckToken.isNullOrBlank()) {
        return emptyMap()
    }
    if (idToken.isNullOrBlank() || appCheckToken.isNullOrBlank()) {
        throw BackendRequestException(
            "JS auth is partially configured. Set both __FIREBASE_ID_TOKEN__ and __FIREBASE_APP_CHECK_TOKEN__ (or matching localStorage keys)."
        )
    }

    return mapOf(
        "Authorization" to "Bearer $idToken",
        "X-Firebase-AppCheck" to appCheckToken,
    )
}
