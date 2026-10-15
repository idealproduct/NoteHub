package com.nqutaoitfteikc.notehub.ui

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
internal fun pickerContext(): Context {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(context, dark) {
        // Keep the Activity as the base so dialogs retain its window token.
        ContextThemeWrapper(context,
            if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert).apply {
            applyOverrideConfiguration(Configuration().apply { setLocale(Locale.TAIWAN) })
        }
    }
}
