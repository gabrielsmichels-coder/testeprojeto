package com.example.projectorlauncher

import android.view.Choreographer
import android.view.View
import com.example.projectorlauncher.adapted.Spring

/** Shared finite frame loop. No callbacks are posted once the adapted springs settle. */
class SpringMotion : Choreographer.FrameCallback {
    private val active = mutableMapOf<View, Spring>()
    private var scheduled = false
    private var lastFrame = 0L
    fun focus(view: View, target: Float, reduced: Boolean) {
        view.animate().cancel()
        if (reduced) { active.remove(view); view.scaleX = 1f; view.scaleY = 1f; return }
        active.getOrPut(view) { Spring(.34f, .82f, view.scaleX) }.target = target
        if (!scheduled) { lastFrame = 0L; scheduled = true; Choreographer.getInstance().postFrameCallback(this) }
    }
    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastFrame == 0L) 1f / 60 else ((frameTimeNanos - lastFrame) / 1e9f).coerceIn(0f, .032f)
        lastFrame = frameTimeNanos
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (view, spring) = iterator.next()
            val moving = spring.step(dt)
            view.scaleX = spring.value; view.scaleY = spring.value
            if (!moving || !view.isAttachedToWindow) iterator.remove()
        }
        scheduled = active.isNotEmpty()
        if (scheduled) Choreographer.getInstance().postFrameCallback(this) else lastFrame = 0L
    }
    fun stop() {
        Choreographer.getInstance().removeFrameCallback(this)
        active.keys.forEach { it.scaleX = 1f; it.scaleY = 1f }
        active.clear(); scheduled = false; lastFrame = 0L
    }
}
