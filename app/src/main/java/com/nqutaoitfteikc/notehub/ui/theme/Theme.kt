package com.nqutaoitfteikc.notehub.ui.theme

import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DC4FF), onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF203D60), onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFFA9CDC7), tertiary = Color(0xFFFFB4AB),
    background = Color(0xFF101419), onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1A2028), onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF28323E), onSurfaceVariant = Color(0xFFBEC9D6),
    outline = Color(0xFF8995A3), outlineVariant = Color(0xFF3C4755)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF245FA6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EDFF), onPrimaryContainer = Color(0xFF123C6B),
    secondary = Color(0xFF376A62), tertiary = Color(0xFFB34139),
    background = Color(0xFFF3F5F9), onBackground = Color(0xFF1C2838),
    surface = Color.White, onSurface = Color(0xFF1C2838),
    surfaceVariant = Color(0xFFEAF0F7), onSurfaceVariant = Color(0xFF536276),
    outline = Color(0xFF7C8999), outlineVariant = Color(0xFFDCE3ED)
)

@Composable
fun NoteHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
