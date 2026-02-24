package org.example.ai.chatbot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class BackendHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal expect suspend fun postChatRequest(url: String, payload: String): BackendHttpResponse

internal expect fun configuredBackendBaseUrl(): String?

class BackendRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackendChatClient {
    suspend fun chat(message: String, userId: String, sessionId: String): String {
        val payload = buildJsonObject {
            put("message", message)
            put("userId", userId)
            put("sessionId", sessionId)
        }.toString()

        val response = try {
            postChatRequest(
                url = "${resolveBackendBaseUrl()}/chat",
                payload = payload,
            )
        } catch (error: Throwable) {
            throw BackendRequestException(
                message = "Network request failed: ${error.message ?: "Unknown error"}",
                cause = error,
            )
        }

        if (response.statusCode !in 200..299) {
            val errorMessage = extractErrorMessage(response.body)
            val normalized = if (errorMessage.isBlank()) "HTTP ${response.statusCode}" else errorMessage
            throw BackendRequestException("Backend error: $normalized")
        }

        val reply = extractReply(response.body)
        if (reply.isBlank()) throw BackendRequestException("Backend returned empty reply")
        return reply
    }
}

private const val FALLBACK_BACKEND_BASE_URL = "https://aichatbot-backend-petuxudhua-uc.a.run.app"

private val jsonParser = Json {
    ignoreUnknownKeys = true
}

private fun resolveBackendBaseUrl(): String {
    val configured = configuredBackendBaseUrl()?.trim().orEmpty()
    val resolved = if (configured.isBlank()) FALLBACK_BACKEND_BASE_URL else configured
    return resolved.removeSuffix("/")
}

private fun extractReply(responseBody: String): String {
    return runCatching {
        jsonParser
            .parseToJsonElement(responseBody)
            .jsonObject["reply"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull().orEmpty()
}

private fun extractErrorMessage(responseBody: String): String {
    return runCatching {
        jsonParser
            .parseToJsonElement(responseBody)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull().orEmpty()
}
