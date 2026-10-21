package com.example.projectorlauncher

object RecommendationPolicy {
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val DISK_LIMIT_BYTES = 16L * 1024 * 1024
    val providers = mapOf("netflix" to 8, "prime" to 9, "disney" to 337, "max" to 1899, "globo" to 307, "apple" to 350, "paramount" to 531)
    /** Nonzero sampling, plus a 4-megapixel transient decode ceiling for unusual panoramas. */
    fun imageSample(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        val minimum = minOf(sourceWidth, sourceHeight).coerceAtLeast(1)
        while (sample <= minimum / 2 && sourceWidth / (sample * 2) >= targetWidth.coerceAtLeast(1) &&
            sourceHeight / (sample * 2) >= targetHeight.coerceAtLeast(1)) sample *= 2
        while (sourceWidth.toLong() / sample * (sourceHeight.toLong() / sample) > 4L * 1024 * 1024) sample *= 2
        return sample
    }
    fun imagePath(path: String) = path.length <= 128 && path.matches(Regex("/[A-Za-z0-9_-]+\\.(jpg|png)"))
    fun region(value: String) = value.uppercase(java.util.Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "BR"
}
