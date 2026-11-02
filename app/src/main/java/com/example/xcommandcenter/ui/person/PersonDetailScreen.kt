package com.example.xcommandcenter.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PersonItemKind
import com.example.xcommandcenter.domain.Platform
import com.example.xcommandcenter.domain.Profiles
import com.example.xcommandcenter.domain.WebLinks
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.ListEditor
import com.example.xcommandcenter.ui.common.PriorityField
import com.example.xcommandcenter.ui.common.RemindersSection
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.common.tabAccent

@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: PersonDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val person = viewModel.person

    DetailScaffold(
        title = if (id == 0L) "New person" else "Person",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = person.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = person.bio,
            onValueChange = { v -> viewModel.update { it.copy(bio = v) } },
            label = { Text("Short bio (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(person.bio)
        ListEditor(
            title = "Profiles",
            lines = viewModel.profiles,
            checkable = false,
            onAdd = viewModel::addProfile,
            onUpdate = viewModel::updateProfile,
            onRemove = viewModel::removeProfile,
            onMove = viewModel::moveProfile,
            addLabel = "Add a profile link or @handle",
            leading = { line -> ProfileBadge(line.text) },
        )
        ListEditor(
            title = "Items",
            lines = viewModel.items,
            checkable = false,
            onAdd = viewModel::addItem,
            onUpdate = viewModel::updateItem,
            onRemove = viewModel::removeItem,
            onMove = viewModel::moveItem,
            addLabel = "Add an idea, video or post link",
            leading = { line -> ItemKindIcon(line.text) },
        )
        PriorityField(person.priority) { p -> viewModel.update { it.copy(priority = p) } }
        RemindersSection(viewModel)
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
    }
}

/** The profile's platform as a small badge (a globe for a website); tapping it opens the profile. */
@Composable
private fun ProfileBadge(profile: String) {
    val uriHandler = LocalUriHandler.current
    val platform = Profiles.platformOf(profile)
    val open = Modifier.clickable(enabled = profile.isNotBlank(), onClickLabel = "Open ${platform.label} profile") {
        runCatching { uriHandler.openUri(Profiles.url(profile)) }
    }
    val short = platform.short
    if (short == null) {
        Box(Modifier.size(36.dp).clip(CircleShape).then(open), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_web), contentDescription = Platform.WEBSITE.label, modifier = Modifier.size(20.dp))
        }
    } else {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.width(30.dp).clip(RoundedCornerShape(6.dp)).then(open),
        ) {
            Text(
                short,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

/** What the item is (an idea, a video, a post or another link), in the tab's color; tapping it opens the item's link. */
@Composable
private fun ItemKindIcon(text: String) {
    val uriHandler = LocalUriHandler.current
    val kind = Profiles.kindOf(text)
    val link = WebLinks.find(text).firstOrNull()
    val (icon, label) = when (kind) {
        PersonItemKind.IDEA -> R.drawable.ic_idea to "Idea"
        PersonItemKind.VIDEO -> R.drawable.ic_video to "Video"
        PersonItemKind.POST -> R.drawable.ic_post to "Post"
        PersonItemKind.LINK -> R.drawable.ic_link to "Link"
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = link != null, onClickLabel = "Open $label") { link?.let { runCatching { uriHandler.openUri(it.url) } } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tabAccent(ItemType.PERSON), modifier = Modifier.size(20.dp))
    }
}
