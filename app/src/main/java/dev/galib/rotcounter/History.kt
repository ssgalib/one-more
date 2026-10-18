package dev.galib.rotcounter

import java.time.LocalDate

/** One stored day. */
data class Day(val date: LocalDate, val reels: Int = 0, val ms: Long = 0, val ads: Int = 0)

/** Totals for a run of days ending today, plus how it compares with the run just before it. */
data class Summary(
    val days: List<Day>,         // oldest first, today last; days with no record are zeros
    val reels: Int,
    val ms: Long,
    val ads: Int,
    val dailyAvg: Int?,          // over tracked days only; null until something is tracked
    val deltaPct: Int?,          // vs the previous run; null when that run has no data
    val previousReels: Int,
    val worst: Day?,             // the day with the most reels; null if all zero
)

object History {
    fun summary(records: Map<LocalDate, Day>, today: LocalDate, length: Int): Summary {
        val days = (length - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> records[d] ?: Day(d) } }
        val previous = (2 * length - 1 downTo length).map { today.minusDays(it.toLong()) }
        val reels = days.sumOf { it.reels }
        val previousReels = previous.sumOf { records[it]?.reels ?: 0 }
        val hasPrevious = previous.any { it in records }
        // Days before the first record (before the app was installed) would drag the average down.
        val first = records.keys.minOrNull()
        val tracked = if (first == null) 0 else days.count { !it.date.isBefore(first) }
        return Summary(
            days = days,
            reels = reels,
            ms = days.sumOf { it.ms },
            ads = days.sumOf { it.ads },
            dailyAvg = if (tracked == 0) null else Math.round(reels.toDouble() / tracked).toInt(),
            deltaPct = if (!hasPrevious || previousReels == 0) null
                else Math.round((reels - previousReels) * 100.0 / previousReels).toInt(),
            previousReels = previousReels,
            worst = days.filter { it.reels > 0 }.maxByOrNull { it.reels },
        )
    }

    /** "count,ms,ads" — the stored form of a day. */
    fun encode(d: Day) = "${d.reels},${d.ms},${d.ads}"

    fun decode(date: LocalDate, s: String): Day? {
        val p = s.split(',')
        if (p.size != 3) return null
        return Day(date, p[0].toIntOrNull() ?: return null, p[1].toLongOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
    }
}
