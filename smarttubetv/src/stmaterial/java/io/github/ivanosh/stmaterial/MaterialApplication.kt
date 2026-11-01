package io.github.ivanosh.stmaterial

import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView
import com.liskovsoft.smartyoutubetv2.common.utils.Utils
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
import io.github.ivanosh.stmaterial.channel.MaterialChannelActivity
import io.github.ivanosh.stmaterial.channel.MaterialUploadsActivity
import io.github.ivanosh.stmaterial.search.MaterialSearchActivity
import io.github.ivanosh.stmaterial.signin.MaterialSignInActivity
import io.github.ivanosh.stmaterial.browse.MaterialBrowseActivity
import io.github.ivanosh.stmaterial.dialog.MaterialDialogActivity
import io.github.ivanosh.stmaterial.player.MaterialPlaybackActivity

class MaterialApplication : MainApplication() {
    /** The single place where the Material screens replace the upstream ones. */
    override fun registerViews() {
        super.registerViews()
        // Before any presenter: BrowsePresenter maps the Settings section only once, when it is created.
        SidebarService.instance(this).enableMaterialUi()
        val viewManager = ViewManager.instance(this)
        val browse = MaterialBrowseActivity::class.java
        viewManager.setRoot(browse)
        viewManager.register(BrowseView::class.java, browse)
        // Every AppDialogPresenter window, the ones over the player included.
        viewManager.register(AppDialogView::class.java, MaterialDialogActivity::class.java, browse)
        // Back from the other screens has to land on the new home.
        listOf(
            PlaybackView::class.java,
            AddDeviceView::class.java,
            WebBrowserView::class.java,
        ).forEach { view -> viewManager.register(view, viewManager.getActivity(view), browse) }
        viewManager.register(SearchView::class.java, MaterialSearchActivity::class.java, browse)
        viewManager.register(ChannelView::class.java, MaterialChannelActivity::class.java, browse)
        viewManager.register(ChannelUploadsView::class.java, MaterialUploadsActivity::class.java, browse)
        // Not in the view stack: closing the sign-in code returns to the screen that opened it, settings or home.
        viewManager.register(SignInView::class.java, MaterialSignInActivity::class.java)
        // MATERIAL: links are never sent to the QR service of api.qrserver.com
        Utils.disableQrService()
        // MATERIAL: no home screen channels. The receiver is removed from the manifest too; this stops a worker that an older build left.
        Utils.disableChannelsService()
        cancelLeftoverChannelsWork()
        // After the list: the parent lookup goes by isInstance, so PlaybackActivity needs the new home too.
        viewManager.register(PlaybackView::class.java, MaterialPlaybackActivity::class.java, browse)
    }

    /** Once: a periodic worker that a build before this one enqueued survives an update. */
    private fun cancelLeftoverChannelsWork() {
        val prefs = getSharedPreferences("material_app", MODE_PRIVATE)
        if (prefs.getBoolean(CHANNELS_WORK_CANCELLED, false)) return
        Utils.cancelChannelsWork(this)
        prefs.edit().putBoolean(CHANNELS_WORK_CANCELLED, true).apply()
    }

    private companion object {
        const val CHANNELS_WORK_CANCELLED = "channels_work_cancelled"
    }
}
