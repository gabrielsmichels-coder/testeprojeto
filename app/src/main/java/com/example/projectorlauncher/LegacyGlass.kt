package com.example.projectorlauncher

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import com.example.projectorlauncher.adapted.Shadows

/** Scene/Frost's image-space glass fallback, with no API 31/33 shader dependency. */
class LegacyGlass(private val host: View, private val light: Boolean, private val contrast: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val xy = IntArray(2)
    private var texture: Bitmap? = null
    private var imageShader: BitmapShader? = null
    private val rect = RectF()
    fun setTexture(bitmap: Bitmap?) {
        texture = bitmap
        imageShader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        invalidateSelf()
    }
    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = host.resources.displayMetrics.density * 24
        val image = texture
        if (!contrast && image != null && !image.isRecycled) {
            host.getLocationInWindow(xy)
            val shader = imageShader ?: return
            matrix.setScale(host.rootView.width.toFloat() / image.width, host.rootView.height.toFloat() / image.height)
            matrix.postTranslate(-xy[0].toFloat(), -xy[1].toFloat()); shader.setLocalMatrix(matrix)
            paint.shader = shader; paint.color = Color.WHITE; canvas.drawRoundRect(rect, radius, radius, paint); paint.shader = null
        }
        paint.color = if (contrast) Color.BLACK else if (light) 0xdfffffff.toInt() else 0xe31a1b23.toInt()
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = host.resources.displayMetrics.density
        paint.color = if (contrast) Color.WHITE else if (light) 0xb3ffffff.toInt() else 0x668d98b2
        canvas.drawRoundRect(rect, radius, radius, paint); paint.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable legacy") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A fixed-size alpha mask is rasterized once, then nine-sliced even while the icon scales. */
class DockShadow(private val density: Float) : Drawable() {
    private val shadows = Shadows()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x70000000 }
    private val shape = RectF()
    override fun draw(canvas: Canvas) {
        shape.set(bounds.left + 10 * density, bounds.top + 8 * density, bounds.right - 10 * density, bounds.bottom - 8 * density)
        shadows.draw(canvas, shape, 12 * density, 5 * density, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable legacy") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
