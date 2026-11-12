package com.nephrocare

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.nephrocare.reminders.Notifications
import com.nephrocare.ui.NephroRoot

class MainActivity : AppCompatActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = NephroApp.container(this)
        // Keep health data out of screenshots and the recent-apps preview when the app is locked by a PIN.
        if (container.settings.settings.value.pinHash != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(Notifications.EXTRA_ROUTE)
        setContent {
            NephroRoot(
                container = container,
                pendingRoute = pendingRoute.value,
                onRouteHandled = { pendingRoute.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
}
