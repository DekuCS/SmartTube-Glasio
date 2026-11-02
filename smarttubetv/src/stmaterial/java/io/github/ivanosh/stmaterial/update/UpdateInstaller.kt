package io.github.ivanosh.stmaterial.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.ivanosh.stmaterial.logic.PercentGate
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Hands a verified APK to the system installer, which asks the user to confirm. There is no silent install: the
 * session needs the user's action and this class only opens the screen the system asks for.
 *
 * [install] runs on a worker thread. It copies the file into the session while hashing the bytes it copies, so what
 * the installer gets is what the digest of the release names, and deletes the file whatever happens.
 * [onPercent] is called from that thread for each whole percent copied, [onFinishing] once when the copy is done and the
 * bytes are being flushed to disk (fsync), which can take a while on slow storage.
 * The result receiver is not exported on any API level, takes only the session it made, and starts only an activity
 * of a system app (the package installer).
 */
class UpdateInstaller(private val activity: Activity, private val onResult: (Outcome) -> Unit) {
    enum class Outcome { CONFIRMING, CANCELLED, FAILED }

    private var receiver: BroadcastReceiver? = null
    @Volatile private var sessionId = -1
    @Volatile private var abandoned = false

    /** True when the session is committed. False: nothing was committed and the session is gone. */
    fun install(
        apk: File,
        expectedSha256: String,
        cancelled: () -> Boolean,
        onPercent: (Int) -> Unit = {},
        onFinishing: () -> Unit = {},
    ): Boolean {
        val installer = activity.packageManager.packageInstaller
        var id = -1
        var committed = false
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(activity.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            id = installer.createSession(params)
            abandoned = false
            installer.openSession(id).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                val total = apk.length()
                val gate = PercentGate()
                var copied = 0L
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled()) throw IOException("Cancelled")
                            val n = input.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                            copied += n
                            gate.next(copied, total)?.let(onPercent)
                        }
                        onFinishing()
                        session.fsync(out)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true) || cancelled()) throw IOException("Not the verified file")
                sessionId = id
                register()
                val intent = Intent(ACTION).setPackage(activity.packageName)
                // Mutable because the system adds the status to it; explicit by package, received by a receiver that is not exported.
                val pending = PendingIntent.getBroadcast(activity, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
                committed = true
            }
            return true
        } catch (e: IOException) {
            return false
        } catch (e: SecurityException) {
            return false
        } catch (e: IllegalStateException) {
            return false
        } finally {
            apk.delete()
            if (!committed) {
                if (id >= 0) try { installer.abandonSession(id) } catch (e: RuntimeException) { }
                unregister()
            }
        }
    }

    /** Gives up a session that is waiting for the user, and ignores what the system says about it from now on. */
    fun abandon() {
        abandoned = true
        val id = sessionId
        unregister()
        if (id >= 0) try { activity.packageManager.packageInstaller.abandonSession(id) } catch (e: RuntimeException) { }
    }

    @Synchronized
    private fun register() {
        unregister()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (abandoned || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2) != sessionId) return
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        if (confirm == null || !isSystemActivity(confirm)) {
                            abandon()
                            onResult(Outcome.FAILED)
                        } else {
                            activity.startActivity(confirm)
                            onResult(Outcome.CONFIRMING)
                        }
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> onResult(Outcome.CANCELLED)
                    PackageInstaller.STATUS_SUCCESS -> Unit // the app is being replaced
                    else -> onResult(Outcome.FAILED)
                }
            }
        }
        receiver = r
        // RECEIVER_NOT_EXPORTED on every API level: below 33 the compat call adds a signature permission instead.
        ContextCompat.registerReceiver(activity, r, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** The confirmation screen belongs to the system's package installer, not to any other app. */
    private fun isSystemActivity(confirm: Intent): Boolean {
        val info = confirm.resolveActivity(activity.packageManager)?.let {
            try { activity.packageManager.getApplicationInfo(it.packageName, 0) } catch (e: Exception) { null }
        } ?: return false
        return info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    @Synchronized
    fun unregister() {
        receiver?.let { try { activity.unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        receiver = null
    }

    private companion object {
        const val ACTION = "io.github.ivanosh.smarttubematerial.UPDATE_RESULT"
    }
}
