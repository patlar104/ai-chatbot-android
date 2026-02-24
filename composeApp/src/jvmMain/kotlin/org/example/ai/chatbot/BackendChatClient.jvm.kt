package org.example.ai.chatbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal actual suspend fun postChatRequest(
    url: String,
    payload: String,
    headers: Map<String, String>,
): BackendHttpResponse =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            BackendHttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

internal actual fun configuredBackendBaseUrl(): String? {
    return System.getProperty("backendBaseUrl") ?: System.getenv("BACKEND_BASE_URL")
}

internal actual suspend fun backendAuthHeaders(): Map<String, String> {
    val idToken = System.getProperty("backend.idToken") ?: System.getenv("BACKEND_ID_TOKEN")
    val appCheckToken = System.getProperty("backend.appCheckToken") ?: System.getenv("BACKEND_APP_CHECK_TOKEN")

    if (idToken.isNullOrBlank() && appCheckToken.isNullOrBlank()) {
        return emptyMap()
    }
    if (idToken.isNullOrBlank() || appCheckToken.isNullOrBlank()) {
        throw BackendRequestException(
            "JVM auth is partially configured. Set both backend.idToken/BACKEND_ID_TOKEN and backend.appCheckToken/BACKEND_APP_CHECK_TOKEN."
        )
    }

    return mapOf(
        "Authorization" to "Bearer $idToken",
        "X-Firebase-AppCheck" to appCheckToken,
    )
}
