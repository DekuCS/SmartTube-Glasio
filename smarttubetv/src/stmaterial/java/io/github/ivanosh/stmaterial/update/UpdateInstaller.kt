package io.github.ivanosh.stmaterial.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * Hands a verified APK to the system installer, which asks the user to confirm. There is no silent install: the
 * session needs the user's action and this class only opens the screen the system asks for. The file is copied into
 * the session and deleted at once, so no copy stays in the app's cache whatever happens next.
 */
class UpdateInstaller(private val activity: Activity, private val onResult: (Outcome) -> Unit) {
    enum class Outcome { CONFIRMING, CANCELLED, FAILED }

    private var receiver: BroadcastReceiver? = null

    /** Returns false if the session could not be made; the file is deleted either way. */
    fun install(apk: File): Boolean {
        val installer = activity.packageManager.packageInstaller
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(activity.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                register()
                val intent = Intent(ACTION).setPackage(activity.packageName)
                val pending = PendingIntent.getBroadcast(activity, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
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
        }
    }

    private fun register() {
        unregister()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        if (confirm == null) onResult(Outcome.FAILED) else {
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
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED) else activity.registerReceiver(r, filter)
    }

    fun unregister() {
        receiver?.let { try { activity.unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        receiver = null
    }

    private companion object {
        const val ACTION = "io.github.ivanosh.smarttubematerial.UPDATE_RESULT"
    }
}
