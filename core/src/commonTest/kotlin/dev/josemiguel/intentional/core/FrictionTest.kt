package dev.josemiguel.intentional.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FrictionTest {

    @Test
    fun cyclesThroughEveryRung() {
        assertEquals(Friction.LONG_PAUSE, Friction.PAUSE.next())
        assertEquals(Friction.PAUSE, Friction.NIGHT_LOCK.next())
    }

    @Test
    fun storedOrdinalsOutOfRangeClamp() {
        assertEquals(Friction.PAUSE, Friction.of(-1))
        assertEquals(Friction.NIGHT_LOCK, Friction.of(99))
    }
}
