package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class GeigerPacerTest {
    @Test fun keepsEachClassPeriod() {
        val pacer = GeigerPacer()
        assertTrue(pacer.due(DistanceClass.FAR, 0))
        assertFalse(pacer.due(DistanceClass.FAR, 2_950))
        assertTrue(pacer.due(DistanceClass.FAR, 3_000))
        assertTrue(pacer.due(DistanceClass.VERY_CLOSE, 3_350))
        assertFalse(pacer.due(DistanceClass.VERY_CLOSE, 3_600))
    }

    @Test fun walkingCloserShortensTheWaitImmediately() {
        val pacer = GeigerPacer()
        assertTrue(pacer.due(DistanceClass.FAR, 0))
        // 멀다(3초)에서 가까워져 바로 옆(350ms)이 되면 3초를 다 기다리지 않는다.
        assertFalse(pacer.due(DistanceClass.VERY_CLOSE, 300))
        assertTrue(pacer.due(DistanceClass.VERY_CLOSE, 350))
    }

    @Test fun unknownStaysSilentAndFirstPulseComesAtOnceWhenTheSignalReturns() {
        val pacer = GeigerPacer()
        assertTrue(pacer.due(DistanceClass.CLOSE, 0))
        assertFalse(pacer.due(DistanceClass.UNKNOWN, 5_000))
        assertTrue(pacer.due(DistanceClass.CLOSE, 5_050))
    }
}
