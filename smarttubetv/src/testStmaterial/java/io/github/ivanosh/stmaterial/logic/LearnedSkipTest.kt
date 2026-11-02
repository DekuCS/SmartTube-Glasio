package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedSkipTest {
    private val ch = "UCabc"
    private val sponsor = "sponsor"

    @Test
    fun theDecisionIsOffFromTwo() {
        assertTrue(autoSkipOn(-3)); assertTrue(autoSkipOn(0)); assertTrue(autoSkipOn(1))
        assertFalse(autoSkipOn(2)); assertFalse(autoSkipOn(3))
    }

    @Test
    fun oneUndoStaysOn() {
        val scores = LearnedScores()
        assertNull(scores.add(ch, sponsor, +1))
        assertTrue(autoSkipOn(scores.score(ch, sponsor)))
    }

    @Test
    fun twoUndosSwitchItOffAndTwoRightsSwitchItOnAgain() {
        val scores = LearnedScores()
        assertNull(scores.add(ch, sponsor, +1))
        assertEquals(false, scores.add(ch, sponsor, +1))
        assertEquals(true, scores.add(ch, sponsor, -1))
        assertNull(scores.add(ch, sponsor, -1))
        assertTrue(autoSkipOn(scores.score(ch, sponsor)))
    }

    @Test
    fun theScoreIsBoundedSoAChangeOfMindTakesAFewPresses() {
        val scores = LearnedScores()
        repeat(10) { scores.add(ch, sponsor, +1) }
        assertEquals(LEARN_MAX, scores.score(ch, sponsor))
        assertNull(scores.add(ch, sponsor, -1)) // 2: still off
        assertEquals(true, scores.add(ch, sponsor, -1)) // 1: on
        repeat(10) { scores.add(ch, sponsor, -1) }
        assertEquals(LEARN_MIN, scores.score(ch, sponsor))
    }

    @Test
    fun aChangeDoesNotCrossCategoriesOrChannels() {
        val scores = LearnedScores()
        scores.add(ch, "intro", +1)
        scores.add(ch, "intro", +1)
        assertFalse(autoSkipOn(scores.score(ch, "intro")))
        assertTrue(autoSkipOn(scores.score(ch, sponsor)))
        assertTrue(autoSkipOn(scores.score("UCother", "intro")))
    }

    @Test
    fun anEntryBackAtZeroIsDropped() {
        val scores = LearnedScores()
        scores.add(ch, sponsor, +1)
        scores.add(ch, sponsor, -1)
        assertEquals(0, scores.size())
    }

    @Test
    fun theOldestEntryGoesWhenTheCapIsPassed() {
        val scores = LearnedScores()
        for (i in 0 until LEARN_MAX_ENTRIES) scores.add("c$i", sponsor, +1)
        scores.add("c0", sponsor, +1) // touched again: no longer the oldest
        scores.add("new", sponsor, +1)
        assertEquals(LEARN_MAX_ENTRIES, scores.size())
        assertEquals(2, scores.score("c0", sponsor))
        assertEquals(0, scores.score("c1", sponsor))
        assertEquals(1, scores.score("new", sponsor))
    }

    @Test
    fun itSurvivesTheTripToPrefs() {
        val scores = LearnedScores()
        scores.add("a", "intro", +1); scores.add("a", "intro", +1); scores.add("b", sponsor, -1)
        val back = LearnedScores.parse(scores.serialize())
        assertEquals(2, back.score("a", "intro"))
        assertEquals(-1, back.score("b", sponsor))
        assertEquals(2, back.channelCount())
        assertEquals(0, LearnedScores.parse(null).size())
        assertEquals(0, LearnedScores.parse("garbage\n\t\t").size())
    }

    private val a = SkipSpan(60_000, 90_000, sponsor)

    @Test
    fun aRewindIntoAJustSkippedSegmentThatPlaysThreeSecondsCounts() {
        val watch = RewindWatch()
        watch.onAutoSkipped(listOf(a), 0)
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(90_000, 1_000))
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(59_000, 5_000))
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(60_500, 6_000))
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(62_500, 8_000))
        assertEquals(listOf(a), watch.onPoll(63_600, 9_100))
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(65_000, 10_500)) // once
    }

    @Test
    fun aRewindAfterThirtySecondsDoesNotCount() {
        val watch = RewindWatch()
        watch.onAutoSkipped(listOf(a), 0)
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(60_500, REWIND_WINDOW_MS + 1))
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(70_000, REWIND_WINDOW_MS + 12_000))
    }

    @Test
    fun leavingBeforeThreeSecondsStartsOver() {
        val watch = RewindWatch()
        watch.onAutoSkipped(listOf(a), 0)
        watch.onPoll(61_000, 5_000)
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(95_000, 6_000)) // left by skipping on
        watch.onPoll(61_000, 9_000)
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(63_000, 11_000))
        assertEquals(listOf(a), watch.onPoll(64_100, 12_100))
    }

    @Test
    fun anEnteredSegmentCountsEvenWhenThreeSecondsEndPastTheWindow() {
        val watch = RewindWatch()
        watch.onAutoSkipped(listOf(a), 0)
        watch.onPoll(61_000, 29_000)
        assertEquals(listOf(a), watch.onPoll(64_500, 32_500))
    }

    @Test
    fun aForgottenSegmentEarnsNothing() {
        val watch = RewindWatch()
        watch.onAutoSkipped(listOf(a), 0)
        watch.forget(listOf(a)) // it was undone, which counted already
        watch.onPoll(61_000, 1_000)
        assertEquals(emptyList<SkipSpan>(), watch.onPoll(65_000, 5_000))
    }

    @Test
    fun theNoticeBeatsEveryChipAndTheUndoBeatsTheSkip() {
        val undo = SkipChip(60_000, a, 0)
        val target = skipTargetAt(61_000, listOf(a))
        assertEquals(ChipChoice.Notice("x", "sponsor"), chooseChip(undo, target, notice = ChipChoice.Notice("x", "sponsor")))
    }
}
