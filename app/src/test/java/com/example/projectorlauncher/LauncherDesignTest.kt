package com.example.projectorlauncher

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class LauncherDesignTest {
    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * linear(color shr 16 and 255) + .7152 * linear(color shr 8 and 255) + .0722 * linear(color and 255)
    }
    private fun ratio(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun themesKeepReadableTextOnEveryCardState() {
        for (mode in listOf("light", "dark", "contrast")) {
            val palette = LauncherPalette.forMode(mode)
            for (background in listOf(palette.background, palette.surface, palette.focused)) {
                assertTrue("$mode main text", ratio(palette.text, background) >= 4.5)
                assertTrue("$mode secondary text", ratio(palette.secondary, background) >= 4.5)
            }
            assertTrue("$mode focus border", ratio(palette.accent, palette.focused) >= 3.0)
        }
    }
    private fun composite(color: Int, behind: Int): Int {
        val alpha = (color ushr 24) / 255.0
        fun channel(shift: Int) = (((color shr shift and 255) * alpha +
            (behind shr shift and 255) * (1 - alpha)).toInt() shl shift)
        return 0xff000000.toInt() or channel(16) or channel(8) or channel(0)
    }
    @Test fun translucentGlassKeepsTextReadableOverBothExtremeWallpapers() {
        for (mode in listOf("light", "dark", "contrast")) {
            val p = LauncherPalette.forMode(mode)
            for (focused in listOf(false, true)) for (behind in listOf(0xff000000.toInt(), 0xffffffff.toInt())) {
                val base = composite(GlassColors.fill(p, focused, mode == "contrast"), behind)
                val highlighted = if (mode == "contrast") base else composite(0x17ffffff, base)
                assertTrue("$mode glass main", ratio(p.text, highlighted) >= 4.5)
                assertTrue("$mode glass secondary", ratio(p.secondary, highlighted) >= 4.5)
            }
        }
    }
    @Test fun favoritesCanBeRemovedEvenWhenFull() {
        val full = (1..FavoritePolicy.LIMIT).map { "app.$it" }
        assertNull(FavoritePolicy.toggle(full, "another.app"))
        val removed = FavoritePolicy.toggle(full, "app.2")!!
        assertEquals(5, removed.size)
        assertFalse("app.2" in removed)
        assertEquals(removed + "another.app", FavoritePolicy.toggle(removed, "another.app"))
    }
    @Test fun explicitEmptyFavoritesRemainEmpty() {
        assertEquals(emptyList<String>(), FavoritePolicy.decode(""))
        assertEquals(emptyList<String>(), FavoritePolicy.toggle(listOf("app.a"), "app.a"))
    }
    @Test fun storedFavoritesPreserveOrderAndRemoveDuplicates() {
        assertEquals(listOf("app.b", "app.a"), FavoritePolicy.decode("app.b|app.a|app.b|"))
        assertEquals(FavoritePolicy.LIMIT, FavoritePolicy.decode((1..12).joinToString("|") { "app.$it" }).size)
    }
}
