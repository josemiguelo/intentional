package dev.josemiguel.intentional.core

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The wind-down window: home shrinks to essential apps and considered-app
 * friction doubles. Sleep displacement is the best-supported causal pathway
 * from phone use to harm, so this window is first-class. A window whose start
 * is after its end runs overnight.
 */
object WindDown {
    fun isActive(start: LocalTime, end: LocalTime, now: LocalTime = localTimeNow()): Boolean =
        if (start > end) now >= start || now < end
        else now >= start && now < end

    private fun localTimeNow() =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
}
