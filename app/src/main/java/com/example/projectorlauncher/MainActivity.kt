package com.example.projectorlauncher

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import java.util.concurrent.Executors

/** One Activity, local package resources, no resident service or network images. */
class MainActivity : Activity() {
    private enum class Screen { HOME, APPS, SETTINGS, PICTURE, INPUTS, APPEARANCE, TMDB }
    private data class App(val label: String, val component: ComponentName) {
        val key: String get() = component.packageName
    }
    private data class IconBinding(val image: ImageView, val component: ComponentName, var loading: Boolean = false)
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private val springMotion = SpringMotion()
    private val recommendations by lazy { RecommendationRepository(this) }
    private var recommendationKind: String? = null
    private var homeDock: LegacyGlass? = null
    private var homePicks = RecommendationRepository.localPicks()
    private var recsStatus = "Arte local · sem conteúdo promocional"
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }
    private var screen = Screen.HOME
    private var manageFavorites = false
    private var apps = emptyList<App>()
    private var appsLoaded = false
    private var discovering = false
    @Volatile private var generation = 0
    private var palette = LauncherPalette.forMode("dark")
    private var themeMode = "dark"
    private var uiSounds = true
    private var reducedMotion = false
    private var dynamicWallpapers = true
    private var wallpaperController: WallpaperController? = null
    private var focusReady = false
    private var navigationSound = AudioManager.FX_FOCUS_NAVIGATION_DOWN
    private var pendingNavigationSound = false
    private var menu: Dialog? = null
    private val iconBindings = mutableListOf<IconBinding>()
    private lateinit var body: LinearLayout
    private var networkButton: Button? = null
    private var callbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkChanged()
        override fun onLost(network: Network) = networkChanged()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = networkChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Migrate v0.2 appearance without dropping an explicit light/contrast choice.
        if (!preferences.contains("theme_v3")) {
            val old = preferences.getInt("theme", 2)
            val light = old == 1 || (old == 0 && resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES)
            preferences.edit().putString("theme_v3", if (preferences.getBoolean("contrast", false))
                "contrast" else if (light) "light" else "dark").apply()
        }
        themeMode = preferences.getString("theme_v3", "dark") ?: "dark"
        uiSounds = preferences.getBoolean("sounds_v3", true)
        reducedMotion = preferences.getBoolean("reduced_motion_v3", false)
        dynamicWallpapers = preferences.getBoolean("dynamic_wallpapers_v4", true)
        screen = savedInstanceState?.getString("screen")?.let { name ->
            Screen.entries.firstOrNull { it.name == name }
        } ?: Screen.HOME
        manageFavorites = savedInstanceState?.getBoolean("manage") ?: false
        render(savedInstanceState?.getString("focus"))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun favoriteKeys() = FavoritePolicy.decode(preferences.getString("favorites_v3", "") ?: "")
    private fun isFavorite(app: App) = app.key in favoriteKeys()
    private fun kystar(action: String) = Intent("com.kystar.settings.$action").setPackage("com.kystar.projector.settings")
    private fun hdmi() = Intent().setComponent(ComponentName("com.hisilicon.tvui", "com.hisilicon.tvui.MainActivity"))

    private fun navigate(destination: Screen) {
        screen = destination
        manageFavorites = false
        render()
    }

    private fun render(restoreFocus: String? = null) {
        wallpaperController?.close(); wallpaperController = null
        springMotion.stop(); homeDock = null
        val currentGeneration = ++generation
        focusReady = false
        pendingNavigationSound = false
        menu?.dismiss()
        iconBindings.clear()
        palette = LauncherPalette.forMode(themeMode)
        setContentView(R.layout.activity_main)
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or (if (themeMode == "light") View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0)
        findViewById<View>(R.id.root).setBackgroundColor(palette.background)
        findViewById<View>(R.id.readabilityOverlay).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, when (themeMode) {
                "contrast" -> intArrayOf(palette.background, palette.background)
                "light" -> intArrayOf(0xd9edf1f7.toInt(), 0xf5edf1f7.toInt())
                else -> intArrayOf(0x3307090f, 0xce07090f.toInt())
            })
        body = findViewById(R.id.body)
        buildNavigation()
        when (screen) {
            Screen.HOME -> buildHome()
            Screen.APPS -> buildApplications()
            Screen.SETTINGS -> buildSettings()
            Screen.PICTURE -> buildPicture()
            Screen.INPUTS -> buildInputs()
            Screen.APPEARANCE -> buildAppearance()
            Screen.TMDB -> buildTmdbSettings()
        }
        findViewById<View>(R.id.contentScroll).viewTreeObserver.addOnScrollChangedListener { refreshVisibleIcons() }
        body.post {
            if (isDestroyed || generation != currentGeneration) return@post
            ensureWallpaper()
            val target = restoreFocus?.let { findViewById<View>(R.id.root).findViewWithTag<View>(it) }
            (target ?: firstFocusable(body) ?: findViewById<View>(R.id.navigation).findViewWithTag("nav:home"))?.requestFocus()
            focusReady = true
            refreshVisibleIcons()
        }
        applyTypography(findViewById(R.id.root))
        if (!reducedMotion) {
            body.alpha = 0.7f
            body.translationY = dp(6).toFloat()
            body.animate().alpha(1f).translationY(0f).setDuration(160).start()
        }
    }

    private fun applyTypography(view: View) {
        if (view is TextView) view.typeface = resources.getFont(R.font.inter)
        if (view is ViewGroup) for (i in 0 until view.childCount) applyTypography(view.getChildAt(i))
    }

    private fun firstFocusable(view: View): View? {
        if (view.visibility != View.VISIBLE) return null
        // Scroll containers are focusable too; prefer an actual tile with a visible focus ring.
        if (view is ViewGroup) for (i in 0 until view.childCount) firstFocusable(view.getChildAt(i))?.let { return it }
        return if (view.isFocusable && view.isEnabled) view else null
    }

    private fun buildNavigation() {
        val bar = findViewById<LinearLayout>(R.id.navigation)
        bar.background = if (screen == Screen.HOME) null else glassPanel(false)
        bar.setPadding(dp(6), 0, dp(6), 0)
        val compact = resources.configuration.screenWidthDp < 760
        if (screen == Screen.HOME) bar.addView(label("Para você", 30f).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        val navigation = if (screen == Screen.HOME) listOf(Triple("Aplicativos", Screen.APPS, "apps")) else
            listOf(Triple("Para você", Screen.HOME, "home"), Triple("Aplicativos", Screen.APPS, "apps"), Triple("Configurações", Screen.SETTINGS, "settings"))
        navigation.forEach { (label, destination, key) ->
            bar.addView(button(label, "nav:$key") { navigate(destination) }.apply {
                textSize = if (compact) 14f else 17f
                isSelected = screen == destination || (destination == Screen.SETTINGS && screen in
                    listOf(Screen.APPEARANCE, Screen.PICTURE, Screen.INPUTS, Screen.TMDB))
                layoutParams = LinearLayout.LayoutParams(dp(if (compact) (if (key == "home") 74 else 98) else 128), dp(48)).apply { marginEnd = dp(6) }
            })
        }
        if (screen != Screen.HOME) bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        networkButton = button("Wi-Fi", "nav:wifi") { launch(Intent(Settings.ACTION_WIFI_SETTINGS), "Rede / Wi-Fi") }.apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(if (compact) 88 else 108), dp(48)).apply { marginEnd = dp(6) }
        }
        bar.addView(networkButton)
        bar.addView(button("Entradas", "nav:inputs") { navigate(Screen.INPUTS) }.apply {
            textSize = if (compact) 14f else 16f
            layoutParams = LinearLayout.LayoutParams(dp(if (compact) 72 else 92), dp(48)).apply { marginEnd = dp(6) }
        })
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_gear)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.text)
            contentDescription = "Abrir configurações do launcher"
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) }
            behavior(this, "nav:gear") { navigate(Screen.SETTINGS) }
        })
        bar.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm a"
            textSize = if (compact) 15f else 19f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(if (compact) 58 else 76), dp(48))
        })
        updateNetwork()
    }

    private fun label(value: String, size: Float = 18f, secondary: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(if (secondary) palette.secondary else palette.text)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    private fun heading(title: String, description: String) {
        body.addView(label(title, 30f))
        body.addView(label(description, 17f, true))
    }

    private fun buildHome() {
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val tabs = LinearLayout(this)
        listOf("Explorar" to null, "Filmes" to "movie", "Séries" to "tv").forEach { (title, kind) ->
            tabs.addView(button(title, "home:tab:$title") {
                recommendationKind = kind; render("home:tab:$title")
            }.apply {
                isSelected = recommendationKind == kind
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(34)).apply { marginEnd = dp(8) }
            })
        }
        heading.addView(tabs)
        val picks = LinearLayout(this).apply { clipChildren = false; clipToPadding = false }
        val dock = FrameLayout(this).apply { setPadding(dp(16), dp(6), dp(16), dp(6)); clipChildren = false }
        homeDock = LegacyGlass(dock, themeMode == "light", themeMode == "contrast")
        dock.background = homeDock
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; clipChildren = false }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
        val selected = favoriteKeys().mapNotNull { key -> apps.firstOrNull { it.key == key } }
        val tileWidth = ((resources.configuration.screenWidthDp - 110 - selected.size * 12) / selected.size.coerceAtLeast(1)).coerceIn(76, 146)
        selected.forEach { app -> row.addView(appTile(app).apply {
            layoutParams = LinearLayout.LayoutParams(dp(tileWidth), -1).apply { setMargins(dp(6), 0, dp(6), 0) }
        }) }
        if (selected.isEmpty()) row.addView(button(if (appsLoaded) "Escolher favoritos" else "Carregando…", "home:choose") {
            screen = Screen.APPS; manageFavorites = true; render()
        })
        scroll.addView(row, FrameLayout.LayoutParams(-2, -1))
        dock.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val stage = HomeStage(this).apply { setLayers(heading, picks, dock) }
        body.addView(stage, LinearLayout.LayoutParams(-1, dp((resources.configuration.screenHeightDp - 110).coerceAtLeast(270))))
        stage.post {
            if (stage.isAttachedToWindow) {
                val height = findViewById<View>(R.id.contentScroll).height - body.paddingTop - body.paddingBottom
                if (height > 0 && stage.layoutParams.height != height) { stage.layoutParams.height = height; stage.requestLayout() }
            }
        }
        populateRecommendations(picks)
        val token = generation
        val installed = apps.map { CinematicPolicy.scene(it.key) to it.key }.filter { it.first != "neutral" }.toMap()
        recommendations.load(installed, recommendationKind, preferences.getString("tmdb_region", "BR") ?: "BR") { result ->
            if (!isDestroyed && generation == token && screen == Screen.HOME) {
                homePicks = result.picks; recsStatus = result.status
                val focus = currentFocus?.tag as? String
                populateRecommendations(picks)
                if (focus?.startsWith("rec:") == true) picks.findViewWithTag<View>(focus)?.requestFocus()
            }
        }
        scroll.viewTreeObserver.addOnScrollChangedListener { refreshVisibleIcons() }
    }

    private fun populateRecommendations(row: LinearLayout) {
        row.removeAllViews()
        val token = generation
        homePicks.take(3).forEachIndexed { index, pick ->
            val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
            val photo = FrameLayout(this).apply {
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0,0,view.width,view.height,dp(14).toFloat())
                    }
                }
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            photo.addView(image, FrameLayout.LayoutParams(-1,-1))
            val scrim = View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                if (themeMode == "contrast") intArrayOf(0xff000000.toInt(),0xff000000.toInt()) else intArrayOf(0x00000000,0x14000000)) }
            photo.addView(scrim, FrameLayout.LayoutParams(-1,-1))
            tile.addView(photo, LinearLayout.LayoutParams(-1,0,1f))
            val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(7), 0, 0) }
            text.addView(label(pick.title, if (index == 0) 17f else 15f).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(-1, dp(24))
            })
            text.addView(label(if (pick.image == null) "Arte local · cena original" else "${if (pick.kind == "movie") "Filme" else "Série"} · TMDB", 12f, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(-1, dp(20))
            })
            tile.addView(text, LinearLayout.LayoutParams(-1,dp(54)))
            tile.contentDescription = pick.title + ". " + if (pick.image == null) "Arte local" else "Recomendação TMDB"
            behavior(tile, "rec:$index") {
                val app = apps.firstOrNull { it.key == pick.pkg }
                if (app != null) openApp(app) else navigate(Screen.APPS)
            }
            tile.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(0x00000000); setStroke(dp(2), palette.accent)
                })
                addState(intArrayOf(), ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            row.addView(tile, LinearLayout.LayoutParams(0,-1,if (index == 0) 3.3f else 1f).apply { if (index < 2) marginEnd = dp(14) })
            worker.execute {
                val bitmap = if (themeMode == "contrast") null else { pick.image?.let { BackdropPipeline.photo(it, 560, 320) }
                    ?: CinematicArt.render(pick.scene, 560, 320) }
                runOnUiThread {
                    if (!isDestroyed && token == generation && image.isAttachedToWindow) image.setImageBitmap(bitmap)
                    else bitmap?.recycle()
                }
            }
        }
        applyTypography(row)
    }

    private fun buildApplications() {
        heading("Aplicativos", if (manageFavorites) "Selecione para adicionar ou remover favoritos. Até ${FavoritePolicy.LIMIT} na tela inicial."
            else "Todos os seus aplicativos. Mantenha OK pressionado para opções.")
        body.addView(button(if (manageFavorites) "✓ Concluir favoritos" else "☆ Gerenciar favoritos", "apps:manage") {
            manageFavorites = !manageFavorites; render("apps:manage")
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(260), dp(54)).apply { bottomMargin = dp(16) } })
        if (apps.isEmpty()) body.addView(label(if (appsLoaded) "Nenhum aplicativo disponível." else "Carregando aplicativos…", secondary = true))
        val columns = ((resources.configuration.screenWidthDp - 64) / 184).coerceIn(2, 6)
        val grid = grid(columns)
        apps.forEachIndexed { index, app -> grid.addView(appTile(app), gridParams(index, columns, 188)) }
        body.addView(grid)
    }

    private fun appTile(app: App): View {
        val tile = layoutInflater.inflate(if (screen == Screen.HOME) R.layout.dock_app else R.layout.app_card, body, false) as LinearLayout
        tile.findViewById<TextView>(R.id.appName).apply { text = app.label; setTextColor(palette.text) }
        tile.findViewById<TextView>(R.id.appBadge).apply {
            visibility = if (screen == Screen.HOME) View.GONE else View.VISIBLE
            text = if (isFavorite(app)) "★ Favorito" else if (manageFavorites) "☆ Adicionar" else ""
            setTextColor(palette.secondary)
        }
        tile.contentDescription = app.label + if (isFavorite(app)) ", favorito" else ""
        behavior(tile, "app:${app.key}") { if (screen == Screen.APPS && manageFavorites) toggleFavorite(app) else openApp(app) }
        if (screen == Screen.HOME) tile.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.LayerDrawable(arrayOf(DockShadow(resources.displayMetrics.density), glassPanel(true))))
            addState(intArrayOf(), DockShadow(resources.displayMetrics.density))
        }
        tile.setOnLongClickListener {
            confirmSound()
            showMenu(app.label, null, listOf("Abrir aplicativo" to { openApp(app) },
                (if (isFavorite(app)) "Remover dos favoritos" else "Adicionar aos favoritos") to { toggleFavorite(app) }))
            true
        }
        iconBindings.add(IconBinding(tile.findViewById(R.id.appIcon), app.component))
        return tile
    }
    private fun openApp(app: App) = launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), app.label)

    private fun toggleFavorite(app: App) {
        val updated = FavoritePolicy.toggle(favoriteKeys(), app.key)
        if (updated == null) {
            showMenu("Favoritos completos", "Escolha até ${FavoritePolicy.LIMIT} aplicativos. Remova um favorito para adicionar outro.", emptyList())
        } else {
            preferences.edit().putString("favorites_v3", updated.joinToString("|")).apply()
            render("app:${app.key}")
        }
    }

    private fun grid(columns: Int) = GridLayout(this).apply {
        columnCount = columns; clipChildren = false; clipToPadding = false
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun gridParams(index: Int, columns: Int, height: Int) = GridLayout.LayoutParams(
        GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
        width = 0; this.height = dp(height); setMargins(dp(6), dp(8), dp(6), dp(8))
    }

    private fun buildSettings() {
        heading("Configurações", "Tudo no lugar certo. Escolha o que deseja ajustar.")
        data class Category(val title: String, val hint: String, val icon: Int, val key: String, val action: () -> Unit)
        val categories = listOf(
            Category("Imagem e projeção", "Correção, zoom e orientação", R.drawable.ic_picture, "picture") { navigate(Screen.PICTURE) },
            Category("Som", "Áudio do dispositivo", R.drawable.ic_sound, "sound") { launch(Intent(Settings.ACTION_SOUND_SETTINGS), "Som") },
            Category("Rede / Wi-Fi", "Conexões sem fio", R.drawable.ic_network, "network") { launch(Intent(Settings.ACTION_WIFI_SETTINGS), "Rede / Wi-Fi") },
            Category("Bluetooth", "Controles e acessórios", R.drawable.ic_bluetooth, "bluetooth") { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth") },
            Category("Entradas / HDMI", "Fontes e Fire Stick", R.drawable.ic_inputs, "inputs") { navigate(Screen.INPUTS) },
            Category("Aplicativos", "Gerenciar aplicativos instalados", R.drawable.ic_apps, "applications") { launch(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), "Aplicativos") },
            Category("Aparência", "Tema, sons e movimento", R.drawable.ic_appearance, "appearance") { navigate(Screen.APPEARANCE) },
            Category("Configurações do sistema", "Opções completas do Android", R.drawable.ic_system, "system") { launch(Intent(Settings.ACTION_SETTINGS), "Sistema") }
        )
        val columns = if (resources.configuration.screenWidthDp < 720) 2 else 3
        val grid = grid(columns)
        categories.forEachIndexed { index, category ->
            val tile = layoutInflater.inflate(R.layout.settings_card, grid, false)
            tile.findViewById<ImageView>(R.id.categoryIcon).apply {
                setImageResource(category.icon); imageTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            }
            tile.findViewById<TextView>(R.id.categoryTitle).apply { text = category.title; setTextColor(palette.text) }
            tile.findViewById<TextView>(R.id.categoryHint).apply { text = category.hint; setTextColor(palette.secondary) }
            behavior(tile, "setting:${category.key}", category.action)
            grid.addView(tile, gridParams(index, columns, 160))
        }
        body.addView(grid)
    }

    private fun buildPicture() {
        heading("Imagem e projeção", "Ajustes nativos do seu projetor, somente quando você escolher.")
        actionRow("Correção trapezoidal", "picture:correct") { launch(kystar("SCREEN_CORRECT"), "Correção trapezoidal") }
        actionRow("Zoom", "picture:zoom") { launch(kystar("SCREEN_ZOOM"), "Zoom") }
        actionRow("Orientação da projeção", "picture:orientation") { launch(kystar("PROJECTION_INSTALL_MODE"), "Orientação da projeção") }
        actionRow("Configurações de tela", "picture:display") { launch(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Tela") }
    }
    private fun buildInputs() {
        heading("Entradas / HDMI", "Escolha a fonte para continuar assistindo.")
        actionRow("Fire Stick / HDMI", "inputs:hdmi") { launch(hdmi(), "Fire Stick / HDMI") }
        actionRow("Seleção de fontes", "inputs:sources") { launch(kystar("SOURCE_LIST"), "Seleção de fontes") }
    }
    private fun buildAppearance() {
        heading("Aparência", "Seu espaço, do seu jeito. As opções ficam salvas neste launcher.")
        body.addView(label("Tema", 22f))
        val row = LinearLayout(this)
        listOf("Claro" to "light", "Escuro" to "dark", "Alto contraste" to "contrast").forEach { (name, mode) ->
            row.addView(button((if (themeMode == mode) "✓ " else "") + name, "theme:$mode") {
                themeMode = mode; preferences.edit().putString("theme_v3", mode).apply(); render("theme:$mode")
            }.apply {
                isSelected = themeMode == mode
                layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(4), 0, dp(4), dp(16)) }
            })
        }
        body.addView(row)
        actionRow("Sons da interface: ${if (uiSounds) "ativados" else "desativados"}", "appearance:sounds") {
            uiSounds = !uiSounds; preferences.edit().putBoolean("sounds_v3", uiSounds).apply(); render("appearance:sounds")
        }
        actionRow("TMDB e recomendações", "appearance:tmdb") { navigate(Screen.TMDB) }
        actionRow("Papéis de parede dinâmicos: ${if (dynamicWallpapers) "ativados" else "desativados"}", "appearance:wallpapers") {
            dynamicWallpapers = !dynamicWallpapers
            preferences.edit().putBoolean("dynamic_wallpapers_v4", dynamicWallpapers).apply()
            render("appearance:wallpapers")
        }
        actionRow("Reduzir animações: ${if (reducedMotion) "ativado" else "desativado"}", "appearance:motion") {
            reducedMotion = !reducedMotion; preferences.edit().putBoolean("reduced_motion_v3", reducedMotion).apply(); render("appearance:motion")
        }
        body.addView(label("Sons curtos do Android, sem arquivos de áudio. Movimento reduzido desativa escala, transições de telas e animações de menus.", 16f, true))
    }

    private fun actionRow(title: String, key: String, action: () -> Unit) {
        body.addView(button(title, key, action).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(12) }
        })
    }

    private fun buildTmdbSettings() {
        heading("TMDB e recomendações", "Sugestões dos serviços instalados. Sem chave, usamos somente arte local.")
        actionRow("Configurar chave/token neste aparelho", "tmdb:key") { editTmdbCredential() }
        actionRow("Região: ${preferences.getString("tmdb_region", "BR")}", "tmdb:region") {
            showMenu("Região do catálogo", null, listOf("Brasil" to { setRegion("BR") }, "Portugal" to { setRegion("PT") }, "Estados Unidos" to { setRegion("US") }))
        }
        actionRow("Atualizar recomendações", "tmdb:refresh") {
            recommendations.invalidate(); navigate(Screen.HOME)
        }
        actionRow("Remover credencial e usar arte local", "tmdb:clear") {
            TmdbCredentials(this).clear(); recommendations.invalidate(); homePicks = RecommendationRepository.localPicks(); recsStatus = "Arte local · sem credencial TMDB"; render("tmdb:clear")
        }
        body.addView(label(recsStatus, 16f, true))
        body.addView(label("This product uses the TMDB API but is not endorsed or certified by TMDB. Imagens e disponibilidade seguem os termos do TMDB. A credencial fica cifrada com Android Keystore somente neste aparelho.", 15f, true))
        actionRow("Licenças Free My TV / Inter", "tmdb:licenses") {
            fun notice(resource: Int, title: String) {
                val text = resources.openRawResource(resource).bufferedReader().use { it.readText() }
                showMenu(title, text, emptyList())
            }
            showMenu("Licenças", null, listOf("Free My TV · MIT" to { notice(R.raw.free_my_tv_license, "Free My TV · MIT") },
                "Inter · SIL OFL" to { notice(R.raw.inter_font_license, "Inter · SIL OFL") }))
        }
    }

    private fun setRegion(value: String) {
        preferences.edit().putString("tmdb_region", RecommendationPolicy.region(value)).apply()
        recommendations.invalidate(); render("tmdb:region")
    }
    private fun editTmdbCredential() {
        val input = EditText(this).apply {
            hint = "API Key v3 ou Read Access Token"; setTextColor(palette.text); setHintTextColor(palette.secondary)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(24),dp(24),dp(24)); setBackgroundColor(palette.surface) }
        panel.addView(label("Credencial TMDB", 24f)); panel.addView(input,LinearLayout.LayoutParams(-1,dp(60)))
        panel.addView(button("Salvar", "tmdb:save") {
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) {
                try { TmdbCredentials(this).save(value); recommendations.invalidate(); dialog.dismiss(); render("tmdb:key") }
                catch (_: Exception) { dialog.dismiss(); showMenu("TMDB", "Não foi possível proteger a credencial neste firmware. Nenhuma chave foi salva em texto puro.", emptyList()) }
            }
        })
        panel.addView(button("Cancelar", "tmdb:cancel") { dialog.dismiss() })
        dialog.setOnDismissListener { input.text.clear(); if (menu === dialog) menu = null }
        dialog.setContentView(panel); dialog.show(); dialog.window?.setBackgroundDrawable(ColorDrawable(palette.surface))
        dialog.window?.setLayout(minOf(dp(580), resources.displayMetrics.widthPixels-dp(48)),-2)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        applyTypography(panel); input.requestFocus(); menu = dialog
    }

    private fun glassPanel(focused: Boolean, selected: Boolean = false): android.graphics.drawable.Drawable {
        val contrast = themeMode == "contrast"
        val fill = GlassColors.fill(palette, focused, contrast)
        val panel = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(fill, fill, if (contrast) fill else (fill and 0x00ffffff) or (0xf8 shl 24))).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(if (focused) 2 else 1), if (focused || selected) palette.accent
                else if (contrast) palette.outline else (palette.outline and 0x00ffffff) or 0x80000000.toInt())
        }
        if (contrast) return panel
        val highlight = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x17ffffff, 0x00ffffff, 0x00ffffff)).apply { cornerRadius = dp(18).toFloat() }
        val layers = mutableListOf<android.graphics.drawable.Drawable>()
        if (focused) layers.add(GradientDrawable().apply {
            cornerRadius = dp(20).toFloat(); setColor(0x00000000)
            setStroke(dp(5), (palette.accent and 0x00ffffff) or 0x40000000)
        })
        layers.add(panel); layers.add(highlight)
        return android.graphics.drawable.LayerDrawable(layers.toTypedArray()).apply {
            if (focused) { setLayerInset(1, dp(2), dp(2), dp(2), dp(2)); setLayerInset(2, dp(2), dp(2), dp(2), dp(2)) }
        }
    }
    private fun tileBackground() = StateListDrawable().apply {
        setEnterFadeDuration(if (reducedMotion) 0 else 100)
        setExitFadeDuration(if (reducedMotion) 0 else 100)
        addState(intArrayOf(android.R.attr.state_focused), glassPanel(true))
        addState(intArrayOf(android.R.attr.state_pressed), glassPanel(true))
        addState(intArrayOf(android.R.attr.state_selected), glassPanel(false, true))
        addState(intArrayOf(), glassPanel(false))
    }
    private fun button(title: String, key: String, action: () -> Unit) = Button(this).apply {
        text = title; textSize = 18f; isAllCaps = false
        setTextColor(palette.text); maxLines = 2; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LinearLayout.LayoutParams(dp(220), dp(60))
        behavior(this, key, action)
        backgroundTintList = null
    }
    private fun behavior(view: View, key: String, action: () -> Unit) {
        view.tag = key; view.isFocusable = true; view.isClickable = true; view.isSoundEffectsEnabled = false
        view.background = tileBackground()
        view.setOnFocusChangeListener { v, focused ->
            val target = if (!focused) 1f else if (key.startsWith("app:") && screen == Screen.HOME) 1.16f else if (key.startsWith("app:") || key.startsWith("rec:")) 1.05f else 1.025f
            springMotion.focus(v, target, reducedMotion)
            if (focused) requestWallpaper(key)
            if (focused && focusReady && pendingNavigationSound) {
                if (uiSounds) audio.playSoundEffect(navigationSound)
                pendingNavigationSound = false
            }
        }
        view.setOnClickListener {
            confirmSound()
            if (!reducedMotion) { it.alpha = 0.72f; it.animate().alpha(1f).setDuration(100).start() }
            action()
        }
    }
    private fun confirmSound() { if (uiSounds) audio.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN) }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            navigationSound = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
                else -> navigationSound
            }
            pendingNavigationSound = event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        return super.dispatchKeyEvent(event)
    }

    /** Custom palette-aware menus; no Android side-panel for launcher settings. */
    private fun showMenu(title: String, message: String?, choices: List<Pair<String, () -> Unit>>) {
        menu?.dismiss()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)); background = glassPanel(false)
        }
        panel.addView(label(title, 24f))
        if (message != null) {
            if (message.length > 400) panel.addView(ScrollView(this).apply {
                addView(label(message, 16f, true)); isFocusable = true
            }, LinearLayout.LayoutParams(-1, minOf(dp(230), resources.displayMetrics.heightPixels / 3)))
            else panel.addView(label(message, 17f, true))
        }
        (choices + ("Fechar" to {})).forEachIndexed { index, (name, action) ->
            panel.addView(button(name, "dialog:$index") { dialog.dismiss(); action() }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) }
            })
        }
        applyTypography(panel)
        dialog.setContentView(panel)
        dialog.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                navigationSound = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) AudioManager.FX_FOCUS_NAVIGATION_UP else AudioManager.FX_FOCUS_NAVIGATION_DOWN
                pendingNavigationSound = true
            }
            false
        }
        dialog.window?.setWindowAnimations(if (reducedMotion) 0 else R.style.MenuMotion)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(minOf(dp(560), resources.displayMetrics.widthPixels - dp(48)), -2)
        firstFocusable(panel)?.requestFocus()
        menu = dialog
    }

    private fun launch(intent: Intent, title: String) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            showMenu(title, "Função indisponível neste firmware ou aplicativo não instalado.", emptyList())
        } catch (_: SecurityException) {
            showMenu(title, "O firmware bloqueou o acesso por aplicativos comuns. Funcionar via ADB não garante permissão. Use as configurações ou o launcher original do projetor.", emptyList())
        }
    }

    @Suppress("DEPRECATION")
    private fun discoverApps() {
        if (discovering) return
        discovering = true
        worker.execute {
            val entries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
                .filter { it.activityInfo.packageName != packageName }
                .map { App(it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
                .distinctBy { it.key }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                discovering = false
                if (isDestroyed) return@runOnUiThread
                if (!preferences.contains("favorites_v3") && entries.isNotEmpty()) {
                    val priority = listOf("youtube", "globoplay", "prime", "netflix", "disney", "play store")
                    val initial = entries.sortedBy { app -> priority.indexOfFirst { app.label.lowercase().contains(it) }.let { if (it < 0) 99 else it } }.take(5)
                    preferences.edit().putString("favorites_v3", initial.joinToString("|") { it.key }).apply()
                }
                val changed = !appsLoaded || entries != apps
                appsLoaded = true; apps = entries
                if (changed && screen in listOf(Screen.HOME, Screen.APPS)) render(currentFocus?.tag as? String)
            }
        }
    }

    private fun refreshVisibleIcons() {
        if (isDestroyed) return
        val currentGeneration = generation
        val bounds = Rect()
        iconBindings.forEach { binding ->
            val image = binding.image
            if (!image.getGlobalVisibleRect(bounds)) image.setImageDrawable(null)
            else if (image.drawable == null && !binding.loading) {
                binding.loading = true
                worker.execute {
                    if (generation != currentGeneration) return@execute
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
                        if (!isDestroyed && generation == currentGeneration && image.getGlobalVisibleRect(Rect())) image.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
    private fun rasterIcon(drawable: android.graphics.drawable.Drawable): Bitmap {
        val size = dp(68).coerceIn(48, 144)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = minOf(size.toFloat() / width, size.toFloat() / height)
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        drawable.setBounds((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun ensureWallpaper() {
        if (wallpaperController != null || themeMode == "contrast") return
        wallpaperController = WallpaperController(findViewById(R.id.wallpaperFirst), findViewById(R.id.wallpaperSecond), reducedMotion) { frost -> homeDock?.setTexture(frost) }
        wallpaperController?.request("neutral", immediate = true)
    }
    private fun requestWallpaper(key: String?) {
        val pick = if (key?.startsWith("rec:") == true) homePicks.getOrNull(key.removePrefix("rec:").toIntOrNull() ?: -1) else null
        val scene = when {
            !dynamicWallpapers || screen != Screen.HOME -> "neutral"
            pick != null -> pick.image?.let { "photo:${it.path}" } ?: pick.scene
            key?.startsWith("app:") == true -> CinematicPolicy.scene(key.removePrefix("app:"))
            else -> "neutral"
        }
        wallpaperController?.request(scene)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) requestWallpaper(currentFocus?.tag as? String)
    }

    private fun networkChanged() = runOnUiThread { if (!isDestroyed) updateNetwork() }
    private fun updateNetwork() {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val status = when {
            capabilities == null -> "Sem rede"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi • conectado"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Rede • cabo"
            else -> "Rede • conectada"
        }
        networkButton?.text = status
        networkButton?.contentDescription = "$status. Abrir configurações de Wi-Fi."
    }
    override fun onStart() {
        super.onStart()
        if (!callbackRegistered) { connectivity.registerDefaultNetworkCallback(networkCallback); callbackRegistered = true }
    }
    override fun onResume() {
        super.onResume(); updateNetwork(); discoverApps()
        findViewById<View>(R.id.root).post { if (!isDestroyed) { ensureWallpaper(); requestWallpaper(currentFocus?.tag as? String) } }
    }
    override fun onStop() {
        springMotion.stop()
        wallpaperController?.close(); wallpaperController = null
        if (callbackRegistered) { connectivity.unregisterNetworkCallback(networkCallback); callbackRegistered = false }
        super.onStop()
    }
    @Deprecated("Legacy back handling for API 28")
    override fun onBackPressed() {
        when (screen) {
            Screen.TMDB -> navigate(Screen.APPEARANCE)
            Screen.PICTURE, Screen.INPUTS, Screen.APPEARANCE -> navigate(Screen.SETTINGS)
            Screen.APPS -> if (manageFavorites) { manageFavorites = false; render("apps:manage") } else navigate(Screen.HOME)
            Screen.SETTINGS -> navigate(Screen.HOME)
            Screen.HOME -> super.onBackPressed()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name); outState.putBoolean("manage", manageFavorites)
        outState.putString("focus", currentFocus?.tag as? String)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        generation++; menu?.dismiss(); wallpaperController?.close(); springMotion.stop(); recommendations.close(); worker.shutdownNow()
        iconBindings.clear()
        super.onDestroy()
    }
}
