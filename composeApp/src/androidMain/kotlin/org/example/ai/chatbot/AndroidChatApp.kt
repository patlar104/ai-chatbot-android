package org.example.ai.chatbot

import androidx.compose.runtime.Composable

@Composable
fun AndroidChatApp() {
    ChatAppScreen(sendMessage = BackendChatClient::chat)
}
