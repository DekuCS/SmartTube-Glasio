package io.github.ivanosh.stmaterial.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.ivanosh.stmaterial.logic.ArchiveFacts
import io.github.ivanosh.stmaterial.logic.MAX_APK_BYTES
import io.github.ivanosh.stmaterial.logic.RELEASES_URL
import io.github.ivanosh.stmaterial.logic.Release
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.downloadUrl
import io.github.ivanosh.stmaterial.logic.isAllowedHop
import io.github.ivanosh.stmaterial.logic.parseReleases
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The feed is rate limited or refused: [code] is the HTTP status. */
class FeedHttpException(val code: Int) : IOException("HTTP $code")

/**
 * The network side of the updater: the list of releases, and the download of one APK. Only HTTPS to GitHub's hosts;
 * every hop of a redirect is checked, and the first address is built from the tag and the asset name, never copied
 * from the feed. A debug build may be pointed at a test server by [UpdateDebug]; a release build cannot.
 */
class UpdateClient(private val context: Context) {
    private val override = UpdateDebug.override(context)

    fun fetchReleases(): List<Release> {
        val url = override?.let { "${it.base}/releases" } ?: RELEASES_URL
        val conn = open(url)
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw FeedHttpException(code)
            val bytes = conn.inputStream.use { it.readLimited(2 * 1024 * 1024) }
            return parseReleases(String(bytes, Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }

    /** What the download came to: the digest and the size of the bytes that arrived. */
    class Downloaded(val sha256: String, val size: Long)

    /** Writes the asset to [dest]. [progress] gets the percent; [cancelled] is polled while bytes arrive. */
    fun download(offer: UpdateOffer, dest: File, progress: (Int) -> Unit, cancelled: () -> Boolean): Downloaded {
        var url = override?.let { "${it.base}/download/${offer.tag}/${offer.asset.name}" }
            ?: downloadUrl(offer.tag, offer.asset.name)
            ?: throw IOException("Not a release asset of this app")
        var hops = 0
        while (true) {
            val conn = open(url)
            try {
                val code = conn.responseCode
                if (code in 301..308 && code != 304) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without a target")
                    val resolved = URL(URL(url), next).toString()
                    if (++hops > MAX_REDIRECTS || !allowed(resolved)) throw IOException("Redirect refused")
                    url = resolved
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw FeedHttpException(code)
                return conn.inputStream.use { copy(it, dest, offer.asset.size, progress, cancelled) }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun copy(input: java.io.InputStream, dest: File, expected: Long, progress: (Int) -> Unit, cancelled: () -> Boolean): Downloaded {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var lastPercent = -1
        dest.parentFile?.mkdirs()
        dest.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (cancelled()) throw IOException("Cancelled")
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                // A file larger than the release lists is not the release: stop before it fills the disk.
                if (total > expected || total > MAX_APK_BYTES) throw IOException("Larger than listed")
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
                val percent = (total * 100 / expected).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    progress(percent)
                }
            }
        }
        return Downloaded(digest.digest().joinToString("") { "%02x".format(it) }, total)
    }

    private fun allowed(url: String): Boolean = isAllowedHop(url) || (override != null && url.startsWith(override.base))

    private fun open(url: String): HttpURLConnection {
        if (!allowed(url)) throw IOException("Address refused")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "SmartTubeMaterial-updater")
        return conn
    }

    /** What the package manager says of the file, with the signers it verified; null when it cannot read it. */
    fun archiveFacts(file: File): ArchiveFacts? {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { sha256Hex(it.toByteArray()) }
        return ArchiveFacts(info.packageName, info.longVersionCode, info.versionName, signers)
    }

    /** The production key, or the one a debug build was told to accept. */
    fun pinnedCert(): String = override?.cert ?: io.github.ivanosh.stmaterial.logic.PINNED_CERT_SHA256

    companion object {
        private const val MAX_REDIRECTS = 5

        fun supportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun apkFile(context: Context): File = File(File(context.cacheDir, "update"), "update.apk")

        /** An APK left by a killed process or a failure. */
        fun deleteLeftovers(context: Context) {
            File(context.cacheDir, "update").listFiles()?.forEach { it.delete() }
        }
    }
}

private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        if (out.size() + n > limit) throw IOException("Response too large")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
