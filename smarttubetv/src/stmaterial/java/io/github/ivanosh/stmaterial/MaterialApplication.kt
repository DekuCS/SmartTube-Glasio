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
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication
import io.github.ivanosh.stmaterial.browse.MaterialBrowseActivity
import io.github.ivanosh.stmaterial.dialog.MaterialDialogActivity

class MaterialApplication : MainApplication() {
    /** The single place where the Material screens replace the upstream ones. */
    override fun registerViews() {
        super.registerViews()
        // Before any presenter: BrowsePresenter maps the Settings section only once, when it is created.
        SidebarService.instance(this).lockSettingsSection()
        val viewManager = ViewManager.instance(this)
        val browse = MaterialBrowseActivity::class.java
        viewManager.setRoot(browse)
        viewManager.register(BrowseView::class.java, browse)
        // Every AppDialogPresenter window, the ones over the player included.
        viewManager.register(AppDialogView::class.java, MaterialDialogActivity::class.java, browse)
        // Back from the other screens has to land on the new home.
        listOf(
            PlaybackView::class.java,
            SearchView::class.java,
            SignInView::class.java,
            AddDeviceView::class.java,
            ChannelView::class.java,
            ChannelUploadsView::class.java,
            WebBrowserView::class.java,
        ).forEach { view -> viewManager.register(view, viewManager.getActivity(view), browse) }
    }
}
