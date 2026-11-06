package com.nqutaoitfteikc.notehub.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.nqutaoitfteikc.notehub.domain.FontSizeRules

/** Retains the platform's nonlinear font conversion, applying the app choice in sp first. */
private class AppDensity(private val base: Density, private val factor: Float) : Density {
    override val density get() = base.density
    override val fontScale get() = base.fontScale * factor
    override fun TextUnit.toDp(): Dp = with(base) { (this@toDp * factor).toDp() }
    override fun Dp.toSp(): TextUnit = with(base) { this@toSp.toSp() / factor }
}

@Composable
internal fun AppFontScale(scale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val safe = FontSizeRules.safe(scale)
    val density = remember(base, safe) { if (safe == 1f) base else AppDensity(base, safe) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
