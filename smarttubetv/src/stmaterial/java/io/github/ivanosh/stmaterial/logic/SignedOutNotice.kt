package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup

/** What an empty section says to a signed-out viewer. The presenter says "sign in" for every section. */
enum class SignedOutNotice { PASS_THROUGH, SIGN_IN, PUBLIC, HOME }

/** Sections that show an account's own data, whatever the flag in BrowsePresenter's mapping says. */
private val ACCOUNT_SECTIONS = setOf(
    MediaGroup.TYPE_SUBSCRIPTIONS,
    MediaGroup.TYPE_HISTORY,
    MediaGroup.TYPE_USER_PLAYLISTS,
    MediaGroup.TYPE_NOTIFICATIONS,
    MediaGroup.TYPE_MY_VIDEOS,
    MediaGroup.TYPE_CHANNEL_UPLOADS
)

/** [isSignInError] is whether the presenter's error is its sign-in prompt; any other error is left alone. */
fun signedOutNotice(isSignInError: Boolean, sectionId: Int?, authOnly: Boolean): SignedOutNotice = when {
    !isSignInError || sectionId == null -> SignedOutNotice.PASS_THROUGH
    authOnly || sectionId in ACCOUNT_SECTIONS -> SignedOutNotice.SIGN_IN
    sectionId == MediaGroup.TYPE_HOME -> SignedOutNotice.HOME
    else -> SignedOutNotice.PUBLIC
}
