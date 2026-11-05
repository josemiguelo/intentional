package dev.josemiguel.intentional.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HyprlandTest {

    @Test
    fun aLockOnAnyMonitorIsALock() {
        assertTrue(monitorsLocked("""[{"name":"eDP-1","solitaryBlockedBy":["LOCK"]},{"name":"DP-1","solitaryBlockedBy":[]}]"""))
    }

    @Test
    fun otherBlockersAreNot() {
        // What this machine reports unlocked.
        assertFalse(monitorsLocked("""[{"name":"eDP-1","solitaryBlockedBy":["WINDOWED","CANDIDATE"]}]"""))
        assertFalse(monitorsLocked("""[{"name":"eDP-1"}]"""))
    }

    @Test
    fun anUnreadableAnswerIsNotALock() {
        assertFalse(monitorsLocked("error: no such command"))
        assertFalse(monitorsLocked("""{"not":"an array"}"""))
    }
}
