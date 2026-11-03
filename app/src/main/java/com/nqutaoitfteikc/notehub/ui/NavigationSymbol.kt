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
            "locate" -> {
                drawCircle(color,7f*unit,point(12f,12f),style=stroke)
                drawCircle(color,2.5f*unit,point(12f,12f))
                drawLine(color,point(12f,1f),point(12f,5f),stroke.width)
                drawLine(color,point(12f,19f),point(12f,23f),stroke.width)
                drawLine(color,point(1f,12f),point(5f,12f),stroke.width)
                drawLine(color,point(19f,12f),point(23f,12f),stroke.width)
            }
            "map" -> {
                drawPath(Path().apply {
                    moveTo(3f*unit,5f*unit); lineTo(9f*unit,3f*unit); lineTo(15f*unit,5f*unit); lineTo(21f*unit,3f*unit)
                    lineTo(21f*unit,19f*unit); lineTo(15f*unit,21f*unit); lineTo(9f*unit,19f*unit); lineTo(3f*unit,21f*unit); close()
                },color,style=stroke)
                drawLine(color,point(9f,3f),point(9f,19f),stroke.width)
                drawLine(color,point(15f,5f),point(15f,21f),stroke.width)
            }
            "achievements" -> {
                drawPath(Path().apply {
                    moveTo(12f*unit,2f*unit); lineTo(15f*unit,9f*unit); lineTo(22f*unit,10f*unit)
                    lineTo(17f*unit,15f*unit); lineTo(18f*unit,22f*unit); lineTo(12f*unit,18f*unit)
                    lineTo(6f*unit,22f*unit); lineTo(7f*unit,15f*unit); lineTo(2f*unit,10f*unit)
                    lineTo(9f*unit,9f*unit); close()
                },color,style=stroke)
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
