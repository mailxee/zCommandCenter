package com.example.xcommandcenter.ui.links

import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The map lays out ideas in the Ideas tab's order, not by title, and follows a reorder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapOrderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c = (context as XCommandCenterApp).container

    private fun idea(id: Long) = ItemRef(ItemType.IDEA, id)

    private fun state(rootArg: String, until: (MapState) -> Boolean): MapState {
        val vm = MapViewModel(SavedStateHandle(mapOf(Routes.ARG_ROOT to rootArg)), c.linkRepository, c.mapPrefsRepository)
        var s: MapState? = null
        val job = CoroutineScope(Dispatchers.Default).launch { vm.state.collect { s = it } }
        repeat(300) {
            if (s?.let(until) != true) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
        job.cancel()
        return s!!
    }

    @Test
    fun branchesFollowTheIdeasTab() {
        val (root, zeta, alpha, mid, top2) = runBlocking {
            c.modeRepository.setMode(AppMode.THINK)
            val root = c.ideaRepository.save(IdeaEntity(title = "Root"))
            val zeta = c.ideaRepository.save(IdeaEntity(title = "Zeta", parentId = root))
            val alpha = c.ideaRepository.save(IdeaEntity(title = "Alpha", parentId = root))
            val mid = c.ideaRepository.save(IdeaEntity(title = "Mid", parentId = root))
            val top2 = c.ideaRepository.save(IdeaEntity(title = "Another top"))
            listOf(root, zeta, alpha, mid, top2)
        }
        val inNetwork = Routes.encodeRef(idea(root))
        var s = state(inNetwork) { it.tree.children[idea(root)]?.size == 3 }
        assertEquals(listOf(zeta, alpha, mid).map(::idea), s.tree.children[idea(root)])
        assertEquals(listOf(root, zeta, alpha, mid).map(::idea), s.nodes.map { it.entry.ref })

        // Everything map: the top-level ideas in their order too (the app's starting ideas come first).
        val ours = setOf(root, top2).map(::idea)
        s = state("") { st -> st.tree.roots.containsAll(ours) }
        assertEquals(listOf(root, top2).map(::idea), s.tree.roots.filter { it in ours })

        // Moved on the Ideas tab: Mid first, then Zeta, then Alpha.
        runBlocking { c.ideaRepository.reorder(listOf(root, mid, zeta, alpha, top2)) }
        val moved = listOf(mid, zeta, alpha).map(::idea)
        s = state(inNetwork) { it.tree.children[idea(root)] == moved }
        assertEquals(moved, s.tree.children[idea(root)])
    }
}
