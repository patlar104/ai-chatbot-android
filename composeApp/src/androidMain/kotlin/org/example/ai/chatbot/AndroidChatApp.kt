package org.example.ai.chatbot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private data class ChatMessage(
    val role: String,
    val text: String,
)

private object BackendChatClient {
    private const val BASE_URL = BuildConfig.BACKEND_BASE_URL

    suspend fun chat(message: String, userId: String, sessionId: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("message", message)
            .put("userId", userId)
            .put("sessionId", sessionId)
            .toString()

        val connection = (URL("$BASE_URL/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return@withContext try {
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (statusCode !in 200..299) {
                val errorMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                val normalized = if (errorMessage.isBlank()) "HTTP $statusCode" else errorMessage
                throw IOException("Backend error: $normalized")
            }

            val reply = runCatching {
                JSONObject(body).optString("reply")
            }.getOrNull().orEmpty()

            if (reply.isBlank()) throw IOException("Backend returned empty reply")
            reply
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun AndroidChatApp() {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val sessionId = remember { "android-session-1" }
    val userId = remember { "android-user-1" }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI Chat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.role,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                enabled = !loading,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val message = input.trim()
                        if (message.isBlank() || loading) return@Button

                        errorText = null
                        messages += ChatMessage(role = "You", text = message)
                        input = ""
                        loading = true

                        scope.launch {
                            runCatching {
                                BackendChatClient.chat(
                                    message = message,
                                    userId = userId,
                                    sessionId = sessionId,
                                )
                            }.onSuccess { reply ->
                                messages += ChatMessage(role = "Assistant", text = reply)
                            }.onFailure { error ->
                                errorText = error.message ?: "Request failed"
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                ) {
                    Text("Send")
                }

                if (loading) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
