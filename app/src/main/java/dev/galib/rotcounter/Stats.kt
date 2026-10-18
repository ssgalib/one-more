package dev.galib.rotcounter

import android.content.Context
import java.time.LocalDate

/** Today's numbers in SharedPreferences; anything stored under another date reads as zero. */
object Stats {
    private const val PREFS = "stats"

    fun today(): String = LocalDate.now().toString()

    fun load(ctx: Context): ReelTracker {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("day", null) != today()) return ReelTracker()
        return ReelTracker(p.getInt("count", 0), p.getLong("ms", 0L), p.getInt("ads", 0))
    }

    fun save(ctx: Context, day: String, t: ReelTracker) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("day", day).putInt("count", t.count).putLong("ms", t.reelMs).putInt("ads", t.ads).apply()
    }

    fun loadBubble(ctx: Context): Pair<Int, Int>? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("bx")) return null
        return p.getInt("bx", 0) to p.getInt("by", 0)
    }

    fun saveBubble(ctx: Context, x: Int, y: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("bx", x).putInt("by", y).apply()
    }

    fun minutes(ms: Long): String {
        val m = ms / 60_000
        return if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
    }
}
