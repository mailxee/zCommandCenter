package com.example.xcommandcenter.ui.links

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.LineWidth

/**
 * The dash pattern for [style] at [px] pixels per dp; null for a solid line. [scale] spreads the
 * pattern for a thick line, whose dots would otherwise run together.
 */
internal fun dashEffect(style: LineStyle, px: Float, scale: Float = 1f): PathEffect? {
    val k = px * maxOf(1f, scale)
    return when (style) {
        LineStyle.SOLID -> null
        LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(8f * k, 5f * k))
        LineStyle.LONG_DASH -> PathEffect.dashPathEffect(floatArrayOf(16f * k, 6f * k))
        LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.1f * k, 6f * k))
        LineStyle.DASH_DOT -> PathEffect.dashPathEffect(floatArrayOf(10f * k, 5f * k, 0.1f * k, 5f * k))
    }
}

/** Dots need round ends to show; the other patterns have square ones. */
internal fun capOf(style: LineStyle): StrokeCap =
    if (style == LineStyle.DOTTED || style == LineStyle.DASH_DOT) StrokeCap.Round else StrokeCap.Butt

/** A short line drawn in [style] and [width], for legends and the line choices. */
@Composable
internal fun LineSample(color: Color, style: LineStyle, thick: Boolean, modifier: Modifier, width: LineWidth = LineWidth.NORMAL) {
    Canvas(modifier) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            (if (thick) 4.dp else 2.5.dp).toPx() * width.factor,
            cap = capOf(style),
            pathEffect = dashEffect(style, 1.dp.toPx(), width.factor),
        )
    }
}

/**
 * How one kind of link is drawn on the map: its pattern (solid, dashes, dots…) and its width.
 * [name] tells screen readers which kind; [thick] marks Under, drawn thicker than links.
 */
@Composable
internal fun LineChoices(
    name: String,
    color: Color,
    thick: Boolean,
    style: LineStyle,
    width: LineWidth,
    onStyle: (LineStyle) -> Unit,
    onWidth: (LineWidth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LineStyle.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = style == option,
                    onClick = { onStyle(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, LineStyle.entries.size),
                    icon = {},
                    modifier = Modifier.semantics { contentDescription = "$name: ${option.label}" },
                ) {
                    LineSample(color, option, thick, Modifier.width(26.dp).height(12.dp))
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LineWidth.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = width == option,
                    onClick = { onWidth(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, LineWidth.entries.size),
                    icon = {},
                    modifier = Modifier.semantics { contentDescription = "$name: ${option.label}" },
                ) {
                    Text(option.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
