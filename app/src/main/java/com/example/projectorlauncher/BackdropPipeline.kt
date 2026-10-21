package com.example.projectorlauncher

import android.graphics.*
import com.example.projectorlauncher.adapted.BackdropBlur
import java.io.File

/** Backdrop/Scene adaptation: crop once, apply directional scrims, reduce/blur once on I/O worker. */
object BackdropPipeline {
    data class Frame(val image: Bitmap, val frost: Bitmap)
    fun load(key: String, width: Int, height: Int): Frame {
        val bitmap = if (key.startsWith("photo:")) photo(File(key.removePrefix("photo:")), width, height)
            ?: CinematicArt.render("neutral", width, height) else CinematicArt.render(key, width, height)
        val pixels = BackdropBlur.reduce(bitmap, 128, 72, 3, 3)
        val frost = Bitmap.createBitmap(pixels, 128, 72, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.DITHER_FLAG)
        // A photo's bright left side gets stronger protection for hero text.
        val sample = bitmap.getPixel(bitmap.width / 4, bitmap.height / 3)
        val bright = (Color.red(sample) + Color.green(sample) + Color.blue(sample)) / 765f
        val alpha = (145 + bright * 50).toInt().coerceAtMost(205)
        val canvas = Canvas(bitmap)
        paint.shader = LinearGradient(0f, 0f, bitmap.width * .78f, 0f,
            intArrayOf(Color.argb(alpha, 7, 9, 15), 0x4007090f, 0x0007090f), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.shader = LinearGradient(0f, bitmap.height.toFloat(), 0f, bitmap.height * .55f,
            0xb307090f.toInt(), 0x0007090f, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        return Frame(bitmap, frost)
    }
    fun photo(file: File, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        val (w,h) = CinematicPolicy.renderSize(width, height)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = RecommendationPolicy.imageSample(bounds.outWidth, bounds.outHeight, w, h)
        }
        val source = BitmapFactory.decodeFile(file.path, options) ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val scale = maxOf(w.toFloat() / source.width, h.toFloat() / source.height)
        val sw = source.width * scale; val sh = source.height * scale
        Canvas(out).drawBitmap(source, null, RectF((w-sw)/2, (h-sh)/2, (w+sw)/2, (h+sh)/2), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return out
    }
}
