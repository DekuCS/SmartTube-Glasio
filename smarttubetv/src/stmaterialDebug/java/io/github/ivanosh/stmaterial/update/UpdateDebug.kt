package io.github.ivanosh.stmaterial.update

import android.content.Context
import java.io.File
import java.util.Properties

/** Where a debug build's updater looks, and which signer it accepts. */
class UpdateOverride(val base: String, val cert: String?)

/**
 * Debug builds only (this file is in src/stmaterialDebug; the release build has a version that returns null).
 * `files/update-debug.properties` in the app's private storage, written with `adb shell run-as`, holds `base=`
 * (a test server that serves `/releases` and `/download/<tag>/<asset>`) and optionally `cert=` (a signer to
 * accept in place of the production key, to see the installer reached with a test APK).
 */
object UpdateDebug {
    fun override(context: Context): UpdateOverride? {
        val file = File(context.filesDir, "update-debug.properties")
        if (!file.isFile) return null
        return try {
            val props = Properties().also { p -> file.inputStream().use { p.load(it) } }
            val base = props.getProperty("base")?.trim()?.trimEnd('/').orEmpty()
            if (base.isEmpty()) null else UpdateOverride(base, props.getProperty("cert")?.trim()?.takeIf { it.isNotEmpty() })
        } catch (e: java.io.IOException) {
            null
        }
    }
}
