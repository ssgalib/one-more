package dev.galib.rotcounter

/** Pure counting logic: a reel counts once after [MIN_VIEW_MS] on screen; time builds up only between ticks. */
class ReelTracker(var count: Int = 0, var reelMs: Long = 0, var ads: Int = 0) {
    private val seen = HashSet<Int>()
    private var current = -1
    private var since = 0L
    private var lastTick = -1L

    fun onReel(index: Int, now: Long) {
        if (index != current) {
            current = index
            since = now
        }
    }

    /** Call about once a second while the Reels viewer is on screen. Returns true if a reel or ad was just counted. */
    fun tick(now: Long, isAd: Boolean = false): Boolean {
        if (current < 0) onReel(0, now) // the first reel shows before any scroll event
        if (lastTick >= 0) reelMs += (now - lastTick).coerceAtMost(MAX_GAP_MS)
        lastTick = now
        if (current !in seen && now - since >= MIN_VIEW_MS) {
            seen += current
            if (isAd) ads++ else count++ // ads keep their time in reelMs but stay out of the reel count
            return true
        }
        return false
    }

    /** Screen off or app hidden: stop the clock but remember what was seen. */
    fun pause() {
        lastTick = -1
    }

    /** Left the Reels viewer: the pager's indices restart next time. */
    fun leave() {
        seen.clear()
        current = -1
        lastTick = -1
    }

    fun reset() {
        leave()
        count = 0
        reelMs = 0
        ads = 0
    }

    companion object {
        const val MIN_VIEW_MS = 2_000L
        const val MAX_GAP_MS = 5_000L // a late tick (e.g. a stalled thread) shouldn't add a big chunk of time
    }
}
