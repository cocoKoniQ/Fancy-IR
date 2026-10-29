package io.github.cocokoniq.fancyir.core.irdb.remote

import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.FlipperIrParser
import io.github.cocokoniq.fancyir.core.irdb.catalog.BrandKey
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.FlipperCatalog
import io.github.cocokoniq.fancyir.core.irdb.smartir.SmartIr
import io.github.cocokoniq.fancyir.core.irdb.smartir.SmartIrPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** The one network seam of Layer 3; tests substitute canned responses. */
fun interface HttpFetcher {
    /** Returns the body of a 2xx response; throws [RemoteIrdbException] otherwise. */
    suspend fun get(url: String): String
}

class RemoteIrdbException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** GitHub's anonymous API allows 60 requests per hour per IP. */
    val isRateLimited: Boolean get() = status == 403 || status == 429
}

/** Plain `HttpURLConnection` transport: works on Android and the JVM tooling, no extra dependency. */
class UrlConnectionFetcher(
    private val userAgent: String = "Fancy-IR",
    private val timeoutMs: Int = 10_000,
) : HttpFetcher {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/vnd.github+json, text/plain, */*")
            val status = connection.responseCode
            if (status !in 200..299) throw RemoteIrdbException("HTTP $status for $url", status)
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: RemoteIrdbException) {
            throw e
        } catch (e: java.io.IOException) {
            throw RemoteIrdbException("network error for $url: ${e.message}", cause = e)
        } finally {
            connection.disconnect()
        }
    }
}

/** A brand folder in a remote database. */
data class RemoteBrand(val name: String, val path: String) {
    val key: String get() = BrandKey.of(name)
}

/**
 * Layer 3 fetcher for Flipper-IRDB on GitHub (ARCHITECTURE.md §3.8): lists a category's brand
 * folders through the contents API, downloads `.ir` files from raw.githubusercontent.com (which
 * doesn't count against the API rate limit) and ranks them into code-set families.
 */
class FlipperIrdbRemote(
    private val http: HttpFetcher,
    val repo: String = "Lucaslhm/Flipper-IRDB",
    val ref: String = "main",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun brands(category: DeviceCategory): List<RemoteBrand> =
        category.irdbFolders.flatMap { folder ->
            listDirectory(folder).filter { it.type == "dir" }.map { RemoteBrand(it.name.replace('_', ' '), it.path) }
        }.sortedBy { it.name.lowercase() }

    /** Up to [maxFamilies] ranked code sets for [brand], downloading at most [maxFiles] remotes. */
    suspend fun codesets(category: DeviceCategory, brand: String, maxFamilies: Int = 5, maxFiles: Int = 16): List<CatalogCodeset> {
        val key = BrandKey.of(brand)
        val match = brands(category).let { all -> all.firstOrNull { it.key == key } ?: all.firstOrNull { it.key.startsWith(key) } }
            ?: return emptyList()
        val files = listDirectory(match.path).filter { it.type == "file" && it.name.endsWith(".ir", ignoreCase = true) }.take(maxFiles)
        val parsed = coroutineScope {
            files.map { entry -> async { entry.path to FlipperIrParser.parse(http.get(rawUrl(entry.path))) } }.awaitAll()
        }
        return FlipperCatalog.rankedCodesets(parsed.filter { it.second.signals.isNotEmpty() }, maxFamilies)
    }

    fun rawUrl(path: String): String = "https://raw.githubusercontent.com/$repo/$ref/${encodePath(path)}"

    private suspend fun listDirectory(path: String): List<ContentEntry> {
        val body = http.get("https://api.github.com/repos/$repo/contents/${encodePath(path)}?ref=$ref")
        val array = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            ContentEntry(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "file",
            )
        }
    }

    private data class ContentEntry(val name: String, val path: String, val type: String)
}

/** Layer 3 fetcher for SmartIR: reads the docs index tables, then the code files of one brand. */
class SmartIrRemote(
    private val http: HttpFetcher,
    val repo: String = "smartHomeHub/SmartIR",
    val ref: String = "master",
) {
    suspend fun codesets(category: DeviceCategory, brand: String, maxProfiles: Int = 4): List<CatalogCodeset> {
        val platform = SmartIrPlatform.forCategory(category) ?: return emptyList()
        val key = BrandKey.of(brand)
        val entries = SmartIr.parseIndex(http.get(rawUrl("docs/${platform.docsPage}")))
            .filter { BrandKey.of(it.manufacturer) == key && it.controller.contains("Broadlink", ignoreCase = true) }
            .distinctBy { it.code }
            .take(maxProfiles)
        return coroutineScope {
            entries.mapIndexed { index, entry ->
                async {
                    runCatching {
                        SmartIr.parse(http.get(rawUrl("codes/${platform.folder}/${entry.code}.json")), platform, entry.code, category, rank = index + 1)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun rawUrl(path: String): String = "https://raw.githubusercontent.com/$repo/$ref/${encodePath(path)}"
}

private fun encodePath(path: String): String =
    path.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }
