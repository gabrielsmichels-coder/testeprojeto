package com.example.projectorlauncher

/** Pure values shared by all XML screens, cards, dialogs and focus states. */
data class LauncherPalette(
    val background: Int, val surface: Int, val focused: Int,
    val text: Int, val secondary: Int, val accent: Int, val outline: Int
) {
    companion object {
        fun forMode(mode: String) = when (mode) {
            "light" -> LauncherPalette(0xffedf1f7.toInt(), 0xffffffff.toInt(), 0xffd6e7ff.toInt(),
                0xff162034.toInt(), 0xff45516b.toInt(), 0xff154e91.toInt(), 0xff64748b.toInt())
            "contrast" -> LauncherPalette(0xff000000.toInt(), 0xff000000.toInt(), 0xff182638.toInt(),
                0xffffffff.toInt(), 0xffffffff.toInt(), 0xffffff00.toInt(), 0xffffffff.toInt())
            else -> LauncherPalette(0xff101522.toInt(), 0xff1c2436.toInt(), 0xff314767.toInt(),
                0xfff5f7fc.toInt(), 0xffb8c2d6.toInt(), 0xffa8c7fa.toInt(), 0xff66758e.toInt())
        }
    }
}

object FavoritePolicy {
    const val LIMIT = 6
    /** Null means full. An explicitly empty selection stays empty. */
    fun toggle(current: List<String>, key: String): List<String>? = when {
        key in current -> current.filterNot { it == key }
        current.size >= LIMIT -> null
        else -> current + key
    }
    fun decode(saved: String): List<String> = saved.split('|').filter { it.isNotBlank() }.distinct().take(LIMIT)
}
