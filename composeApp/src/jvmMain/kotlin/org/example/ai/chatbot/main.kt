package org.example.ai.chatbot

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ai chatbot android",
    ) {
        App()
    }
}