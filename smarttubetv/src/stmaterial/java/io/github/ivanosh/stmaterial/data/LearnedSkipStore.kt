package io.github.ivanosh.stmaterial.data

import android.content.Context
import io.github.ivanosh.stmaterial.logic.LearnedScores

/**
 * What auto-skip learned from the owner, in the app's own prefs: a small map of channel id and category to a
 * score, capped by [LearnedScores]. It is never sent anywhere and never written to the account.
 */
class LearnedSkipStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun load(): LearnedScores = LearnedScores.parse(prefs.getString(KEY_SCORES, null))

    fun save(scores: LearnedScores) {
        prefs.edit().putString(KEY_SCORES, scores.serialize()).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_SCORES).apply()
    }

    private companion object {
        const val PREFS_NAME = "stmaterial_learned_skip"
        const val KEY_ENABLED = "enabled"
        const val KEY_SCORES = "scores"
    }
}
