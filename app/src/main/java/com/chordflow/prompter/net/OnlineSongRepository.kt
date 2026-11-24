package com.chordflow.prompter.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The ONLY part of the app that touches the network, and only when the user opens
 * "Download Free Songs". Nothing here is called from the teleprompter.
 *
 * Two ways in:
 *   1. Browse an open-source ChordPro catalog hosted on GitHub (tree API + raw files).
 *   2. Paste any raw ChordPro / .cho / .onsong / .txt URL and pull it straight down.
 */
class OnlineSongRepository(private val context: Context) {

    /** A GitHub repository holding ChordPro or OnSong files. */
    data class Catalog(
        val label: String,
        val owner: String,
        val repo: String,
        val branch: String,
        val note: String = ""
    )

    /** One downloadable song file found in a catalog. */
    data class RemoteSong(
        val title: String,
        val path: String,
        val rawUrl: String,
        val catalog: String
    )

    sealed interface LoadResult {
        data class Success(val songs: List<RemoteSong>) : LoadResult
        data class Offline(val message: String) : LoadResult
        data class Error(val message: String) : LoadResult
    }

    companion object {
        /**
         * Starting points. These are public GitHub repositories that host open or
         * public-domain chord charts. Repository layouts and branch names do change,
         * so if a catalog returns nothing, use "Paste a raw URL" instead — that path
         * works against any reachable file.
         */
        val CATALOGS = listOf(
            Catalog(
                label = "Worship Commons (ChurchApps)",
                owner = "ChurchApps",
                repo = "WorshipCommonsContent",
                branch = "main",
                note = "Public-domain worship catalog maintained by ChurchApps"
            ),
            Catalog(
                label = "OpenLyrics / ChordPro samples",
                owner = "ChordPro",
                repo = "chordpro",
                branch = "master",
                note = "ChordPro reference project — sample charts live under t/ and examples"
            )
        )

        private val SONG_EXTENSIONS = listOf(".cho", ".chopro", ".chordpro", ".crd", ".onsong", ".pro", ".txt")
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TreeResponse(
        @SerialName("tree") val tree: List<TreeEntry> = emptyList(),
        @SerialName("truncated") val truncated: Boolean = false
    )

    @Serializable
    private data class TreeEntry(
        @SerialName("path") val path: String = "",
        @SerialName("type") val type: String = ""
    )

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /** List every chart file in a catalog repository. */
    suspend fun loadCatalog(catalog: Catalog): LoadResult = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext LoadResult.Offline(
                "No internet connection. Your offline library still works — this screen is only for downloading new songs."
            )
        }
        val url = "https://api.github.com/repos/${catalog.owner}/${catalog.repo}" +
            "/git/trees/${catalog.branch}?recursive=1"
        try {
            val body = httpGet(url, accept = "application/vnd.github+json")
                ?: return@withContext LoadResult.Error(
                    "Could not reach ${catalog.label}. The repository or branch name may have changed — try pasting a raw URL instead."
                )

            val parsed = json.decodeFromString(TreeResponse.serializer(), body)
            val songs = parsed.tree
                .filter { it.type == "blob" }
                .filter { entry -> SONG_EXTENSIONS.any { entry.path.lowercase().endsWith(it) } }
                .map { entry ->
                    RemoteSong(
                        title = prettyTitle(entry.path),
                        path = entry.path,
                        rawUrl = "https://raw.githubusercontent.com/${catalog.owner}/${catalog.repo}/${catalog.branch}/${entry.path}",
                        catalog = catalog.label
                    )
                }
                .sortedBy { it.title.lowercase() }

            if (songs.isEmpty()) {
                LoadResult.Error(
                    "${catalog.label} responded, but holds no chord-sheet files on branch '${catalog.branch}'. Paste a raw URL instead."
                )
            } else {
                LoadResult.Success(songs)
            }
        } catch (e: Exception) {
            LoadResult.Error("Could not load ${catalog.label}: ${e.message ?: "unknown error"}")
        }
    }

    /** Fetch the actual chart text for preview or saving. */
    suspend fun fetchSongText(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(IllegalStateException("No internet connection"))
        }
        try {
            val body = httpGet(rawUrl, accept = "text/plain")
                ?: return@withContext Result.failure(IllegalStateException("Download failed (HTTP error)"))
            if (body.isBlank()) {
                Result.failure(IllegalStateException("That file is empty"))
            } else {
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accepts a raw URL, or a normal github.com/blob/ URL which it rewrites to raw form.
     */
    fun normalizeUrl(input: String): String {
        val url = input.trim()
        if (url.startsWith("https://github.com/") && url.contains("/blob/")) {
            return url.replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("/blob/", "/")
        }
        return url
    }

    private fun httpGet(url: String, accept: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", "ChordFlowPrompter/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun prettyTitle(path: String): String {
        val file = path.substringAfterLast('/')
        val stem = file.substringBeforeLast('.')
        return stem.replace('_', ' ').replace('-', ' ').trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { c -> c.uppercaseChar() }
            }
    }
}
