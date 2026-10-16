package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Decorative icons; the navigation item's text supplies its accessible label. */
@Composable
internal fun NavigationSymbol(route: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(1.7f * unit)
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        when (route) {
            "notes" -> {
                drawRoundRect(color, point(3f, 5f), Size(18f * unit, 16f * unit), CornerRadius(3f * unit), style = stroke)
                drawLine(color, point(3f, 10f), point(21f, 10f), stroke.width)
                listOf(7f, 17f).forEach { drawLine(color, point(it, 2f), point(it, 7f), stroke.width) }
                listOf(7f, 12f, 17f).forEach { drawCircle(color, unit, point(it, 15f)) }
            }
            "photos" -> {
                drawRoundRect(color, point(3f, 3f), Size(18f * unit, 18f * unit), CornerRadius(3f * unit), style = stroke)
                drawCircle(color, 2f * unit, point(15.5f, 8f), style = stroke)
                drawPath(Path().apply { moveTo(3f * unit, 17f * unit); lineTo(9f * unit, 11f * unit); lineTo(18f * unit, 21f * unit) }, color, style = stroke)
            }
            else -> {
                listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (x, y) ->
                    drawLine(color, point(x, 3f), point(x, y - 3f), stroke.width)
                    drawLine(color, point(x, y + 3f), point(x, 21f), stroke.width)
                    drawCircle(color, 2.5f * unit, point(x, y), style = stroke)
                }
            }
        }
    }
}
