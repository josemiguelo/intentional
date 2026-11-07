package dev.josemiguel.intentional.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppTimeTest {

    @Test
    fun aBootThatWipedTheHistoryKeepsWhatWasSeen() {
        val before = AppTime().observe(10, boot = 1).observe(40, boot = 1)
        val after = before.observe(2, boot = 2)
        assertEquals(42, after.total)
        assertEquals(55, after.observe(15, boot = 2).total)
        // And again the next time it's switched on.
        assertEquals(60, after.observe(15, boot = 2).observe(5, boot = 3).total)
    }

    @Test
    fun aBootThatKeptTheHistoryIsNotCountedTwice() {
        val phone = AppTime().observe(40, boot = 1).observe(45, boot = 2)
        assertEquals(45, phone.total)
    }

    @Test
    fun withinABootANumberThatDipsIsNotAWipe() {
        // An event reported late can shorten the open session a little.
        assertEquals(39, AppTime().observe(40, boot = 1).observe(39, boot = 1).total)
    }

    @Test
    fun encodes() {
        val t = AppTime(5, 7, 3)
        assertEquals(t, AppTime.decode(t.encode()))
        assertNull(AppTime.decode("nonsense"))
        assertNull(AppTime.decode(null))
    }
}
