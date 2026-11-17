package com.classroom.rfdetector.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class RssiFilterTest {
    @Test fun emaStartsFromFirstSampleWhenThereIsNoPreviousValue() {
        assertEquals(-70.0, RssiFilter.ema(null, -70), 0.0)
    }

    @Test fun emaMovesQuarterOfTheWayByDefaultAndHonorsAlpha() {
        assertEquals(-70.0 + 0.25 * 10, RssiFilter.ema(-70.0, -60), 1e-9)
        assertEquals(-65.0, RssiFilter.ema(-70.0, -60, alpha = 0.5), 1e-9)
        assertEquals(-70.0, RssiFilter.ema(-70.0, -60, alpha = 0.0), 1e-9)
        assertEquals(-60.0, RssiFilter.ema(-70.0, -60, alpha = 1.0), 1e-9)
    }

    @Test fun emaKeepsValueWhenSampleEqualsPrevious() {
        assertEquals(-72.5, RssiFilter.ema(-72.5, -72), 0.2)
        assertEquals(-60.0, RssiFilter.ema(-60.0, -60), 1e-9)
    }

    @Test fun distanceIsOneMeterAtReferenceStrength() {
        assertEquals(1.0, RssiFilter.distanceM(RssiFilter.DEFAULT_TX_DBM), 1e-9)
        assertEquals(1.0, RssiFilter.distanceM(-70.0, txDbm = -70.0), 1e-9)
    }

    @Test fun distanceFollowsLogDistanceModelAndGrowsAsSignalWeakens() {
        // 10 * n dB below the reference is a factor of 10 in distance.
        assertEquals(10.0, RssiFilter.distanceM(RssiFilter.DEFAULT_TX_DBM - 10 * RssiFilter.PATH_N), 1e-9)
        assertEquals(0.1, RssiFilter.distanceM(RssiFilter.DEFAULT_TX_DBM + 10 * RssiFilter.PATH_N), 1e-9)
        assertTrue(RssiFilter.distanceM(-80.0) > RssiFilter.distanceM(-60.0))
    }

    @Test fun classifyBoundariesAreLowerInclusive() {
        assertEquals(DistanceClass.UNKNOWN, RssiFilter.classify(null))
        assertEquals(DistanceClass.VERY_CLOSE, RssiFilter.classify(0.0))
        assertEquals(DistanceClass.VERY_CLOSE, RssiFilter.classify(1.5999))
        assertEquals(DistanceClass.CLOSE, RssiFilter.classify(1.6))
        assertEquals(DistanceClass.CLOSE, RssiFilter.classify(3.1999))
        assertEquals(DistanceClass.NEAR, RssiFilter.classify(3.2))
        assertEquals(DistanceClass.NEAR, RssiFilter.classify(7.9999))
        assertEquals(DistanceClass.FAR, RssiFilter.classify(8.0))
        assertEquals(DistanceClass.FAR, RssiFilter.classify(50.0))
    }

    @Test fun classifyFromRssiMatchesBoundariesEitherSide() {
        fun rssiAt(meters: Double) = RssiFilter.DEFAULT_TX_DBM - 10 * RssiFilter.PATH_N * log10(meters)
        assertEquals(DistanceClass.VERY_CLOSE, RssiFilter.classify(RssiFilter.distanceM(rssiAt(1.59))))
        assertEquals(DistanceClass.CLOSE, RssiFilter.classify(RssiFilter.distanceM(rssiAt(1.61))))
        assertEquals(DistanceClass.CLOSE, RssiFilter.classify(RssiFilter.distanceM(rssiAt(3.19))))
        assertEquals(DistanceClass.NEAR, RssiFilter.classify(RssiFilter.distanceM(rssiAt(3.21))))
        assertEquals(DistanceClass.NEAR, RssiFilter.classify(RssiFilter.distanceM(rssiAt(7.9))))
        assertEquals(DistanceClass.FAR, RssiFilter.classify(RssiFilter.distanceM(rssiAt(8.1))))
    }
}
