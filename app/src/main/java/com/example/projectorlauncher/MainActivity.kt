package com.example.projectorlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

/** Local package icons, bounded raster size, no network or background services. */
class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val loader = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var appearance = 2
    private var contrast = false
    private var textColorValue = Color.WHITE
    private var background = Color.BLACK
    private val iconBindings = mutableListOf<IconBinding>()
    private data class IconBinding(val image: ImageView, val component: ComponentName, var loading: Boolean = false)
    private data class App(val label: String, val component: ComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appearance = preferences.getInt("theme", 2)
        contrast = preferences.getBoolean("contrast", false)
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun render() {
        val dark = when (appearance) {
            1 -> false
            2 -> true
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val mode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        val themed = createConfigurationContext(config)
        setContentView(android.view.LayoutInflater.from(themed).inflate(R.layout.activity_main, null))
        background = if (contrast || dark) Color.rgb(16, 21, 34) else Color.rgb(242, 244, 248)
        if (contrast) background = Color.BLACK
        textColorValue = if (contrast || dark) Color.WHITE else Color.rgb(24, 33, 52)
        findViewById<View>(R.id.root).setBackgroundColor(background)
        listOf(R.id.brand, R.id.clock, R.id.headline, R.id.subtitle, R.id.shortcutTitle, R.id.appsTitle).forEach {
            findViewById<TextView>(it).setTextColor(textColorValue)
        }
        if (contrast) {
            (findViewById<View>(R.id.headline).parent as View).setBackgroundColor(Color.BLACK)
        }
        iconBindings.clear()
        val top = findViewById<LinearLayout>(R.id.topActions)
        listOf(
            "Wi-Fi" to Intent(Settings.ACTION_WIFI_SETTINGS),
            "Entradas" to Intent("com.kystar.settings.SOURCE_LIST").setPackage("com.kystar.projector.settings"),
            "Configurações" to Intent(Settings.ACTION_SETTINGS)
        ).forEach { (label, intent) -> top.addView(card(label) { launch(intent, label) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(128), dp(60)).apply { setMargins(dp(3), 0, dp(3), 0) }
        }) }
        val hero = findViewById<LinearLayout>(R.id.hero)
        hero.addView(card("▶  Fire Stick / HDMI") {
            launch(Intent().setComponent(ComponentName("com.hisilicon.tvui", "com.hisilicon.tvui.MainActivity")), "Fire Stick / HDMI")
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(320), dp(84)) })
        val row = findViewById<LinearLayout>(R.id.shortcuts)
        listOf(
            "Correção trapezoidal" to Intent("com.kystar.settings.SCREEN_CORRECT").setPackage("com.kystar.projector.settings"),
            "Zoom" to Intent("com.kystar.settings.SCREEN_ZOOM").setPackage("com.kystar.projector.settings"),
            "Seleção de fontes" to Intent("com.kystar.settings.SOURCE_LIST").setPackage("com.kystar.projector.settings"),
            "Orientação da projeção" to Intent("com.kystar.settings.PROJECTION_INSTALL_MODE").setPackage("com.kystar.projector.settings")
        ).forEach { (label, intent) -> row.addView(card(label) { launch(intent, label) }) }
        listOf(
            "Configurações" to Settings.ACTION_SETTINGS,
            "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "Aplicativos" to Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            "Tela" to Settings.ACTION_DISPLAY_SETTINGS
        ).forEach { (label, action) -> row.addView(card(label) { launch(Intent(action)) }) }
        val themeButton = card(getString(R.string.appearance)) { chooseAppearance() }
        row.addView(themeButton)
        hero.getChildAt(hero.childCount - 1).requestFocus()
        findViewById<View>(R.id.root).viewTreeObserver.addOnScrollChangedListener { refreshVisibleIcons() }
        loadApps()
    }

    private fun tileBackground(): android.graphics.drawable.StateListDrawable {
        val states = android.graphics.drawable.StateListDrawable()
        fun tile(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (contrast) Color.BLACK else if (focused) Color.rgb(21, 78, 145) else if (textColorValue == Color.WHITE) Color.rgb(32, 42, 59) else Color.WHITE)
            cornerRadius = dp(14).toFloat()
            if (focused || contrast) setStroke(dp(if (focused) 4 else 1), if (focused) Color.YELLOW else Color.WHITE)
        }
        states.addState(intArrayOf(android.R.attr.state_focused), tile(true))
        states.addState(intArrayOf(), tile(false))
        return states
    }

    private fun card(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            minHeight = dp(76)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isFocusable = true
            background = tileBackground()
            backgroundTintList = null
            setTextColor(textColorValue)
            layoutParams = LinearLayout.LayoutParams(dp(168), dp(84)).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }
            setOnFocusChangeListener { _, focused ->
                setTextColor(if (focused) Color.WHITE else textColorValue)
            }
            setOnClickListener { action() }
        }
    }

    private fun chooseAppearance() {
        AlertDialog.Builder(this).setTitle(R.string.appearance)
            .setItems(arrayOf("Sistema", "Claro", "Escuro", if (contrast) "Desativar alto contraste" else "Ativar alto contraste")) { _, value ->
                if (value < 3) {
                    appearance = value
                    preferences.edit().putInt("theme", value).apply()
                } else {
                    contrast = !contrast
                    preferences.edit().putBoolean("contrast", contrast).apply()
                }
                render()
                findViewById<LinearLayout>(R.id.shortcuts).let { it.getChildAt(it.childCount - 1).requestFocus() }
            }.show()
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        val currentGeneration = ++generation
        loader.execute {
            val entries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category -> packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .filter { it.activityInfo.packageName != packageName }
                .map { App(it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
                .distinctBy { it.component.packageName }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isDestroyed || generation != currentGeneration) return@runOnUiThread
                val grid = findViewById<GridLayout>(R.id.apps)
                iconBindings.clear()
                grid.removeAllViews()
                // Keep lightweight metadata for all apps; bind icon pixels only while visible.
                val columns = maxOf(1, minOf(5, (resources.configuration.screenWidthDp - 56) / 176))
                grid.columnCount = columns
                entries.forEachIndexed { index, app ->
                    val tile = layoutInflater.inflate(R.layout.app_card, grid, false) as LinearLayout
                    val name = tile.findViewById<TextView>(R.id.appName)
                    val icon = tile.findViewById<ImageView>(R.id.appIcon)
                    name.text = app.label
                    name.setTextColor(textColorValue)
                    // Reuse exactly the same focus colors/borders as the remote-control shortcuts.
                    tile.background = tileBackground()
                    tile.contentDescription = app.label
                    tile.setOnFocusChangeListener { _, focused -> name.setTextColor(if (focused) Color.WHITE else textColorValue) }
                    tile.setOnClickListener {
                        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), app.label)
                    }
                    tile.layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
                        width = 0; height = dp(148); setMargins(dp(4), dp(6), dp(4), dp(6))
                    }
                    iconBindings.add(IconBinding(icon, app.component))
                    grid.addView(tile)
                }
                grid.post { refreshVisibleIcons() }
                if (entries.isEmpty()) grid.addView(TextView(this).apply { setText(R.string.empty); setTextColor(textColorValue) })
            }
        }
    }

    private fun refreshVisibleIcons() {
        if (isDestroyed) return
        val currentGeneration = generation
        val bounds = android.graphics.Rect()
        iconBindings.forEach { binding ->
            val image = binding.image
            if (!image.getGlobalVisibleRect(bounds)) {
                image.setImageDrawable(null)
            } else if (image.drawable == null && !binding.loading) {
                binding.loading = true
                loader.execute {
                    // ActivityInfo.loadIcon respects activity-specific icons, application icons,
                    // adaptive icons and Android's default fallback when no icon is declared.
                    val bitmap = try {
                        @Suppress("DEPRECATION")
                        val drawable = packageManager.getActivityInfo(binding.component, 0).loadIcon(packageManager)
                        rasterIcon(drawable ?: packageManager.defaultActivityIcon)
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        rasterIcon(packageManager.defaultActivityIcon)
                    } catch (_: android.content.res.Resources.NotFoundException) {
                        rasterIcon(packageManager.defaultActivityIcon)
                    }
                    runOnUiThread {
                        binding.loading = false
                        if (!isDestroyed && generation == currentGeneration && image.getGlobalVisibleRect(android.graphics.Rect())) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    private fun rasterIcon(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        // At most 144x144 ARGB pixels (~81 KiB) per visible icon; no tint or opaque fill.
        val size = dp(72).coerceIn(48, 144)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = minOf(size.toFloat() / width, size.toFloat() / height)
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        drawable.setBounds((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
        drawable.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun launch(intent: Intent, label: String? = null) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            showLaunchError(label, "Função não encontrada. O aplicativo ou a tela pode não estar disponível neste firmware.")
        }
        catch (_: SecurityException) {
            showLaunchError(label, "Acesso bloqueado pelo firmware para aplicativos comuns. Funcionar via ADB não garante permissão para este launcher. Use as configurações ou o launcher original do projetor.")
        }
    }

    private fun showLaunchError(label: String?, message: String) {
        AlertDialog.Builder(this)
            .setTitle(label ?: "Não foi possível abrir")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onRestart() {
        super.onRestart()
        loadApps()
    }

    override fun onDestroy() {
        generation++
        loader.shutdownNow()
        super.onDestroy()
    }
}
