package io.github.ivanosh.stmaterial.logic

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * When an update found by a check is still shown (the mark on the Settings icon and the row in Settings), and how an
 * offer is kept between runs. Nothing here decides what is downloaded or installed: that stays in AppUpdate.kt, which
 * checks the offer again when it is used.
 */

/**
 * The stored offer if it should still be shown: the channel allows it ([offerAllowed]) and it is newer than the
 * installed version. Equal or older (already installed) and an unreadable installed version show nothing.
 */
fun badgeOffer(stored: UpdateOffer?, channel: UpdateChannel, installedVersionName: String): UpdateOffer? {
    if (stored == null || !offerAllowed(stored, channel)) return null
    val installed = parseVersion(installedVersionName) ?: return null
    val offered = parseVersion(stored.versionName) ?: return null
    return stored.takeIf { compareVersions(offered, installed) > 0 }
}

/** What is stored after a check: the offer it found, nothing if it succeeded and found none, else what was there. */
fun storedOfferAfterCheck(stored: UpdateOffer?, found: UpdateOffer?, checkSucceeded: Boolean): UpdateOffer? = when {
    found != null -> found
    checkSucceeded -> null
    else -> stored
}

fun offerToJson(offer: UpdateOffer): String = JSONObject()
    .put("tag", offer.tag)
    .put("versionName", offer.versionName)
    .put("prerelease", offer.prerelease)
    .put("notes", JSONArray(offer.notes))
    .put("assetName", offer.asset.name)
    .put("size", offer.asset.size)
    .put("sha256", offer.asset.sha256)
    .put("abi", offer.abi)
    .toString()

private val HEX64 = Regex("[0-9a-f]{64}")

/**
 * The offer written by [offerToJson], or null for anything that is not exactly such an offer. It is held to the same
 * shape [pickUpdate] gives: our tag and version, an asset name that [downloadUrl] accepts, a bare lowercase digest
 * and a size in range.
 */
fun offerFromJson(json: String?): UpdateOffer? {
    if (json == null) return null
    return try {
        val o = JSONObject(json)
        val tag = o.getString("tag")
        val name = o.getString("versionName")
        val assetName = o.getString("assetName")
        val sha = o.getString("sha256")
        val size = o.getLong("size")
        val prerelease = o.get("prerelease") as? Boolean ?: return null
        val notesArray = o.getJSONArray("notes")
        val notes = (0 until notesArray.length()).map { notesArray.getString(it) }
        if (versionOfTag(tag) != name || downloadUrl(tag, assetName) == null) return null
        if (!HEX64.matches(sha) || size <= 0 || size > MAX_APK_BYTES) return null
        UpdateOffer(tag, name, prerelease, notes, ReleaseAsset(assetName, size, sha), o.getString("abi"))
    } catch (e: JSONException) {
        null
    }
}
