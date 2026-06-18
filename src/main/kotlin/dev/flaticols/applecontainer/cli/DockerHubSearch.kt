package dev.flaticols.applecontainer.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Best-effort Docker Hub repository-name search via the public, unauthenticated
 * `/v2/search/repositories/` endpoint. Used only to enrich image-reference
 * autocompletion — any failure (offline, rate-limited, schema change) yields an
 * empty list, so completion silently falls back to local + curated suggestions.
 */
object DockerHubSearch {
    const val MIN_QUERY = 2
    private const val BASE = "https://hub.docker.com/v2/search/repositories/"

    @Serializable private data class HubSearch(val results: List<HubRepo> = emptyList())
    @Serializable private data class HubRepo(val repo_name: String? = null)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /** Repository names matching [query], official/most-starred first (Hub's default order). */
    fun search(query: String, limit: Int = 25): List<String> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        val url = "$BASE?page_size=$limit&query=${URLEncoder.encode(q, StandardCharsets.UTF_8)}"
        return try {
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return emptyList()
            json.decodeFromString<HubSearch>(response.body()).results.mapNotNull { it.repo_name }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
