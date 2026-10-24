package com.example.xcommandcenter

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.xcommandcenter.reminder.Notifications
import com.example.xcommandcenter.ui.navigation.AppNavHost
import com.example.xcommandcenter.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    /** Set when launched from a reminder notification; the nav host opens that task. */
    private var openTaskId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            AppTheme {
                RequestNotificationPermission()
                AppNavHost(openTaskId = openTaskId, onTaskOpened = { openTaskId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        intent.getLongExtra(EXTRA_OPEN_TASK_ID, 0L).takeIf { it != 0L }?.let { openTaskId = it }
    }

    companion object {
        const val EXTRA_OPEN_TASK_ID = "openTaskId"
    }
}

/** Android 13+ needs runtime permission to post reminders; ask once per launch until decided. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!Notifications.hasPermission(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
