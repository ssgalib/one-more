package dev.galib.rotcounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun day(daysAgo: Long, reels: Int, ms: Long = 0, ads: Int = 0) =
        today.minusDays(daysAgo).let { it to Day(it, reels, ms, ads) }

    @Test fun emptyHistory() {
        val s = History.summary(emptyMap(), today, 7)
        assertEquals(7, s.days.size)
        assertEquals(0, s.reels)
        assertNull(s.dailyAvg)
        assertNull(s.deltaPct)
        assertNull(s.worst)
    }

    @Test fun totalsAndWorstDayOverTheRun() {
        val s = History.summary(mapOf(day(0, 10, 60_000, 2), day(3, 30, 120_000, 1), day(7, 99)), today, 7)
        assertEquals(40, s.reels) // the 99 is 7 days ago: outside a 7-day run
        assertEquals(180_000, s.ms)
        assertEquals(3, s.ads)
        assertEquals(today.minusDays(3), s.worst!!.date)
        assertEquals(today, s.days.last().date)
    }

    @Test fun averageSkipsDaysBeforeTheFirstRecord() {
        // installed 2 days ago: 3 tracked days, not 7
        val s = History.summary(mapOf(day(2, 30), day(1, 0), day(0, 15)), today, 7)
        assertEquals(15, s.dailyAvg)
    }

    @Test fun deltaAgainstThePreviousRun() {
        val records = mapOf(day(0, 90), day(7, 100))
        assertEquals(-10, History.summary(records, today, 7).deltaPct)
        assertEquals(100, History.summary(records, today, 7).previousReels)
        assertNull("nothing before today", History.summary(mapOf(day(0, 5)), today, 7).deltaPct)
    }

    @Test fun encodeRoundTripsAndRejectsJunk() {
        val d = Day(today, 42, 1_080_000, 13)
        assertEquals(d, History.decode(today, History.encode(d)))
        assertNull(History.decode(today, "42,abc,1"))
        assertNull(History.decode(today, "42"))
    }
}
