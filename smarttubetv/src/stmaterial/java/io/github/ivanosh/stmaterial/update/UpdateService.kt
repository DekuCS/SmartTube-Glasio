package io.github.ivanosh.stmaterial.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.ivanosh.stmaterial.logic.UpdateChannel
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.pickUpdate
import java.io.IOException
import java.util.concurrent.Executors

sealed interface CheckResult {
    class Offer(val offer: UpdateOffer) : CheckResult
    object UpToDate : CheckResult
    class Failed(val reason: Failure) : CheckResult
}

enum class Failure { NETWORK, RATE_LIMITED, BAD_FEED }

/** One check on a worker thread, started by the caller and never repeated by itself. */
object UpdateService {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "material-update").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** [onResult] runs on the main thread. */
    fun check(context: Context, channel: UpdateChannel, onResult: (CheckResult) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            // The channel is read again here: a request queued before Off was chosen makes no call.
            val current = UpdateStore(app).channel
            val result = if (current == UpdateChannel.OFF) CheckResult.UpToDate else try {
                val releases = UpdateClient(app).fetchReleases()
                val offer = pickUpdate(releases, current, installedVersionName(app), UpdateClient.supportedAbis())
                if (offer != null) CheckResult.Offer(offer) else CheckResult.UpToDate
            } catch (e: FeedHttpException) {
                CheckResult.Failed(if (e.code == 403 || e.code == 429) Failure.RATE_LIMITED else Failure.NETWORK)
            } catch (e: IOException) {
                CheckResult.Failed(Failure.NETWORK)
            } catch (e: org.json.JSONException) {
                CheckResult.Failed(Failure.BAD_FEED)
            } catch (e: RuntimeException) {
                CheckResult.Failed(Failure.BAD_FEED)
            }
            main.post { onResult(result) }
        }
    }

    fun runInBackground(task: Runnable) = executor.execute(task)

    fun postMain(task: Runnable) = main.post(task)
}
