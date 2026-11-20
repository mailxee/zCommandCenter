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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A labelled link between ideas in different branches (Folly, under Vice, opposes Wisdom, two levels
 * under Virtue) reaches the map, in a network and on the Everything map, read from its subject.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapLinksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c = (context as XCommandCenterApp).container

    @Test
    fun labelledLinkBetweenTwoBranches() {
        val ids = runBlocking {
            c.modeRepository.setMode(AppMode.THINK)
            val root = c.ideaRepository.save(IdeaEntity(title = "Stoicism"))
            val virtue = c.ideaRepository.save(IdeaEntity(title = "Virtue", parentId = root))
            val vice = c.ideaRepository.save(IdeaEntity(title = "Vice", parentId = root))
            val sub = c.ideaRepository.save(IdeaEntity(title = "Subordinate", parentId = virtue))
            val wisdom = c.ideaRepository.save(IdeaEntity(title = "Wisdom", parentId = sub))
            val folly = c.ideaRepository.save(IdeaEntity(title = "Folly", parentId = vice))
            c.linkRepository.addLabel("opposes", null)
            val label = c.linkRepository.labels.first().first { it.forward == "opposes" }
            c.linkRepository.link(ItemRef(ItemType.IDEA, folly), ItemRef(ItemType.IDEA, wisdom), label.id)
            listOf(root, folly, wisdom)
        }
        for (rootArg in listOf(Routes.encodeRef(ItemRef(ItemType.IDEA, ids[0])), "")) {
            val vm = MapViewModel(SavedStateHandle(mapOf(Routes.ARG_ROOT to rootArg)), c.linkRepository, c.mapPrefsRepository)
            var s: MapState? = null
            val job = CoroutineScope(Dispatchers.Default).launch { vm.state.collect { s = it } }
            repeat(300) {
                if (s?.edges?.any { it.label == "opposes" } != true) {
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
            }
            job.cancel()
            val link = s!!.edges.single { it.label == "opposes" }
            assertEquals(setOf(ItemRef(ItemType.IDEA, ids[1]), ItemRef(ItemType.IDEA, ids[2])), setOf(link.link.a, link.link.b))
            assertEquals(ItemRef(ItemType.IDEA, ids[1]), link.subject)
            assertTrue(!link.directed) // "opposes" has no reverse wording, so it reads both ways.
        }
    }
}
