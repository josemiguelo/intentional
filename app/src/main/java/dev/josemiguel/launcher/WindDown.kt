package dev.josemiguel.launcher

import java.time.LocalTime

/**
 * The wind-down window: home shrinks to essential apps and considered-app
 * friction doubles. Sleep displacement is the best-supported causal pathway
 * from phone use to harm, so this window is first-class. Schedule lives in
 * [Prefs.windDownStart] / [Prefs.windDownEnd].
 */
object WindDown {
    fun isActive(start: LocalTime, end: LocalTime, now: LocalTime = LocalTime.now()): Boolean =
        if (start > end) now >= start || now < end
        else now >= start && now < end
}
