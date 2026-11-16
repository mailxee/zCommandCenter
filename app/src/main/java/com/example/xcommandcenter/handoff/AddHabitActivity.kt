package com.example.xcommandcenter.handoff

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.MainActivity
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.domain.HabitHandoff
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemStyles
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.handoff.Handoff.longExtra
import com.example.xcommandcenter.share.CallerVerdict
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.LocalItemStyles
import com.example.xcommandcenter.ui.common.LocalTabOrder
import com.example.xcommandcenter.ui.habit.HabitDetailScreen
import com.example.xcommandcenter.ui.habit.HabitDetailViewModel
import com.example.xcommandcenter.ui.navigation.Routes
import com.example.xcommandcenter.ui.theme.AppTheme
import java.time.LocalDate

/**
 * ADD_HABIT: the habit editor as a new, unsaved habit filled in from another app's request. The
 * user can change anything; nothing is saved until they tap Save. RESULT_OK with habit_id once
 * saved (still so if they then go back), RESULT_CANCELED otherwise.
 */
class AddHabitActivity : ComponentActivity() {
    private val access: AddHabitHandoffViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The editor's view model reads the habit to open from the extras ("id"); a request only ever adds.
        intent.removeExtra(Routes.ARG_ID)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)
        val draft = HabitHandoff.newHabit(
            name = intent.getStringExtra(Handoff.EXTRA_NAME),
            notes = intent.getStringExtra(Handoff.EXTRA_NOTES),
            startEpochDay = intent.longExtra(Handoff.EXTRA_START_DATE),
            today = LocalDate.now(),
        )
        access.start(callingPackage)
        val container = (application as XCommandCenterApp).container
        setContent {
            val styles by container.itemStyleRepository.styles.collectAsStateWithLifecycle(initialValue = ItemStyles())
            val tabOrder by container.tabOrderRepository.order.collectAsStateWithLifecycle(initialValue = TabOrder.default)
            AppTheme {
                CompositionLocalProvider(LocalItemStyles provides styles, LocalTabOrder provides tabOrder) {
                    val verdict by access.verdict.collectAsStateWithLifecycle()
                    when (val v = verdict) {
                        null -> Unit
                        is CallerVerdict.NotAllowed -> HandoffMessage(v.message, ::finish)
                        CallerVerdict.Unknown -> HandoffMessage(CallerVerdict.Unknown.MESSAGE, ::finish)
                        CallerVerdict.Allowed ->
                            if (draft == null) {
                                HandoffMessage("The request didn't give the habit a name, so nothing was added.", ::finish)
                            } else {
                                Editor(draft)
                            }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Editor(draft: HabitHandoff.NewHabit) {
        val editor: HabitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
        remember(editor) { editor.prefill(draft) }
        val id by editor.itemId.collectAsStateWithLifecycle()
        val deleted = editor.deleted
        LaunchedEffect(id, deleted) {
            if (id != 0L && !deleted) setResult(Activity.RESULT_OK, Intent().putExtra(Handoff.EXTRA_HABIT_ID, id))
            else setResult(Activity.RESULT_CANCELED)
        }
        HabitDetailScreen(onBack = ::finish, onOpen = ::openInApp, viewModel = editor)
    }

    /** A linked item opens in the app itself, in its own task, leaving this screen to return to. */
    private fun openInApp(ref: ItemRef) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TYPE, ref.type.name)
                .putExtra(MainActivity.EXTRA_OPEN_ID, ref.id)
                .putExtra(MainActivity.EXTRA_TOKEN, System.nanoTime())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
