package org.example.ai.chatbot

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed.")
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_refreshed", true)
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_prefix", token.take(8))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "FCM message received: id=${message.messageId.orEmpty()}")
        FirebaseCrashlytics.getInstance().setCustomKey(
            "last_fcm_message_id",
            message.messageId ?: "missing"
        )

        val analytics = FirebaseAnalytics.getInstance(applicationContext)
        val bundle = Bundle().apply {
            putString("message_id", message.messageId ?: "missing")
            putString("from", message.from ?: "unknown")
        }
        analytics.logEvent("fcm_message_received", bundle)
    }

    private companion object {
        private const val TAG = "AppFCMService"
    }
}
