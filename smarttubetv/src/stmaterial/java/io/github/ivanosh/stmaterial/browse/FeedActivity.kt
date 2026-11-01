package io.github.ivanosh.stmaterial.browse

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.sharedutils.helpers.KeyHelpers
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity
import io.github.ivanosh.stmaterial.dialog.ConfirmKeyGuard
import io.github.ivanosh.stmaterial.ui.FeedPage
import io.github.ivanosh.stmaterial.ui.FeedPageActions
import io.github.ivanosh.stmaterial.ui.MenuItem

/**
 * The part the screens of cards without tabs share: the presenter feeds [state], [FeedPage] draws it,
 * and a card does what it does on the home screen. A subclass binds the view interface of its presenter.
 */
abstract class FeedActivity<P> : LeanbackActivity() where P : BasePresenter<*>, P : VideoGroupPresenter {
    protected val state = BrowseState(rowTitle = { getString(it) })
    protected abstract val presenter: P
    protected var title by mutableStateOf<String?>(null)
    private val cardMenu = CardMenu(this, onGone = { state.updateSection(it) }, onMessage = { state.message = it })

    protected val feedActions = object : FeedPageActions {
        override fun onVideoFocused(video: Video) = presenter.onVideoItemSelected(video)
        override fun onVideoClicked(video: Video) = presenter.onVideoItemClicked(video)
        // The menu is the page's own: the presenters' long press opens the old dialog.
        override fun onVideoLongClicked(video: Video) = Unit
        override fun videoMenuItems(video: Video): List<MenuItem> = cardMenu.items(video, MediaGroup.TYPE_UNDEFINED)
        override fun onNeedMore(row: VideoRow) {
            state.takeMoreRequest(row)?.let(presenter::onScrollEnd)
        }
    }

    /** Gives the presenter this view. */
    protected abstract fun connect()

    private val confirmKeyGuard = ConfirmKeyGuard()

    /**
     * The player opens the channel and search on a held OK, so the rest of that press (repeats and the up)
     * would land on the first card. Confirm keys count only from a press that starts on this screen.
     */
    override fun interceptKeyEvent(event: KeyEvent): Boolean =
        KeyHelpers.isConfirmKey(event.keyCode) &&
            confirmKeyGuard.shouldDrop(event.action == KeyEvent.ACTION_DOWN, event.repeatCount)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply { setContent { Screen() } })
        // The screen is opaque, a window background would only be one more fill per frame.
        window.setBackgroundDrawable(null)
        connect()
        presenter.onViewInitialized()
    }

    override fun onResume() {
        super.onResume()
        presenter.onViewResumed()
    }

    override fun onPause() {
        super.onPause()
        presenter.onViewPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onViewDestroyed()
    }

    /** The user left for good (Back), as against a system kill that the presenter restores from. */
    override fun finishReally() {
        super.finishReally()
        presenter.onFinish()
    }

    @Composable
    protected open fun Screen() = FeedPage(title, state, feedActions)

    protected fun updateFeed(group: VideoGroup) = state.updateSection(group)

    protected fun clearFeed() = state.clearContent()
}
