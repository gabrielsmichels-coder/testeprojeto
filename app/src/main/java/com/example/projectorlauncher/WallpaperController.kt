package com.example.projectorlauncher

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Only one visible raster plus an incoming raster. No retained wallpaper cache. */
class WallpaperController(private val first: ImageView, private val second: ImageView,
                          private val reducedMotion: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    private var current = first
    private var incoming = second
    private var requested: String? = null
    private var task: Future<*>? = null
    @Volatile private var closed = false

    fun request(scene: String, immediate: Boolean = false) {
        if (closed || requested == scene) return
        requested = scene
        val token = ++generation
        handler.removeCallbacksAndMessages(null)
        task?.cancel(false)
        // Settle an interrupted fade to its dominant frame and release the other slot.
        first.animate().cancel(); second.animate().cancel()
        if (incoming.drawable != null && incoming.alpha >= 0.5f) {
            val previous = current; current = incoming; incoming = previous
        }
        current.alpha = 1f
        incoming.setImageDrawable(null); incoming.tag = null; incoming.alpha = 0f
        if (current.tag == scene) return
        handler.postDelayed({
            if (closed || generation != token) return@postDelayed
            val width = first.width.coerceAtLeast(1); val height = first.height.coerceAtLeast(1)
            task = worker.submit {
                if (generation != token) return@submit
                val bitmap = CinematicArt.render(scene, width, height)
                if (closed || generation != token) { bitmap.recycle(); return@submit }
                handler.post {
                    if (closed || generation != token) { bitmap.recycle(); return@post }
                    incoming.setImageBitmap(bitmap); incoming.tag = scene
                    incoming.bringToFront()
                    if (reducedMotion || current.drawable == null) finishTransition(token)
                    else incoming.animate().alpha(1f).setDuration(CinematicPolicy.CROSSFADE_MS)
                        .withEndAction { finishTransition(token) }.start()
                }
            }
        }, if (immediate) 0L else CinematicPolicy.DEBOUNCE_MS)
    }

    private fun finishTransition(token: Int) {
        if (closed || generation != token) return
        current.setImageDrawable(null); current.tag = null; current.alpha = 0f
        incoming.alpha = 1f
        val previous = current; current = incoming; incoming = previous
    }

    fun close() {
        closed = true; generation++
        handler.removeCallbacksAndMessages(null); task?.cancel(false)
        first.animate().cancel(); second.animate().cancel()
        first.setImageDrawable(null); second.setImageDrawable(null)
        worker.shutdownNow()
    }
}
