package dev.josemiguel.intentional.core

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindDownTest {

    @Test
    fun overnightWindowWrapsMidnight() {
        val start = LocalTime(22, 0)
        val end = LocalTime(6, 0)
        assertTrue(WindDown.isActive(start, end, LocalTime(22, 0)))
        assertTrue(WindDown.isActive(start, end, LocalTime(2, 30)))
        assertFalse(WindDown.isActive(start, end, LocalTime(6, 0)))
        assertFalse(WindDown.isActive(start, end, LocalTime(12, 0)))
    }

    @Test
    fun sameDayWindow() {
        val start = LocalTime(13, 0)
        val end = LocalTime(14, 0)
        assertTrue(WindDown.isActive(start, end, LocalTime(13, 30)))
        assertFalse(WindDown.isActive(start, end, LocalTime(14, 0)))
        assertFalse(WindDown.isActive(start, end, LocalTime(9, 0)))
    }
}
