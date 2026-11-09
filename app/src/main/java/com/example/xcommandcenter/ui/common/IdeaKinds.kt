package com.example.xcommandcenter.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.IdeaKind

@DrawableRes
fun kindIcon(kind: IdeaKind): Int = when (kind) {
    IdeaKind.VISION -> R.drawable.ic_kind_vision
    IdeaKind.BELIEF -> R.drawable.ic_kind_belief
    IdeaKind.PRINCIPLE -> R.drawable.ic_kind_principle
    IdeaKind.IDEA -> R.drawable.ic_idea
    IdeaKind.QUESTION -> R.drawable.ic_kind_question
}

/** Each kind's own color, readable on the theme's surface. */
@Composable
fun kindColor(kind: IdeaKind): Color {
    val dark = isDarkSurface()
    return Color(
        when (kind) {
            IdeaKind.VISION -> if (dark) 0xFFCE93D8 else 0xFF7B1FA2
            IdeaKind.BELIEF -> if (dark) 0xFFF48FB1 else 0xFFC2185B
            IdeaKind.PRINCIPLE -> if (dark) 0xFF80CBC4 else 0xFF00796B
            IdeaKind.IDEA -> if (dark) 0xFFFFCC80 else 0xFFB26A00
            IdeaKind.QUESTION -> if (dark) 0xFF90CAF9 else 0xFF1565C0
        },
    )
}

@Composable
fun KindIcon(kind: IdeaKind, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    Icon(painterResource(kindIcon(kind)), contentDescription = kind.label, tint = kindColor(kind), modifier = modifier.size(size))
