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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemStyles
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.reminder.Notifications
import com.example.xcommandcenter.ui.common.LocalItemStyles
import com.example.xcommandcenter.ui.common.LocalTabOrder
import com.example.xcommandcenter.ui.navigation.AppNavHost
import com.example.xcommandcenter.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    /** Set when launched from a reminder notification; the nav host opens that task or habit. */
    private var openItem by mutableStateOf<ItemRef?>(null)

    /**
     * Token of the last open-task intent acted on. A notification tap can recreate the activity
     * from saved state (after process death), so "first launch" is not enough to tell a new
     * request from a rotation re-delivering an old one.
     */
    private var handledToken = NO_TOKEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handledToken = savedInstanceState?.getLong(KEY_HANDLED_TOKEN, NO_TOKEN) ?: NO_TOKEN
        readIntent(intent)
        setContent {
            val styles by (application as XCommandCenterApp).container.itemStyleRepository.styles
                .collectAsStateWithLifecycle(initialValue = ItemStyles())
            val tabOrder by (application as XCommandCenterApp).container.tabOrderRepository.order
                .collectAsStateWithLifecycle(initialValue = TabOrder.default)
            AppTheme {
                CompositionLocalProvider(LocalItemStyles provides styles, LocalTabOrder provides tabOrder) {
                    RequestNotificationPermission()
                    AppNavHost(openItem = openItem, onItemOpened = { openItem = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_HANDLED_TOKEN, handledToken)
    }

    private fun readIntent(intent: Intent) {
        val type = ItemType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_OPEN_TYPE) }
        val id = intent.getLongExtra(EXTRA_OPEN_ID, 0L)
        val token = intent.getLongExtra(EXTRA_TOKEN, 0L)
        if (type == null || id == 0L || token == handledToken) return
        handledToken = token
        openItem = ItemRef(type, id)
    }

    companion object {
        /** The [ItemType] name and id of the item a notification opens. */
        const val EXTRA_OPEN_TYPE = "openType"
        const val EXTRA_OPEN_ID = "openId"
        /** Unique per notification, so each tap is handled exactly once. */
        const val EXTRA_TOKEN = "openTaskToken"
        private const val KEY_HANDLED_TOKEN = "handledOpenTaskToken"
        private const val NO_TOKEN = -1L
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
