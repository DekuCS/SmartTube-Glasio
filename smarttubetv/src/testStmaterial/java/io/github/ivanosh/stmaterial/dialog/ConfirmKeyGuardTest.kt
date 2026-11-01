package io.github.ivanosh.stmaterial.dialog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmKeyGuardTest {
    @Test
    fun aKeyHeldSinceBeforeTheWindowOpenedIsDroppedUpToItsRelease() {
        val guard = ConfirmKeyGuard()
        assertTrue(guard.shouldDrop(isDown = true, repeatCount = 12))
        assertTrue(guard.shouldDrop(isDown = true, repeatCount = 13))
        assertTrue(guard.shouldDrop(isDown = false, repeatCount = 0))
    }

    @Test
    fun theNextPressPassesWhole() {
        val guard = ConfirmKeyGuard()
        guard.shouldDrop(isDown = false, repeatCount = 0)
        assertFalse(guard.shouldDrop(isDown = true, repeatCount = 0))
        assertFalse(guard.shouldDrop(isDown = false, repeatCount = 0))
    }

    @Test
    fun aPressThatStartedHereMayRepeat() {
        val guard = ConfirmKeyGuard()
        assertFalse(guard.shouldDrop(isDown = true, repeatCount = 0))
        assertFalse(guard.shouldDrop(isDown = true, repeatCount = 1))
        assertFalse(guard.shouldDrop(isDown = false, repeatCount = 0))
    }
}
