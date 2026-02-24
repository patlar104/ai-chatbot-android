package org.example.ai.chatbot

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

private val json = Json { ignoreUnknownKeys = true }
private const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

data class ChatRequest(
    val message: String,
    val userId: String? = null,
    val sessionId: String? = null,
)

fun Application.module(
    chatService: ChatService = GenkitFlowChatService.fromEnvironment(),
    requestSecurityVerifier: RequestSecurityVerifier = FirebaseRequestSecurityVerifier.fromEnvironment(environment.log),
) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(APP_CHECK_HEADER)
        allowNonSimpleContentTypes = true

        val allowedOrigins = (System.getenv("CHAT_CORS_ALLOWED_ORIGINS") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (allowedOrigins.isEmpty()) {
            allowHost("localhost:8080", schemes = listOf("http"))
            allowHost("localhost:3000", schemes = listOf("http"))
            allowHost("127.0.0.1:8080", schemes = listOf("http"))
            allowHost("127.0.0.1:3000", schemes = listOf("http"))
        } else {
            allowedOrigins.forEach { origin ->
                val uri = runCatching { URI(origin) }.getOrNull()
                val host = uri?.host
                val scheme = uri?.scheme
                if (!host.isNullOrBlank() && !scheme.isNullOrBlank()) {
                    val hostWithPort = if (uri.port != -1) "$host:${uri.port}" else host
                    allowHost(hostWithPort, schemes = listOf(scheme))
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
        get("/health") {
            respondJson(
                call = call,
                status = HttpStatusCode.OK,
                payload = buildJsonObject {
                    put("status", "ok")
                    put("service", "server")
                }
            )
        }
        post("/chat") {
            val verifiedContext = try {
                requestSecurityVerifier.verify(
                    authorizationHeader = call.request.header(HttpHeaders.Authorization),
                    appCheckHeader = call.request.header(APP_CHECK_HEADER),
                )
            } catch (exception: UnauthorizedRequestSecurityException) {
                respondError(
                    call = call,
                    status = HttpStatusCode.Unauthorized,
                    code = exception.errorCode,
                    message = exception.message ?: "Unauthorized",
                )
                return@post
            } catch (exception: ForbiddenRequestSecurityException) {
                respondError(
                    call = call,
                    status = HttpStatusCode.Forbidden,
                    code = exception.errorCode,
                    message = exception.message ?: "Forbidden",
                )
                return@post
            }

            val request = try {
                parseChatRequest(call.receiveText())
            } catch (exception: IllegalArgumentException) {
                respondError(call, HttpStatusCode.BadRequest, "bad_request", exception.message ?: "Invalid request")
                return@post
            }

            val result = try {
                chatService.generateReply(
                    message = request.message,
                    userId = verifiedContext?.uid ?: request.userId,
                    sessionId = request.sessionId,
                )
            } catch (exception: MissingApiKeyException) {
                respondError(call, HttpStatusCode.ServiceUnavailable, "missing_api_key", exception.message ?: "Missing API key")
                return@post
            } catch (exception: UpstreamAiException) {
                respondError(call, HttpStatusCode.BadGateway, "upstream_failure", exception.message ?: "Upstream AI failure")
                return@post
            }

            respondJson(
                call = call,
                status = HttpStatusCode.OK,
                payload = buildJsonObject {
                    put("reply", result.reply)
                    put("model", result.model)
                    request.sessionId?.let { put("sessionId", it) }
                    (verifiedContext?.uid ?: request.userId)?.let { put("userId", it) }
                }
            )
        }
    }
}

private fun parseChatRequest(rawBody: String): ChatRequest {
    val rootObject = try {
        json.parseToJsonElement(rawBody).jsonObject
    } catch (_: Exception) {
        throw IllegalArgumentException("Body must be valid JSON")
    }

    val message = rootObject["message"]?.jsonPrimitive?.contentOrNull?.trim()
    if (message.isNullOrBlank()) {
        throw IllegalArgumentException("Field 'message' is required")
    }
    if (message.length > 4000) {
        throw IllegalArgumentException("Field 'message' must be <= 4000 characters")
    }

    return ChatRequest(
        message = message,
        userId = rootObject["userId"]?.jsonPrimitive?.contentOrNull,
        sessionId = rootObject["sessionId"]?.jsonPrimitive?.contentOrNull,
    )
}

private suspend fun respondJson(
    call: ApplicationCall,
    status: HttpStatusCode,
    payload: kotlinx.serialization.json.JsonObject,
) {
    call.respondText(
        text = payload.toString(),
        contentType = ContentType.Application.Json,
        status = status,
    )
}

private suspend fun respondError(
    call: ApplicationCall,
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respondJson(
        call = call,
        status = status,
        payload = buildJsonObject {
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }
    )
}
