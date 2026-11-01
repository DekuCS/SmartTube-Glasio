package io.github.ivanosh.stmaterial.logic

import org.json.JSONArray
import java.net.URL

/*
 * The decisions of the in-app updater, with no Android in them: which release is offered on a channel, whether a
 * check is due, which addresses may be fetched, and whether a downloaded file may be handed to the installer.
 * The feed is this repository's GitHub Releases. See README-Material.md, "Updates".
 */

enum class UpdateChannel(val key: String) {
    OFF("off"), STABLE("stable"), PRERELEASE("prerelease");

    companion object {
        /** Anything unknown is the default, Stable, never the riskier channel. */
        fun fromKey(key: String?): UpdateChannel = values().firstOrNull { it.key == key } ?: STABLE
    }
}

/** [sha256] is what the feed says, as it was written (`sha256:<hex>`); an offer holds the bare lowercase hex. */
data class ReleaseAsset(val name: String, val size: Long, val sha256: String?)

data class Release(val tag: String, val prerelease: Boolean, val draft: Boolean, val body: String, val assets: List<ReleaseAsset>)

data class UpdateOffer(
    val tag: String,
    val versionName: String,
    val prerelease: Boolean,
    val notes: List<String>,
    val asset: ReleaseAsset,
    val abi: String,
)

/** The signing certificate of every release, as in the release workflow and README-Material.md. */
const val PINNED_CERT_SHA256 = "8619c34516110bf503c6850f1c405778fae8fd318beb82b138db207724748e8c"
const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
const val MAX_APK_BYTES = 150L * 1024 * 1024

const val REPO = "ivanosh/SmartTube-Material"
const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=30"

private const val TAG_PREFIX = "material-v"
private const val UNIVERSAL = "universal"
private val APK_ABIS = listOf("arm64-v8a", "armeabi-v7a")
private val VERSION = Regex("""[0-9]{1,4}(\.[0-9]{1,4}){1,3}-[0-9]{1,2}""")
private val HEX64 = Regex("[0-9a-fA-F]{64}")
private val HOSTS = setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

/** `material-v32.68-1` is version 32.68-1; any other tag is not one of our releases. */
fun versionOfTag(tag: String): String? =
    tag.takeIf { it.startsWith(TAG_PREFIX) }?.substring(TAG_PREFIX.length)?.takeIf { parseVersion(it) != null }

/** `32.68-1` is [32, 68, 1]: upstream's version, then this fork's patch. Null for any other shape. */
fun parseVersion(name: String): List<Int>? {
    if (!VERSION.matches(name)) return null
    return name.split('.', '-').map { it.toInt() }
}

fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
        if (c != 0) return c
    }
    return 0
}

/** Throws unless [json] is a JSON array. Entries that are not releases are left out. */
fun parseReleases(json: String): List<Release> {
    val array = JSONArray(json)
    val releases = mutableListOf<Release>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val tag = item.optString("tag_name", "").takeIf { !item.isNull("tag_name") && it.isNotEmpty() } ?: continue
        val assets = mutableListOf<ReleaseAsset>()
        val list = item.optJSONArray("assets")
        if (list != null) {
            for (j in 0 until list.length()) {
                val a = list.optJSONObject(j) ?: continue
                val name = a.optString("name", "").takeIf { !a.isNull("name") && it.isNotEmpty() } ?: continue
                val digest = if (a.isNull("digest")) null else a.optString("digest", "").takeIf { it.isNotEmpty() }
                assets += ReleaseAsset(name, a.optLong("size", 0), digest)
            }
        }
        releases += Release(
            tag = tag,
            // Fail closed: only a JSON `false` is "not a prerelease" and "not a draft". Missing, null, a string or a
            // number is a prerelease and a draft.
            prerelease = item.opt("prerelease") != false,
            draft = item.opt("draft") != false,
            body = if (item.isNull("body")) "" else item.optString("body", ""),
            assets = assets,
        )
    }
    return releases
}

private fun digestHex(digest: String?): String? {
    val hex = digest?.takeIf { it.startsWith("sha256:") }?.substring("sha256:".length) ?: return null
    return if (HEX64.matches(hex)) hex.lowercase() else null
}

/**
 * The newest release the [channel] allows that is newer than [installedVersionName] and has an APK for this device
 * with a digest. Stable takes only releases not marked prerelease; Prerelease takes either. Drafts and tags that are
 * not ours are never taken. The newest is by version, not by the order or the date of the feed.
 */
fun pickUpdate(
    releases: List<Release>,
    channel: UpdateChannel,
    installedVersionName: String,
    supportedAbis: List<String>,
): UpdateOffer? {
    if (channel == UpdateChannel.OFF) return null
    val installed = parseVersion(installedVersionName) ?: return null
    val abis = supportedAbis.filter { it in APK_ABIS } + UNIVERSAL

    return releases.asSequence()
        .filter { !it.draft && (channel == UpdateChannel.PRERELEASE || !it.prerelease) }
        .mapNotNull { release ->
            val name = versionOfTag(release.tag) ?: return@mapNotNull null
            val version = parseVersion(name) ?: return@mapNotNull null
            if (compareVersions(version, installed) <= 0) return@mapNotNull null
            for (abi in abis) {
                val asset = release.assets.firstOrNull { it.name == "SmartTube_material_${name}_$abi.apk" } ?: continue
                val hex = digestHex(asset.sha256) ?: continue
                if (asset.size <= 0 || asset.size > MAX_APK_BYTES) continue
                return@mapNotNull version to UpdateOffer(
                    release.tag, name, release.prerelease, releaseNotes(release.body), asset.copy(sha256 = hex), abi,
                )
            }
            null
        }
        .maxWithOrNull { a, b -> compareVersions(a.first, b.first) }
        ?.second
}

/**
 * Whether an offer found earlier may still be shown or downloaded under the channel chosen now. The channel can
 * change between the check and the prompt, so this is asked again at both places: Off never offers, Stable never a
 * prerelease.
 */
fun offerAllowed(offer: UpdateOffer, channel: UpdateChannel): Boolean = when (channel) {
    UpdateChannel.OFF -> false
    UpdateChannel.STABLE -> !offer.prerelease
    UpdateChannel.PRERELEASE -> true
}

/** At most once a day. A clock set back counts as due, or the check would wait for the clock to catch up. */
fun shouldCheck(channel: UpdateChannel, lastCheckMs: Long, nowMs: Long): Boolean = when {
    channel == UpdateChannel.OFF -> false
    lastCheckMs <= 0 -> true
    nowMs < lastCheckMs -> true
    else -> nowMs - lastCheckMs >= CHECK_INTERVAL_MS
}

/** https on a GitHub host and nothing else. Parsed by the same class that the connection uses. */
fun isAllowedHop(url: String): Boolean {
    val parsed = try {
        URL(url)
    } catch (e: java.net.MalformedURLException) {
        return false
    }
    return parsed.protocol == "https" && parsed.userInfo == null && (parsed.port == -1 || parsed.port == 443) &&
        parsed.host.lowercase() in HOSTS
}

/** Where an asset is downloaded from. Built here from the tag and the name, never taken from the feed. */
fun downloadUrl(tag: String, assetName: String): String? {
    val version = versionOfTag(tag) ?: return null
    if ((APK_ABIS + UNIVERSAL).none { assetName == "SmartTube_material_${version}_$it.apk" }) return null
    return "https://github.com/$REPO/releases/download/$tag/$assetName"
}

/** The first lines of the release text, as plain lines for a dialog. */
fun releaseNotes(body: String): List<String> =
    body.replace("\r\n", "\n").lineSequence()
        .map { it.trim().trimStart('#', '*', '-', ' ').trim() }
        .filter { it.isNotEmpty() }
        .take(30)
        .map { it.take(200) }
        .toList()

/** What the package manager reports about the downloaded file. */
data class ArchiveFacts(val packageName: String, val versionCode: Long, val versionName: String?, val signerSha256: List<String>)

enum class Refusal(val message: String) {
    SIZE("The download is not the file the release lists."),
    DIGEST("The download does not match the checksum of the release."),
    UNREADABLE("The downloaded file is not an app package."),
    PACKAGE("The downloaded file is for a different app."),
    VERSION("The downloaded file is not the version offered."),
    DOWNGRADE("The downloaded file is not newer than this app."),
    SIGNER("The downloaded file is not signed with this app's key."),
}

/** Null when the file may go to the installer. [pinnedCert] is the production key except in a debug build's test. */
fun checkDownloaded(
    offer: UpdateOffer,
    actualSha256: String,
    actualSize: Long,
    archive: ArchiveFacts?,
    installedPackage: String,
    installedVersionCode: Long,
    pinnedCert: String = PINNED_CERT_SHA256,
): Refusal? = when {
    actualSize != offer.asset.size -> Refusal.SIZE
    !actualSha256.equals(offer.asset.sha256, ignoreCase = true) -> Refusal.DIGEST
    archive == null -> Refusal.UNREADABLE
    archive.packageName != installedPackage -> Refusal.PACKAGE
    archive.versionName != offer.versionName -> Refusal.VERSION
    archive.versionCode <= installedVersionCode -> Refusal.DOWNGRADE
    archive.signerSha256.size != 1 || !archive.signerSha256[0].equals(pinnedCert, ignoreCase = true) -> Refusal.SIGNER
    else -> null
}
