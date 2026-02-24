package org.example.ai.chatbot

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "BackendChatClient"

internal actual suspend fun postChatRequest(
    url: String,
    payload: String,
    headers: Map<String, String>,
): BackendHttpResponse =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            BackendHttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

internal actual fun configuredBackendBaseUrl(): String? = BuildConfig.BACKEND_BASE_URL

internal actual suspend fun backendAuthHeaders(): Map<String, String> {
    val idToken = runCatching { fetchIdToken() }
        .onFailure { Log.w(TAG, "Failed to fetch Firebase ID token", it) }
        .getOrNull()
    val appCheckToken = runCatching { fetchAppCheckToken() }
        .onFailure { Log.w(TAG, "Failed to fetch App Check token", it) }
        .getOrNull()

    if (idToken.isNullOrBlank() && appCheckToken.isNullOrBlank()) {
        return emptyMap()
    }
    if (idToken.isNullOrBlank() || appCheckToken.isNullOrBlank()) {
        throw BackendRequestException(
            "Android auth is partially available. Both Firebase ID token and App Check token are required."
        )
    }

    return mapOf(
        "Authorization" to "Bearer $idToken",
        "X-Firebase-AppCheck" to appCheckToken,
    )
}

private suspend fun fetchIdToken(): String {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser ?: auth.signInAnonymously().await().user
    val resolvedUser = user ?: throw BackendRequestException("Firebase auth user is unavailable")
    return resolvedUser.getIdToken(false).await().token.orEmpty()
        .ifBlank { throw BackendRequestException("Firebase ID token is empty") }
}

private suspend fun fetchAppCheckToken(): String {
    val token = FirebaseAppCheck.getInstance()
        .getAppCheckToken(false)
        .await()
        .token
        .orEmpty()
    if (token.isBlank()) throw BackendRequestException("Firebase App Check token is empty")
    return token
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { error ->
        continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
