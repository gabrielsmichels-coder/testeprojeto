package com.example.projectorlauncher

import com.example.projectorlauncher.adapted.Spring
import org.junit.Assert.*
import org.junit.Test

class FreeTvAdaptationTest {
    @Test fun springSettlesAfterGrowthAndInterruption() {
        val spring = Spring(.34f, .82f, 1f)
        spring.target = 1.16f
        repeat(8) { spring.step(1f/60) }
        assertTrue(spring.value > 1f && spring.value < 1.20f)
        spring.target = 1f
        repeat(240) { spring.step(.032f) }
        assertTrue(spring.settled())
        assertEquals(1f, spring.value, .001f)
        assertFalse(spring.step(.016f))
    }
    @Test fun springRemainsFiniteAfterLongFrame() {
        val spring = Spring(.34f, .82f, 1f)
        spring.target = 1.2f
        spring.step(.25f)
        assertTrue(spring.value.isFinite())
        spring.snap(1f)
        assertTrue(spring.settled())
    }
    @Test fun tmdbImagePathsCannotEscapePrivateCacheOrChangeHost() {
        assertTrue(RecommendationPolicy.imagePath("/abc123-def_4.jpg"))
        for (value in listOf("/../../secret.jpg", "https://evil.example/x.jpg", "/x.jpg?key=secret", "/" + "a".repeat(200) + ".jpg", "null", ""))
            assertFalse(value, RecommendationPolicy.imagePath(value))
    }
    @Test fun imageSamplingAlwaysStartsNonzeroAndKeepsEnoughNormalPhotoDetail() {
        assertEquals(1, RecommendationPolicy.imageSample(0,0,0,0))
        assertEquals(1, RecommendationPolicy.imageSample(1280,720,1920,1080))
        assertEquals(2, RecommendationPolicy.imageSample(1280,720,560,320))
        assertEquals(2, RecommendationPolicy.imageSample(1024,512,300,200))
    }
    @Test fun unusualPanoramasCannotForceUnboundedTransientBitmaps() {
        for ((w,h) in listOf(8192 to 8192, 8192 to 2048, 2048 to 8192)) {
            val sample = RecommendationPolicy.imageSample(w,h,1920,1080)
            assertTrue(sample > 0)
            assertTrue(w.toLong()/sample*(h.toLong()/sample) <= 4L*1024*1024)
        }
    }
    @Test fun regionsAndCacheBudgetAreBounded() {
        assertEquals("BR", RecommendationPolicy.region("br"))
        assertEquals("PT", RecommendationPolicy.region("PT"))
        assertEquals("BR", RecommendationPolicy.region("../../us"))
        assertEquals(16L*1024*1024, RecommendationPolicy.DISK_LIMIT_BYTES)
        assertTrue(RecommendationPolicy.MAX_IMAGE_BYTES <= 2*1024*1024)
    }
}
