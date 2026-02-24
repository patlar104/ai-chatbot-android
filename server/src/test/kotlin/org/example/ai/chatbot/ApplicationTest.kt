package org.example.ai.chatbot

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRoot() = testApplication {
        application {
            module(requestSecurityVerifier = AllowAllRequestSecurityVerifier)
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }

    @Test
    fun testHealth() = testApplication {
        application {
            module(
                chatService = FakeChatService(),
                requestSecurityVerifier = AllowAllRequestSecurityVerifier,
            )
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
        assertEquals("server", body["service"]?.jsonPrimitive?.content)
    }

    @Test
    fun testChatBadRequestWhenMessageMissing() = testApplication {
        application {
            module(
                chatService = FakeChatService(),
                requestSecurityVerifier = AllowAllRequestSecurityVerifier,
            )
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"u1"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Field 'message' is required"))
    }

    @Test
    fun testChatReturnsReplyFromService() = testApplication {
        application {
            module(
                chatService = FakeChatService(reply = "Server reply", model = "fake-model"),
                requestSecurityVerifier = AllowAllRequestSecurityVerifier,
            )
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello","userId":"u1","sessionId":"s1"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Server reply", body["reply"]?.jsonPrimitive?.content)
        assertEquals("fake-model", body["model"]?.jsonPrimitive?.content)
        assertEquals("u1", body["userId"]?.jsonPrimitive?.content)
        assertEquals("s1", body["sessionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun testChatMissingApiKeyMapsTo503() = testApplication {
        application {
            module(
                chatService = object : ChatService {
                    override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
                        throw MissingApiKeyException("Missing GEMINI_API_KEY on server")
                    }
                },
                requestSecurityVerifier = AllowAllRequestSecurityVerifier,
            )
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello"}""")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("missing_api_key"))
    }

    @Test
    fun testChatRejectsMissingAuthHeadersWhenVerifierRequiresSecurity() = testApplication {
        application {
            module(
                chatService = FakeChatService(),
                requestSecurityVerifier = RequiredHeadersVerifier(),
            )
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("missing_credentials"))
    }

    @Test
    fun testChatAcceptsValidHeadersAndOverridesUserId() = testApplication {
        application {
            module(
                chatService = FakeChatService(reply = "Secure reply", model = "secure-model"),
                requestSecurityVerifier = RequiredHeadersVerifier(),
            )
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer id-token-123")
            header("X-Firebase-AppCheck", "app-check-456")
            setBody("""{"message":"Hello","userId":"spoofed-user","sessionId":"s1"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Secure reply", body["reply"]?.jsonPrimitive?.content)
        assertEquals("secure-model", body["model"]?.jsonPrimitive?.content)
        assertEquals("verified-user", body["userId"]?.jsonPrimitive?.content)
    }

    private class FakeChatService(
        private val reply: String = "Hello from fake AI",
        private val model: String = "fake-gemini",
    ) : ChatService {
        override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
            return ChatResult(reply = reply, model = model)
        }
    }

    private class RequiredHeadersVerifier : RequestSecurityVerifier {
        override fun verify(authorizationHeader: String?, appCheckHeader: String?): VerifiedRequestContext {
            val bearerToken = authorizationHeader
                ?.takeIf { it.startsWith("Bearer ") }
                ?.removePrefix("Bearer ")
                ?.trim()
            if (bearerToken.isNullOrBlank() || appCheckHeader.isNullOrBlank()) {
                throw UnauthorizedRequestSecurityException(
                    errorCode = "missing_credentials",
                    message = "Missing credentials",
                )
            }
            if (bearerToken != "id-token-123" || appCheckHeader != "app-check-456") {
                throw ForbiddenRequestSecurityException(
                    errorCode = "invalid_credentials",
                    message = "Invalid credentials",
                )
            }
            return VerifiedRequestContext(uid = "verified-user")
        }
    }
}
