package org.example.ai.chatbot

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    private var hasRequestedPostNotificationsInThisActivity = false

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i("MainActivity", "POST_NOTIFICATIONS granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        FirebaseRuntimeSetup.initialize(applicationContext)
        FirebaseRuntimeSetup.runConnectivityCheck { status ->
            Log.i("FirebaseSetup", status)
        }
        hasRequestedPostNotificationsInThisActivity =
            savedInstanceState?.getBoolean(KEY_NOTIFICATIONS_REQUESTED_IN_ACTIVITY) ?: false

        setContent {
            AndroidChatApp()
        }
    }

    override fun onStart() {
        super.onStart()
        requestPostNotificationsPermissionIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            KEY_NOTIFICATIONS_REQUESTED_IN_ACTIVITY,
            hasRequestedPostNotificationsInThisActivity
        )
        super.onSaveInstanceState(outState)
    }

    private fun requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasRequestedPostNotificationsInThisActivity) return

        val permissionState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) return

        hasRequestedPostNotificationsInThisActivity = true
        postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        private const val KEY_NOTIFICATIONS_REQUESTED_IN_ACTIVITY =
            "key_notifications_requested_in_activity"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AndroidChatApp()
}
