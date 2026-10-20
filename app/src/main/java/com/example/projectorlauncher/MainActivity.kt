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

/** No persistent services, network access, wallpaper bitmaps or resident icon cache. */
class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val loader = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var appearance = 0
    private var contrast = false
    private var textColorValue = Color.WHITE
    private var background = Color.BLACK
    private data class App(val label: String, val component: ComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appearance = preferences.getInt("theme", 0)
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
        val row = findViewById<LinearLayout>(R.id.shortcuts)
        listOf(
            "Fire Stick / HDMI" to Intent().setComponent(ComponentName("com.hisilicon.tvui", "com.hisilicon.tvui.MainActivity")),
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
        row.getChildAt(0).requestFocus()
        loadApps()
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
            val states = android.graphics.drawable.StateListDrawable()
            fun tile(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (contrast) Color.BLACK else if (focused) Color.rgb(21, 78, 145) else if (textColorValue == Color.WHITE) Color.rgb(32, 42, 59) else Color.WHITE)
                cornerRadius = dp(14).toFloat()
                if (focused || contrast) setStroke(dp(if (focused) 4 else 1), if (focused) Color.YELLOW else Color.WHITE)
            }
            states.addState(intArrayOf(android.R.attr.state_focused), tile(true))
            states.addState(intArrayOf(), tile(false))
            background = states
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
                grid.removeAllViews()
                // Only visible card text is retained. No full-resolution package icons are decoded.
                val columns = maxOf(1, minOf(5, (resources.configuration.screenWidthDp - 56) / 176))
                grid.columnCount = columns
                entries.forEachIndexed { index, app ->
                    grid.addView(card(app.label) {
                        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                    }.apply {
                        layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
                            width = 0; height = dp(90); setMargins(dp(4), dp(6), dp(4), dp(6))
                        }
                    })
                }
                if (entries.isEmpty()) grid.addView(TextView(this).apply { setText(R.string.empty); setTextColor(textColorValue) })
            }
        }
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
