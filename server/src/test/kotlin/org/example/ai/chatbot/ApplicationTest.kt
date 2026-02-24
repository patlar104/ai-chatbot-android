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
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }

    @Test
    fun testHealth() = testApplication {
        application {
            module(chatService = FakeChatService())
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
            module(chatService = FakeChatService())
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
            module(chatService = FakeChatService(reply = "Server reply", model = "fake-model"))
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
            module(chatService = object : ChatService {
                override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
                    throw MissingApiKeyException("Missing GEMINI_API_KEY on server")
                }
            })
        }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello"}""")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("missing_api_key"))
    }

    private class FakeChatService(
        private val reply: String = "Hello from fake AI",
        private val model: String = "fake-gemini",
    ) : ChatService {
        override fun generateReply(message: String, userId: String?, sessionId: String?): ChatResult {
            return ChatResult(reply = reply, model = model)
        }
    }
}
