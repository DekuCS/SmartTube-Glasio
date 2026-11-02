package io.github.ivanosh.stmaterial.logic

enum class SubscribeLabel { SUBSCRIBE, SUBSCRIBED, SIGN_IN }

/** What OK on the button does. [ASK_UNSUBSCRIBE] opens the confirmation; nothing is sent before it. */
enum class SubscribeAction { NONE, SIGN_IN, SUBSCRIBE, ASK_UNSUBSCRIBE }

data class SubscribeButton(val label: SubscribeLabel, val enabled: Boolean, val action: SubscribeAction)

/**
 * The Subscribe button of a channel page. [subscribed] is null until the account's state is known;
 * [pending] is a subscribe or unsubscribe call in flight. Signed out the button only leads to sign-in.
 */
fun subscribeButton(signedIn: Boolean, subscribed: Boolean?, pending: Boolean): SubscribeButton = when {
    !signedIn -> SubscribeButton(SubscribeLabel.SIGN_IN, enabled = true, action = SubscribeAction.SIGN_IN)
    subscribed == null -> SubscribeButton(SubscribeLabel.SUBSCRIBE, enabled = false, action = SubscribeAction.NONE)
    pending -> SubscribeButton(label(subscribed), enabled = false, action = SubscribeAction.NONE)
    subscribed -> SubscribeButton(SubscribeLabel.SUBSCRIBED, enabled = true, action = SubscribeAction.ASK_UNSUBSCRIBE)
    else -> SubscribeButton(SubscribeLabel.SUBSCRIBE, enabled = true, action = SubscribeAction.SUBSCRIBE)
}

private fun label(subscribed: Boolean) = if (subscribed) SubscribeLabel.SUBSCRIBED else SubscribeLabel.SUBSCRIBE

/** The state after a call that started from [before]: it changes only when the call succeeded. */
fun subscribedAfter(before: Boolean, succeeded: Boolean): Boolean = if (succeeded) !before else before
