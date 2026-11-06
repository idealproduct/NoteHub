package com.nqutaoitfteikc.notehub.domain

object FontSizeRules {
    fun safe(value: Float): Float = if (value.isFinite()) value.coerceIn(0.8f, 1.5f) else 1f
}
