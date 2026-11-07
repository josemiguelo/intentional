package dev.josemiguel.intentional.core

/**
 * One app's time today on a device whose own record of it can vanish: Android
 * deletes its usage history when it boots with the clock in the past (a TV
 * without a clock battery boots in the past every time, until the network
 * sets the time), so the time it reports can drop to nothing mid-day.
 *
 * [seen] is what the system last reported this [boot]; [carried] is what
 * earlier boots reported before their history went. Within one boot the
 * system's number never goes backwards in any way that matters, and a boot
 * that keeps its history reports at least [seen] again, so only a boot that
 * comes back lower has lost it.
 */
data class AppTime(val seen: Long = 0L, val carried: Long = 0L, val boot: Int = 0) {

    /** Today's time: the history that went, plus what the system reports now. */
    val total: Long get() = carried + seen

    /** Takes the system's [reported] time in [boot]. */
    fun observe(reported: Long, boot: Int): AppTime =
        if (boot != this.boot && reported < seen) AppTime(reported, carried + seen, boot)
        else AppTime(reported, carried, boot)

    fun encode(): String = "$seen,$carried,$boot"

    companion object {
        fun decode(raw: String?): AppTime? = raw?.split(',')?.takeIf { it.size == 3 }?.let { (s, c, b) ->
            val seen = s.toLongOrNull() ?: return null
            val carried = c.toLongOrNull() ?: return null
            AppTime(seen, carried, b.toIntOrNull() ?: return null)
        }
    }
}
