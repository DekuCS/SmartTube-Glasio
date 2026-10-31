package io.github.ivanosh.stmaterial.data

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.sharedutils.rx.OnError
import com.liskovsoft.sharedutils.rx.OnResult
import com.liskovsoft.sharedutils.rx.RxHelper
import io.reactivex.Observable
import io.reactivex.disposables.Disposable

/**
 * One request for the first page of the watch history, for "Continue watching". [observe] is the
 * ContentService observable the History tab uses. An empty or failed answer is silence, not an error.
 */
class HistorySource(private val observe: () -> Observable<MediaGroup>) {
    private var action: Disposable? = null

    /** Replaces a request that is still running. [onGroup] gets the answer on the thread of the observable. */
    fun load(onGroup: (MediaGroup) -> Unit) {
        dispose()
        action = RxHelper.execute(
            observe().take(1),
            OnResult<MediaGroup> { group -> if (!group.mediaItems.isNullOrEmpty()) onGroup(group) },
            OnError { },
        )
    }

    fun dispose() {
        RxHelper.disposeActions(action)
        action = null
    }
}
