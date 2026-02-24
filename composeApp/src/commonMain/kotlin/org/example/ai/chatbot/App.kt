package org.example.ai.chatbot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    ChatAppScreen(sendMessage = BackendChatClient::chat)
}
