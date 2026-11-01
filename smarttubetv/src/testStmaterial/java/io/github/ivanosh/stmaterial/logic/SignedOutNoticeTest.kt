package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SignedOutNoticeTest {
    @Test
    fun accountSectionKeepsSignIn() {
        listOf(
            MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_USER_PLAYLISTS,
            MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_CHANNEL_UPLOADS
        ).forEach {
            assertEquals("id $it", SignedOutNotice.SIGN_IN, signedOutNotice(isSignInError = true, sectionId = it, authOnly = false))
        }
    }

    @Test
    fun authOnlyFlagKeepsSignIn() {
        assertEquals(SignedOutNotice.SIGN_IN, signedOutNotice(true, 12345, authOnly = true))
    }

    @Test
    fun publicSectionGetsRetryAndSignIn() {
        listOf(MediaGroup.TYPE_TRENDING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_LIVE, 987654).forEach {
            assertEquals("id $it", SignedOutNotice.PUBLIC, signedOutNotice(true, it, false))
        }
    }

    @Test
    fun homeGetsItsExtraLine() {
        assertEquals(SignedOutNotice.HOME, signedOutNotice(true, MediaGroup.TYPE_HOME, false))
    }

    @Test
    fun otherErrorsPassThrough() {
        assertEquals(SignedOutNotice.PASS_THROUGH, signedOutNotice(false, MediaGroup.TYPE_HOME, false))
        assertEquals(SignedOutNotice.PASS_THROUGH, signedOutNotice(false, MediaGroup.TYPE_SUBSCRIPTIONS, true))
    }

    @Test
    fun noSelectedSectionPassesThrough() {
        assertEquals(SignedOutNotice.PASS_THROUGH, signedOutNotice(true, null, false))
    }
}
