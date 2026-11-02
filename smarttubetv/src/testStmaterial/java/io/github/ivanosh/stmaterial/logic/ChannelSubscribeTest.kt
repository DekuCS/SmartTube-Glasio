package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSubscribeTest {
    @Test
    fun `signed out the button asks to sign in and is enabled whatever is known`() {
        for (known in listOf(null, true, false)) {
            assertEquals(
                SubscribeButton(SubscribeLabel.SIGN_IN, enabled = true, action = SubscribeAction.SIGN_IN),
                subscribeButton(signedIn = false, subscribed = known, pending = false)
            )
        }
    }

    @Test
    fun `signed in with the state not known yet the button is disabled`() {
        val button = subscribeButton(signedIn = true, subscribed = null, pending = false)
        assertEquals(SubscribeLabel.SUBSCRIBE, button.label)
        assertEquals(false, button.enabled)
        assertEquals(SubscribeAction.NONE, button.action)
    }

    @Test
    fun `not subscribed offers Subscribe and OK subscribes at once`() {
        assertEquals(
            SubscribeButton(SubscribeLabel.SUBSCRIBE, enabled = true, action = SubscribeAction.SUBSCRIBE),
            subscribeButton(signedIn = true, subscribed = false, pending = false)
        )
    }

    @Test
    fun `subscribed shows Subscribed and OK asks before unsubscribing`() {
        assertEquals(
            SubscribeButton(SubscribeLabel.SUBSCRIBED, enabled = true, action = SubscribeAction.ASK_UNSUBSCRIBE),
            subscribeButton(signedIn = true, subscribed = true, pending = false)
        )
    }

    @Test
    fun `while a call runs the button keeps its label and does nothing`() {
        for (known in listOf(true, false)) {
            val button = subscribeButton(signedIn = true, subscribed = known, pending = true)
            assertEquals(if (known) SubscribeLabel.SUBSCRIBED else SubscribeLabel.SUBSCRIBE, button.label)
            assertEquals(SubscribeAction.NONE, button.action)
            assertEquals(false, button.enabled)
        }
    }

    @Test
    fun `the state flips only when the call succeeded`() {
        assertEquals(true, subscribedAfter(before = false, succeeded = true))
        assertEquals(false, subscribedAfter(before = true, succeeded = true))
        assertEquals(false, subscribedAfter(before = false, succeeded = false))
        assertEquals(true, subscribedAfter(before = true, succeeded = false))
    }
}
