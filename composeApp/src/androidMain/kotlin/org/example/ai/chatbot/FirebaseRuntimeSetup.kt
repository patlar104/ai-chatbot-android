package org.example.ai.chatbot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

object FirebaseRuntimeSetup {
    private const val TAG = "FirebaseSetup"
    private var buildTypeLabel: String = "unknown"

    fun initialize(appContext: Context) {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val isDebugBuild = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        buildTypeLabel = if (isDebugBuild) "debug" else "release"

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        crashlytics.setCustomKey("build_type", buildTypeLabel)

        FirebaseAnalytics.getInstance(appContext).logEvent("firebase_bootstrap_complete", Bundle())

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.i(TAG, "FCM token fetched.")
                crashlytics.setCustomKey("fcm_token_ready", true)
                crashlytics.setCustomKey("fcm_token_prefix", token.take(8))
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "FCM token fetch failed.", error)
                crashlytics.recordException(error)
            }
    }

    fun runConnectivityCheck(onStatus: (String) -> Unit = {}) {
        ensureSignedIn { uid, error ->
            if (error != null) {
                val message = "Firebase Auth failed: ${error.message.orEmpty()}"
                Log.e(TAG, message, error)
                FirebaseCrashlytics.getInstance().recordException(error)
                onStatus(message)
                return@ensureSignedIn
            }

            val safeUid = uid ?: "unknown"
            writeFirestoreCheck(safeUid, onStatus)
            uploadStorageCheck(safeUid, onStatus)
        }
    }

    private fun ensureSignedIn(onDone: (String?, Throwable?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val existingUser = auth.currentUser
        if (existingUser != null) {
            onDone(existingUser.uid, null)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                onDone(result.user?.uid, null)
            }
            .addOnFailureListener { error ->
                onDone(null, error)
            }
    }

    private fun writeFirestoreCheck(uid: String, onStatus: (String) -> Unit) {
        val payload = hashMapOf(
            "uid" to uid,
            "platform" to "android",
            "buildType" to buildTypeLabel,
            "checkedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("healthchecks")
            .document(uid)
            .set(payload)
            .addOnSuccessListener {
                val message = "Firestore write OK for uid=$uid"
                Log.i(TAG, message)
                onStatus(message)
            }
            .addOnFailureListener { error ->
                val message = "Firestore write failed: ${error.message.orEmpty()}"
                Log.e(TAG, message, error)
                FirebaseCrashlytics.getInstance().recordException(error)
                onStatus(message)
            }
    }

    private fun uploadStorageCheck(uid: String, onStatus: (String) -> Unit) {
        val content = "storage healthcheck ${System.currentTimeMillis()}\n".toByteArray()

        FirebaseStorage.getInstance()
            .reference
            .child("healthchecks/$uid/ping.txt")
            .putBytes(content)
            .addOnSuccessListener {
                val message = "Storage upload OK for uid=$uid"
                Log.i(TAG, message)
                onStatus(message)
            }
            .addOnFailureListener { error ->
                val message = "Storage upload failed: ${error.message.orEmpty()}"
                Log.e(TAG, message, error)
                FirebaseCrashlytics.getInstance().recordException(error)
                onStatus(message)
            }
    }
}
