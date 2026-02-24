package org.example.ai.chatbot

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform