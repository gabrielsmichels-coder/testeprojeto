package com.example.projectorlauncher

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** HomeView's three-card/dock composition, with independent native focusable layers.
 * Adapted design: Free My TV contributors (2026), MIT notice in res/raw/free_my_tv_license.txt. */
class HomeStage(context: Context) : ViewGroup(context) {
    private lateinit var heading: View
    private lateinit var picks: View
    private lateinit var dock: View
    fun setLayers(header: View, recommendations: View, shelf: View) {
        heading = header; picks = recommendations; dock = shelf
        clipChildren = false; clipToPadding = false
        addView(heading); addView(picks); addView(dock)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (layoutParams.height > 0) layoutParams.height else (resources.displayMetrics.heightPixels * .76f).toInt()
        val gap = (12 * resources.displayMetrics.density).toInt()
        val header = (34 * resources.displayMetrics.density).toInt()
        val shelf = (h * .27f).toInt().coerceAtLeast((80 * resources.displayMetrics.density).toInt())
        heading.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(header, MeasureSpec.EXACTLY))
        picks.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((h-header-shelf-gap*3).coerceAtLeast(1), MeasureSpec.EXACTLY))
        dock.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(shelf, MeasureSpec.EXACTLY))
        setMeasuredDimension(w,h)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        heading.layout(0,0,width,heading.measuredHeight)
        val gap = (12 * resources.displayMetrics.density).toInt()
        picks.layout(0,heading.bottom+gap,width,heading.bottom+gap+picks.measuredHeight)
        dock.layout(0,height-dock.measuredHeight,width,height)
    }
}
