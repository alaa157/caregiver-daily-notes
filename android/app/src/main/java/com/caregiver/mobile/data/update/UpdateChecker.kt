package com.caregiver.mobile.data.update

import com.caregiver.mobile.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A GitHub release that ships an APK for this app. */
data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String,
)

sealed interface UpdateResult {
    data class Available(val update: AvailableUpdate) : UpdateResult
    data object UpToDate : UpdateResult
}

fun interface UpdateChecker {
    suspend fun check(): UpdateResult
}

/**
 * Reads the public GitHub Releases feed and compares it with the installed
 * version. This is deliberately a notice, not an installer: the UI opens the
 * APK download in the browser, so the app never needs the "install unknown
 * apps" permission or a background download service.
 *
 * The request is unauthenticated. GitHub allows 60 requests per hour per IP,
 * and About creates one checker per visit; that is comfortably inside the
 * limit for a single-user app.
 */
class GitHubReleaseChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: HttpUrl = RELEASES_ENDPOINT.toHttpUrl(),
) : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val latest = selectLatest(fetchReleases())
            ?: throw IOException("No downloadable Android release found")
        val installed = AppVersion.parse(currentVersion)
            ?: throw IOException("Installed version is not comparable")
        if (latest.version > installed) {
            UpdateResult.Available(
                AvailableUpdate(
                    version = latest.version.toString(),
                    releaseUrl = latest.releaseUrl,
                    downloadUrl = latest.downloadUrl,
                ),
            )
        } else {
            UpdateResult.UpToDate
        }
    }

    private fun fetchReleases(): List<GitHubReleaseDto> {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "CaregiverDailyNotes/$currentVersion")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub releases request failed (${response.code})")
            }
            val body = response.body?.string()
                ?: throw IOException("GitHub releases response was empty")
            if (body.length > MAX_RESPONSE_CHARS) {
                throw IOException("GitHub releases response was too large")
            }
            return try {
                json.decodeFromString<List<GitHubReleaseDto>>(body)
            } catch (e: SerializationException) {
                throw IOException("GitHub releases response was unreadable", e)
            }
        }
    }

    companion object {
        private const val RELEASES_ENDPOINT =
            "https://api.github.com/repos/alaa157/caregiver-daily-notes/releases?per_page=30"

        private const val RELEASE_PAGE_PREFIX =
            "https://github.com/alaa157/caregiver-daily-notes/releases/tag/"
        private const val RELEASE_DOWNLOAD_PREFIX =
            "https://github.com/alaa157/caregiver-daily-notes/releases/download/"

        private const val MAX_RESPONSE_CHARS = 1_000_000

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        /**
         * Picks the highest version with a real APK asset. All current demo
         * releases are GitHub prereleases, so filtering on `prerelease`
         * would hide the only builds users can install. Only URLs under this
         * repository's release path are accepted.
         */
        internal fun selectLatest(releases: List<GitHubReleaseDto>): AndroidRelease? {
            return releases.asSequence()
                .filterNot { it.draft }
                .mapNotNull { release ->
                    val version = AppVersion.parseTag(release.tagName) ?: return@mapNotNull null
                    if (!release.htmlUrl.startsWith(RELEASE_PAGE_PREFIX)) return@mapNotNull null
                    val apk = release.assets.firstOrNull {
                        it.name.endsWith(".apk", ignoreCase = true) &&
                            it.browserDownloadUrl.startsWith(RELEASE_DOWNLOAD_PREFIX)
                    } ?: return@mapNotNull null
                    AndroidRelease(
                        version = version,
                        releaseUrl = release.htmlUrl,
                        downloadUrl = apk.browserDownloadUrl,
                    )
                }
                .maxByOrNull { it.version }
        }
    }
}

internal data class AndroidRelease(
    val version: AppVersion,
    val releaseUrl: String,
    val downloadUrl: String,
)

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/**
 * Numeric app version with trailing-zero-insensitive comparison. Tags such
 * as `android-0.3.4-demo` and `v0.3.4 (demo)` both normalize to `0.3.4`.
 */
data class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val left = parts.getOrElse(index) { 0 }
            val right = other.parts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        private val LEADING_NUMBERS = Regex("""^\d+(?:\.\d+)*""")

        fun parse(tag: String): AppVersion? {
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) return null
            val match = LEADING_NUMBERS.find(trimmed) ?: return null
            val parts = match.value.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts)
        }

        internal fun parseTag(tag: String): AppVersion? {
            val trimmed = tag.trim()
            val candidate = when {
                trimmed.startsWith("android-", ignoreCase = true) -> trimmed.substring(8)
                trimmed.startsWith("v", ignoreCase = true) -> trimmed.substring(1)
                else -> return null
            }
            return parse(candidate)
        }
    }
}
