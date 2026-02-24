plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "org.example.ai.chatbot"
version = "1.0.0"
application {
    mainClass.set("org.example.ai.chatbot.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation("io.ktor:ktor-server-cors-jvm:3.3.3")
    implementation("com.google.firebase:firebase-admin:9.4.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
