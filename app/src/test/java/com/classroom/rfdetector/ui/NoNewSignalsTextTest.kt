package com.classroom.rfdetector.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoNewSignalsTextTest {
    @Test fun saysEverythingWasInTheEmptyRoomOnlyWhenNothingWeakIsNew() {
        assertEquals("지금 잡히는 전파는 모두 빈 교실 기록에 있던 것입니다.", noNewSignalsText(0))
    }

    @Test fun doesNotClaimEverythingWasInTheEmptyRoomWhenWeakNewSignalsExist() {
        val text = noNewSignalsText(3)
        assertFalse(text.contains("모두 빈 교실 기록에 있던"))
        assertTrue(text.contains("멀리서 약하게 잡히는 새 전파 3종"))
    }
}
