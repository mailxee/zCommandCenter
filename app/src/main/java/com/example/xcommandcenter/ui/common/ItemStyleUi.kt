package com.example.xcommandcenter.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.xcommandcenter.domain.ItemStyle
import com.example.xcommandcenter.domain.ItemStyles
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.StyleColor
import com.example.xcommandcenter.domain.StyleFont

/** Every tab's item style, provided once at the root so any screen can style an item's title. */
val LocalItemStyles = compositionLocalOf { ItemStyles() }

/** True when the theme's surface is dark, so styles pick their light shades. */
@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The style for titles of [type]'s items, to merge into a text style or apply to part of a string. */
@Composable
fun itemSpanStyle(type: ItemType): SpanStyle = LocalItemStyles.current[type].toSpanStyle(isDarkSurface())

/** [base] with [type]'s item style on top. */
@Composable
fun itemTextStyle(type: ItemType, base: TextStyle): TextStyle = base.merge(itemSpanStyle(type))

fun ItemStyle.toSpanStyle(dark: Boolean): SpanStyle = SpanStyle(
    color = color.toColor(dark),
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
    fontFamily = font.family,
)

/** A darker shade on light surfaces and a lighter one on dark surfaces, so the text stays readable. */
fun StyleColor.toColor(dark: Boolean): Color {
    val (light, darkShade) = when (this) {
        StyleColor.DEFAULT -> return Color.Unspecified
        StyleColor.RED -> 0xFFC62828 to 0xFFFF8A80
        StyleColor.ORANGE -> 0xFFD84315 to 0xFFFFAB91
        StyleColor.AMBER -> 0xFF8D6200 to 0xFFFFD54F
        StyleColor.GREEN -> 0xFF2E7D32 to 0xFFA5D6A7
        StyleColor.TEAL -> 0xFF00796B to 0xFF80CBC4
        StyleColor.BLUE -> 0xFF1565C0 to 0xFF90CAF9
        StyleColor.INDIGO -> 0xFF3949AB to 0xFF9FA8DA
        StyleColor.PURPLE -> 0xFF7B1FA2 to 0xFFCE93D8
        StyleColor.PINK -> 0xFFC2185B to 0xFFF48FB1
        StyleColor.BROWN -> 0xFF6D4C41 to 0xFFBCAAA4
        StyleColor.GREY -> 0xFF616161 to 0xFFBDBDBD
    }
    return Color(if (dark) darkShade else light)
}

val StyleFont.family: FontFamily?
    get() = when (this) {
        StyleFont.DEFAULT -> null
        StyleFont.SERIF -> FontFamily.Serif
        StyleFont.MONOSPACE -> FontFamily.Monospace
        StyleFont.CURSIVE -> FontFamily.Cursive
    }
