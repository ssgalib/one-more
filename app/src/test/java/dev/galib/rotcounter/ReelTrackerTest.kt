package dev.galib.rotcounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelTrackerTest {
    private val t = ReelTracker()

    @Test fun underTwoSecondsDoesNotCount() {
        t.onReel(0, 0)
        assertFalse(t.tick(1_000))
        t.onReel(1, 1_500) // swiped away early
        assertFalse(t.tick(2_500))
        assertEquals(0, t.count)
    }

    @Test fun firstReelCountsWithoutScrollEvent() {
        t.tick(0)
        assertFalse(t.tick(1_000))
        assertTrue(t.tick(2_000))
        assertEquals(1, t.count)
    }

    @Test fun countsOnceAndScrollingBackDoesNotDoubleCount() {
        t.onReel(0, 0)
        t.tick(2_000); t.tick(3_000)
        t.onReel(1, 3_000); t.tick(5_000)
        t.onReel(0, 5_000); t.tick(8_000) // back to reel 0
        assertEquals(2, t.count)
    }

    @Test fun leavingResetsSeenIndices() {
        t.onReel(0, 0); t.tick(2_000)
        t.leave()
        t.onReel(0, 10_000); t.tick(12_000) // new Reels session, index 0 is a different reel
        assertEquals(2, t.count)
    }

    @Test fun timeBuildsOnlyBetweenTicksAndPauseStopsClock() {
        t.tick(0); t.tick(1_000); t.tick(2_000)
        assertEquals(2_000, t.reelMs)
        t.pause()
        t.tick(60_000) // screen was off: no time added
        assertEquals(2_000, t.reelMs)
        t.tick(61_000)
        assertEquals(3_000, t.reelMs)
    }

    @Test fun lateTickIsCapped() {
        t.tick(0); t.tick(30_000)
        assertEquals(ReelTracker.MAX_GAP_MS, t.reelMs)
    }

    @Test fun adsCountSeparatelyButKeepTheirTime() {
        t.tick(0, isAd = true); t.tick(1_000, isAd = true); t.tick(2_000, isAd = true)
        assertEquals(0, t.count)
        assertEquals(1, t.ads)
        assertEquals(2_000, t.reelMs)
    }

    @Test fun formatsMinutesAndHours() {
        assertEquals("18m", Stats.minutes(18 * 60_000L + 59_000))
        assertEquals("1h 05m", Stats.minutes(65 * 60_000L))
    }
}
