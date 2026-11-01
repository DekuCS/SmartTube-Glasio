package io.github.ivanosh.stmaterial.channel

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView
import io.github.ivanosh.stmaterial.browse.FeedActivity

/**
 * A channel: its rows (uploads, playlists, ...) under the channel's name. The rows, their pages and
 * the sorting stay in [ChannelPresenter]; this implements [ChannelView] and draws it.
 */
class MaterialChannelActivity : FeedActivity<ChannelPresenter>(), ChannelView {
    override val presenter: ChannelPresenter by lazy { ChannelPresenter.instance(this) }

    override fun connect() {
        presenter.setView(this)
    }

    override fun update(videoGroup: VideoGroup) {
        updateFeed(videoGroup)
        presenter.channel?.let { title = it.getAuthor() ?: it.title }
    }

    // The old screen jumps to a row of its list; there is no such list here.
    override fun setPosition(index: Int) = Unit

    override fun showProgressBar(show: Boolean) {
        state.loading = show
    }

    override fun clear() {
        title = null
        clearFeed()
    }
}

/**
 * The contents of a playlist or a channel that is one list of uploads: one grid. [ChannelUploadsPresenter]
 * loads it; the long press offers the card menu, which has no Remove from playlist.
 */
class MaterialUploadsActivity : FeedActivity<ChannelUploadsPresenter>(), ChannelUploadsView {
    override val presenter: ChannelUploadsPresenter by lazy { ChannelUploadsPresenter.instance(this) }

    override fun connect() {
        // A section of the grid type makes the state lay the group out as a grid, as the home screen does.
        state.sections.add(BrowseSection(GRID_SECTION, "", BrowseSection.TYPE_GRID, -1))
        state.selectTab(GRID_SECTION)
        presenter.setView(this)
    }

    override fun update(videoGroup: VideoGroup) {
        updateFeed(videoGroup)
        title = presenter.channel?.let { it.title ?: it.getAuthor() } ?: videoGroup.title
    }

    override fun showProgressBar(show: Boolean) {
        state.loading = show
    }

    override fun clear() {
        title = null
        clearFeed()
    }

    private companion object {
        /** An id no section of the presenters has. */
        const val GRID_SECTION = MediaGroup.TYPE_UNDEFINED - 1000
    }
}
