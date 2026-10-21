package com.example.projectorlauncher

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Recs.java adaptation: installed-provider picks; credentials stay device-local.
 * Free My TV, Copyright (c) 2026 contributors, MIT (res/raw/free_my_tv_license.txt).
 * Downloads use fixed HTTPS hosts, bounded streams, timeouts and a bounded private disk cache. */
class RecommendationRepository(private val context: Context) {
    data class Pick(val title: String, val detail: String, val scene: String, val pkg: String? = null,
                    val image: File? = null, val kind: String = "movie")
    data class Result(val picks: List<Pick>, val status: String)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val directory = File(context.cacheDir, "tmdb-images").apply { mkdirs(); listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() } }
    @Volatile private var generation = 0
    private var signature = ""
    private var cached: Result? = null
    private var cachedAt = 0L
    fun invalidate() { generation++; cached = null; cachedAt = 0 }
    fun load(installed: Map<String, String>, kind: String?, regionValue: String, callback: (Result) -> Unit) {
        val token = ++generation
        val region = RecommendationPolicy.region(regionValue)
        val stamp = "$region/$kind/${installed.toSortedMap()}"
        worker.execute {
            val now = System.currentTimeMillis()
            if (stamp == signature && now - cachedAt < 6 * 3600_000 && cached != null) {
                val result = cached!!; main.post { if (generation == token) callback(result) }; return@execute
            }
            val key = TmdbCredentials(context).read()
            val fallback = Result(localPicks(), if (key == null) "Cenas locais · configure TMDB em Aparência" else "TMDB indisponível · exibindo arte local")
            val result = if (key == null || installed.keys.none { it in RecommendationPolicy.providers }) fallback else try {
                val start = System.currentTimeMillis()
                val endpoint = if (kind == null) "all" else kind
                val items = get("/trending/$endpoint/week?language=pt-BR", key).getJSONArray("results")
                val picks = mutableListOf<Pick>()
                for (i in 0 until minOf(items.length(), 8)) {
                    if (generation != token || System.currentTimeMillis() - start > 20000) break
                    val item = items.getJSONObject(i)
                    val type = item.optString("media_type", kind ?: "movie")
                    if (type !in listOf("movie", "tv")) continue
                    val path = item.optString("backdrop_path")
                    if (!RecommendationPolicy.imagePath(path)) continue
                    val id = item.optInt("id")
                    val providers = get("/$type/$id/watch/providers", key).optJSONObject("results")?.optJSONObject(region)
                    val available = mutableSetOf<Int>()
                    for (mode in listOf("flatrate", "free", "ads")) {
                        val list = providers?.optJSONArray(mode) ?: continue
                        for (j in 0 until list.length()) available.add(list.getJSONObject(j).optInt("provider_id"))
                    }
                    val scene = installed.keys.firstOrNull { RecommendationPolicy.providers[it] in available } ?: continue
                    val file = image(path, picks.mapNotNull { it.image?.name }.toSet())
                    val title = item.optString("title", item.optString("name", "Recomendação"))
                    val overview = item.optString("overview").take(220)
                    picks.add(Pick(title, overview.ifBlank { "Disponível em um dos seus aplicativos · TMDB" }, scene, installed[scene], file, type))
                    if (picks.size == 3) break
                }
                if (picks.isEmpty()) Result(localPicks(), "Sem sugestões para seus apps nesta região · arte local")
                else Result(picks + localPicks().take(3 - picks.size), "Sugestões TMDB · disponibilidade: $region")
            } catch (_: Exception) { fallback }
            if (generation != token) return@execute
            signature = stamp; cached = result; cachedAt = if (result.picks.any { it.image != null }) now else now - 5 * 3600_000
            prune(result.picks.mapNotNull { it.image?.name }.toSet())
            main.post { if (generation == token) callback(result) }
        }
    }
    private fun get(path: String, key: String): JSONObject {
        val bearer = key.startsWith("eyJ")
        val suffix = if (bearer) "" else (if ('?' in path) "&" else "?") + "api_key=" + URLEncoder.encode(key, "UTF-8")
        val connection = URL("https://api.themoviedb.org/3$path$suffix").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
        if (bearer) connection.setRequestProperty("Authorization", "Bearer $key")
        return try {
            if (connection.responseCode != 200) throw IOException("TMDB request unavailable")
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (out.size() + n > 256 * 1024) throw IOException("Response too large"); out.write(buffer, 0, n) }
            }
            JSONObject(out.toString("UTF-8"))
        } finally { connection.disconnect() }
    }
    private fun image(path: String, protected: Set<String>): File {
        require(RecommendationPolicy.imagePath(path))
        val file = File(directory, path.removePrefix("/"))
        if (file.exists() && file.length() in 1..RecommendationPolicy.MAX_IMAGE_BYTES.toLong()) return file
        val connection = URL("https://image.tmdb.org/t/p/w1280$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
        val temporary = File(directory, file.name + ".part")
        try {
            if (connection.responseCode != 200) throw IOException("Image unavailable")
            connection.inputStream.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(16384); var total = 0
                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; if (total > RecommendationPolicy.MAX_IMAGE_BYTES) throw IOException("Image too large"); output.write(buffer, 0, n) }
            } }
            if (!temporary.renameTo(file)) throw IOException("Image cache unavailable")
            prune(protected + file.name)
            return file
        } finally { temporary.delete(); connection.disconnect() }
    }
    private fun prune(protected: Set<String>) {
        val files = directory.listFiles()?.filter { it.extension != "part" }?.sortedBy { it.lastModified() } ?: return
        var bytes = files.sumOf { it.length() }; var count = files.size
        for (file in files) if ((bytes > RecommendationPolicy.DISK_LIMIT_BYTES || count > 6) && file.name !in protected) {
            val length = file.length(); if (file.delete()) { bytes -= length; count-- }
        }
    }
    fun close() { generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
    companion object {
        fun localPicks() = listOf(
            Pick("Seu cinema, do seu jeito", "Explore seus aplicativos e escolha o próximo momento.", "prime"),
            Pick("Depois do pôr do sol", "Paisagem original · arte local", "globo"),
            Pick("Novos horizontes", "Paisagem original · arte local", "max"))
    }
}
