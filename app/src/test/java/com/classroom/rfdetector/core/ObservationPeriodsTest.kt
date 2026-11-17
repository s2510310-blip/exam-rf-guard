package com.classroom.rfdetector.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationPeriodsTest {
    private fun presence(source: ScanSource, ageMs: Long) = signalPresence(source, 100_000L, 100_000L + ageMs)

    @Test fun historyGapDiffersBySource() {
        assertEquals(90_000L, ScanSource.WIFI.historyGapMs())
        assertEquals(15_000L, ScanSource.BLE.historyGapMs())
    }

    @Test fun wifiPresenceThresholds() {
        assertEquals(SignalPresence.RECENT, presence(ScanSource.WIFI, 0))
        assertEquals(SignalPresence.RECENT, presence(ScanSource.WIFI, 45_000))
        assertEquals(SignalPresence.QUIET, presence(ScanSource.WIFI, 45_001))
        assertEquals(SignalPresence.QUIET, presence(ScanSource.WIFI, 90_000))
        assertEquals(SignalPresence.HISTORY, presence(ScanSource.WIFI, 90_001))
    }

    @Test fun blePresenceThresholds() {
        assertEquals(SignalPresence.RECENT, presence(ScanSource.BLE, 0))
        assertEquals(SignalPresence.RECENT, presence(ScanSource.BLE, 5_000))
        assertEquals(SignalPresence.QUIET, presence(ScanSource.BLE, 5_001))
        assertEquals(SignalPresence.QUIET, presence(ScanSource.BLE, 15_000))
        assertEquals(SignalPresence.HISTORY, presence(ScanSource.BLE, 15_001))
    }

    @Test fun lastSeenInTheFutureCountsAsRecent() {
        assertEquals(SignalPresence.RECENT, signalPresence(ScanSource.BLE, 200_000L, 100_000L))
    }

    @Test fun firstSampleCreatesSinglePeriod() {
        assertEquals(listOf(ObservationPeriod(1_000, 1_000, 1)), addToPeriods(emptyList(), 1_000, 15_000))
    }

    @Test fun sampleWithinGapExtendsPeriodAndGapEdgeIsInclusive() {
        val first = addToPeriods(emptyList(), 0, 15_000)
        assertEquals(listOf(ObservationPeriod(0, 15_000, 2)), addToPeriods(first, 15_000, 15_000))
    }

    @Test fun sampleBeyondGapStartsNewPeriod() {
        val first = addToPeriods(emptyList(), 0, 15_000)
        assertEquals(
            listOf(ObservationPeriod(0, 0, 1), ObservationPeriod(15_001, 15_001, 1)),
            addToPeriods(first, 15_001, 15_000),
        )
    }

    @Test fun lateSampleInsideExistingPeriodOnlyIncreasesCount() {
        val periods = listOf(ObservationPeriod(0, 10_000, 3))
        assertEquals(listOf(ObservationPeriod(0, 10_000, 4)), addToPeriods(periods, 5_000, 15_000))
    }

    @Test fun lateSampleBeforeFirstPeriodExtendsItBackwardsWhenWithinGap() {
        val periods = listOf(ObservationPeriod(50_000, 60_000, 2))
        assertEquals(listOf(ObservationPeriod(40_000, 60_000, 3)), addToPeriods(periods, 40_000, 15_000))
    }

    @Test fun lateSampleBridgingTwoGapsMergesThreePeriodsIntoOne() {
        val periods = listOf(ObservationPeriod(0, 1_000, 2), ObservationPeriod(20_000, 21_000, 3))
        // 10_000 is within 15 s of both neighbours, so the two periods become one.
        assertEquals(listOf(ObservationPeriod(0, 21_000, 6)), addToPeriods(periods, 10_000, 15_000))
    }

    @Test fun lateSampleBridgingOnlyOneSideLeavesOtherPeriodSeparate() {
        val periods = listOf(ObservationPeriod(0, 1_000, 2), ObservationPeriod(60_000, 61_000, 3))
        assertEquals(
            listOf(ObservationPeriod(0, 1_000, 2), ObservationPeriod(50_000, 61_000, 4)),
            addToPeriods(periods, 50_000, 15_000),
        )
    }

    @Test fun unsortedInputIsReturnedSortedAndMerged() {
        val periods = listOf(ObservationPeriod(60_000, 61_000, 1), ObservationPeriod(0, 1_000, 1))
        assertEquals(
            listOf(ObservationPeriod(0, 1_000, 1), ObservationPeriod(60_000, 61_000, 1), ObservationPeriod(120_000, 120_000, 1)),
            addToPeriods(periods, 120_000, 15_000),
        )
    }
}
