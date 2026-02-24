package org.example.ai.chatbot

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        FirebaseRuntimeSetup.initialize(applicationContext)
        FirebaseRuntimeSetup.runConnectivityCheck { status ->
            Log.i("FirebaseSetup", status)
        }
        requestPostNotificationsPermissionIfNeeded()

        setContent {
            AndroidChatApp()
        }
    }

    private fun requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permissionState = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) return

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            POST_NOTIFICATIONS_REQUEST_CODE
        )
    }

    companion object {
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 1001
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AndroidChatApp()
}
