package com.example.projectorlauncher

import org.junit.Assert.*
import org.junit.Test

class CinematicPolicyTest {
    @Test fun streamingPackagesResolveWithoutLabelsOrNetwork() {
        mapOf("com.netflix.ninja" to "netflix", "com.netflix.mediaclient" to "netflix",
            "com.amazon.amazonvideo.livingroom" to "prime", "com.amazon.avod.thirdpartyclient" to "prime",
            "com.wbd.stream" to "max", "com.hbo.hbonow" to "max", "com.hbo.hbogo" to "max",
            "com.disney.disneyplus" to "disney", "com.globo.globoplay" to "globo",
            "com.globo.globotv" to "globo", "com.google.android.youtube.tv" to "youtube",
            "com.apple.atve.androidtv" to "apple", "com.cbs.ott" to "paramount").forEach { (pkg, expected) ->
            assertEquals(expected, CinematicPolicy.scene(pkg))
        }
    }
    @Test fun absentOrSimilarButUnknownPackagesUseNeutralArt() {
        for (pkg in listOf(null, "", "com.example.player", "com.netflix.ninja.fake"))
            assertEquals("neutral", CinematicPolicy.scene(pkg))
        assertEquals("netflix", CinematicPolicy.scene("COM.NETFLIX.NINJA"))
    }
    @Test fun resolutionPreservesScreenAspectWithoutUpscalingOrHugeAllocations() {
        assertEquals(1280 to 720, CinematicPolicy.renderSize(1280, 720))
        assertEquals(1920 to 1080, CinematicPolicy.renderSize(3840, 2160))
        assertEquals(640 to 480, CinematicPolicy.renderSize(640, 480))
        assertEquals(1 to 1, CinematicPolicy.renderSize(0, -1))
        for ((w, h) in listOf(8192 to 4096, 4096 to 8192, 1920 to 1200)) {
            val (rw, rh) = CinematicPolicy.renderSize(w, h)
            assertTrue(rw <= 1920 && rh <= 1080)
            assertTrue(kotlin.math.abs(rw.toDouble() / rh - w.toDouble() / h) < 0.003)
            assertTrue(rw.toLong() * rh * 2 * 2 <= 1920L * 1080 * 4)
        }
    }
    @Test fun highContrastNeverUsesTranslucentPanels() {
        val p = LauncherPalette.forMode("contrast")
        assertEquals(255, GlassColors.fill(p, false, true) ushr 24)
        assertEquals(255, GlassColors.fill(p, true, true) ushr 24)
        assertTrue(GlassColors.fill(LauncherPalette.forMode("dark"), false, false) ushr 24 < 255)
    }
}
