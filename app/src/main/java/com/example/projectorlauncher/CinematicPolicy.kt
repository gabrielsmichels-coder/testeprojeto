package com.example.projectorlauncher

import java.util.Locale
import kotlin.math.roundToInt

object CinematicPolicy {
    const val DEBOUNCE_MS = 180L
    const val CROSSFADE_MS = 380L
    fun scene(packageName: String?): String = when (packageName?.lowercase(Locale.ROOT)) {
        "com.netflix.ninja", "com.netflix.mediaclient" -> "netflix"
        "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient" -> "prime"
        "com.wbd.stream", "com.wbd.hbomax", "com.hbo.hbonow", "com.hbo.hbogo", "com.hbo.max" -> "max"
        "com.disney.disneyplus", "com.disney.starplus" -> "disney"
        "com.globo.globoplay", "com.globo.globotv" -> "globo"
        "com.google.android.youtube.tv", "com.google.android.youtube" -> "youtube"
        "com.apple.atve.androidtv", "com.apple.atve.androidtv.appletv" -> "apple"
        "com.cbs.ott", "com.cbs.app", "com.paramount.plus" -> "paramount"
        else -> "neutral"
    }
    /** Screen-sized RGB_565 output, capped at 1080p; at most ~8 MiB for two frames. */
    fun renderSize(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1); val h = height.coerceAtLeast(1)
        val scale = minOf(1.0, 1920.0 / w, 1080.0 / h)
        return (w * scale).roundToInt().coerceAtLeast(1) to (h * scale).roundToInt().coerceAtLeast(1)
    }
}

object GlassColors {
    fun fill(palette: LauncherPalette, focused: Boolean, contrast: Boolean): Int {
        val color = if (focused) palette.focused else palette.surface
        val alpha = if (contrast) 255 else if (focused) 246 else 239
        return (color and 0x00ffffff) or (alpha shl 24)
    }
}
