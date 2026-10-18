package dev.galib.rotcounter

import android.content.Context
import java.time.LocalDate

/** Per-day history in SharedPreferences ("history": ISO date -> "count,ms,ads"); bubble position in "stats". */
object Stats {
    private const val PREFS = "stats"
    private const val HISTORY = "history"

    fun today(): String = LocalDate.now().toString()

    fun load(ctx: Context): ReelTracker {
        migrate(ctx)
        val d = all(ctx)[LocalDate.now()] ?: return ReelTracker()
        return ReelTracker(d.reels, d.ms, d.ads)
    }

    fun save(ctx: Context, day: String, t: ReelTracker) {
        ctx.getSharedPreferences(HISTORY, Context.MODE_PRIVATE).edit()
            .putString(day, History.encode(Day(LocalDate.parse(day), t.count, t.reelMs, t.ads))).apply()
    }

    fun all(ctx: Context): Map<LocalDate, Day> =
        ctx.getSharedPreferences(HISTORY, Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@mapNotNull null
            History.decode(date, v as? String ?: return@mapNotNull null)
        }.associateBy { it.date }

    /** v1.0/1.1 kept only today under "stats"; move it into history once. */
    private fun migrate(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = p.getString("day", null) ?: return
        val h = ctx.getSharedPreferences(HISTORY, Context.MODE_PRIVATE)
        if (!h.contains(day)) {
            h.edit().putString(day, History.encode(Day(LocalDate.parse(day), p.getInt("count", 0), p.getLong("ms", 0L), p.getInt("ads", 0)))).commit()
        }
        p.edit().remove("day").remove("count").remove("ms").remove("ads").apply()
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
