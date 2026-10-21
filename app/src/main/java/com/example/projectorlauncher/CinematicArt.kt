package com.example.projectorlauncher

import android.graphics.*

/** Original abstract landscape artwork dedicated to CC0; no streaming imagery/logos.
 * Rasterized once per scene change on a worker, never drawn as a continuous effect. */
object CinematicArt {
    fun render(scene: String, width: Int, height: Int): Bitmap {
        val (w, h) = CinematicPolicy.renderSize(width, height)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        canvas.scale(w / 1280f, h / 720f)
        val colors = when (scene) {
            "netflix" -> intArrayOf(0xff180c19.toInt(), 0xff782a43.toInt(), 0xffed846d.toInt())
            "prime" -> intArrayOf(0xff051d34.toInt(), 0xff126c99.toInt(), 0xff8edfe0.toInt())
            "max" -> intArrayOf(0xff150c35.toInt(), 0xff554193.toInt(), 0xffc9a4ec.toInt())
            "disney" -> intArrayOf(0xff071b38.toInt(), 0xff25569d.toInt(), 0xffa9d9f2.toInt())
            "globo" -> intArrayOf(0xff281433.toInt(), 0xffbd5656.toInt(), 0xffffc77c.toInt())
            "youtube" -> intArrayOf(0xff21131e.toInt(), 0xff884443.toInt(), 0xffe7b395.toInt())
            "apple" -> intArrayOf(0xff0c2226.toInt(), 0xff34676d.toInt(), 0xffc7e8da.toInt())
            "paramount" -> intArrayOf(0xff071a39.toInt(), 0xff2e5a9b.toInt(), 0xffb6cee9.toInt())
            else -> intArrayOf(0xff11172f.toInt(), 0xff4b587c.toInt(), 0xffd4b89c.toInt())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1280f, 720f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1280f, 720f, paint)
        // Soft light is a static radial gradient, not a blur pass or GPU effect.
        paint.shader = RadialGradient(960f, 260f, 420f, intArrayOf(0x80ffffff.toInt(), 0x00ffffff), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1280f, 720f, paint)
        paint.shader = null
        paint.color = (colors[2] and 0xffffff) or 0x99000000.toInt()
        canvas.drawCircle(960f, 255f, if (scene == "disney") 76f else 54f, paint)
        val random = java.util.Random(scene.hashCode().toLong())
        paint.color = 0x90ffffff.toInt()
        repeat(45) { canvas.drawCircle(random.nextFloat() * 1280f, random.nextFloat() * 310f, 0.6f + random.nextFloat(), paint) }
        // Three original curved ridgelines create cinematic depth without photographs.
        repeat(3) { layer ->
            val base = 395f + layer * 85f
            val path = Path().apply {
                moveTo(0f, base + 60f)
                cubicTo(220f, base - 100f, 370f, base + 130f, 640f, base - 15f)
                cubicTo(900f, base - 180f + layer * 45f, 1060f, base + 80f, 1280f, base - 65f)
                lineTo(1280f, 720f); lineTo(0f, 720f); close()
            }
            paint.shader = LinearGradient(0f, base - 60f, 0f, 720f,
                intArrayOf((colors[0] and 0xffffff) or ((100 + layer * 45) shl 24), 0xff090f20.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        return result
    }
}
