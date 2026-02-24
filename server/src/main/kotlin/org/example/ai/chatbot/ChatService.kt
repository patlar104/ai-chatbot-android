package org.example.ai.chatbot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ChatResult(
    val reply: String,
    val model: String,
)

interface ChatService {
    fun generateReply(
        message: String,
        userId: String? = null,
        sessionId: String? = null,
    ): ChatResult
}

class MissingApiKeyException(message: String) : RuntimeException(message)

class UpstreamAiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class GenkitFlowChatService(
    private val baseUrlProvider: () -> String?,
    private val timeout: Duration = Duration.ofSeconds(30),
) : ChatService {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
        val baseUrl = baseUrlProvider()?.trim().orEmpty().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw UpstreamAiException("Missing GENKIT_BASE_URL on server")
        }

        val requestBody = buildJsonObject {
            put("message", message)
            userId?.let { put("userId", it) }
            sessionId?.let { put("sessionId", it) }
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: IOException) {
            throw UpstreamAiException("Could not reach Genkit service", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UpstreamAiException("Genkit service request interrupted", exception)
        }

        if (response.statusCode() !in 200..299) {
            throw UpstreamAiException(
                "Genkit service returned HTTP ${response.statusCode()}: ${response.body().take(500)}"
            )
        }

        val root = runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
            ?: throw UpstreamAiException("Genkit service returned invalid JSON")

        val reply = root["reply"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (reply.isBlank()) {
            throw UpstreamAiException("Genkit service returned empty reply")
        }

        val model = root["model"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { "genkit" }

        return ChatResult(
            reply = reply,
            model = model,
        )
    }

    companion object {
        fun fromEnvironment(): GenkitFlowChatService = GenkitFlowChatService(
            baseUrlProvider = {
                System.getenv("GENKIT_BASE_URL")
                    ?: System.getProperty("GENKIT_BASE_URL")
                    ?: "http://127.0.0.1:3400"
            }
        )
    }
}

class GeminiChatService(
    private val apiKeyProvider: () -> String?,
    private val model: String = DEFAULT_MODEL,
    private val timeout: Duration = Duration.ofSeconds(30),
) : ChatService {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            throw MissingApiKeyException("Missing GEMINI_API_KEY on server")
        }

        val prompt = buildPrompt(message, userId, sessionId)
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(
                    buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", prompt) })
                        })
                    }
                )
            })
        }.toString()

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: IOException) {
            throw UpstreamAiException("Could not reach Gemini API", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UpstreamAiException("Gemini API request interrupted", exception)
        }

        if (response.statusCode() !in 200..299) {
            throw UpstreamAiException(
                "Gemini API returned HTTP ${response.statusCode()}: ${response.body().take(500)}"
            )
        }

        val reply = extractReply(response.body())
            ?: throw UpstreamAiException("Gemini API returned no text reply")

        return ChatResult(
            reply = reply,
            model = model,
        )
    }

    private fun buildPrompt(message: String, userId: String?, sessionId: String?): String {
        val cleanedMessage = message.trim()
        return buildString {
            if (!userId.isNullOrBlank()) appendLine("UserId: $userId")
            if (!sessionId.isNullOrBlank()) appendLine("SessionId: $sessionId")
            appendLine("You are a concise and helpful AI assistant.")
            append("User message: $cleanedMessage")
        }
    }

    private fun extractReply(rawResponse: String): String? {
        val root = runCatching { json.parseToJsonElement(rawResponse).jsonObject }.getOrNull() ?: return null
        val candidates = root["candidates"]?.asJsonArrayOrNull() ?: return null

        for (candidate in candidates) {
            val candidateObject = candidate.asJsonObjectOrNull() ?: continue
            val content = candidateObject["content"]?.asJsonObjectOrNull() ?: continue
            val parts = content["parts"]?.asJsonArrayOrNull() ?: continue
            val text = parts
                .mapNotNull { part -> part.asJsonObjectOrNull()?.get("text")?.jsonPrimitive?.contentOrNull }
                .joinToString("\n")
                .trim()

            if (text.isNotEmpty()) return text
        }

        return null
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.asJsonArrayOrNull(): JsonArray? = this as? JsonArray

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"

        fun fromEnvironment(): GeminiChatService = GeminiChatService(
            apiKeyProvider = {
                System.getenv("GEMINI_API_KEY")
                    ?: System.getProperty("GEMINI_API_KEY")
            }
        )
    }
}
