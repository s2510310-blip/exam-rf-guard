package com.classroom.rfdetector.ui

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedAtTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance(seoul).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun todayShowsOnlyTheTime() {
        assertEquals("09:12", savedAt(at(2026, 11, 17, 9, 12), at(2026, 11, 17, 14, 0), seoul))
    }

    @Test fun anEarlierDayShowsTheDateSoItIsNotMistakenForToday() {
        assertEquals("11월 16일 09:12", savedAt(at(2026, 11, 16, 9, 12), at(2026, 11, 17, 9, 0), seoul))
    }

    @Test fun justBeforeMidnightIsYesterday() {
        assertEquals("11월 16일 23:59", savedAt(at(2026, 11, 16, 23, 59), at(2026, 11, 17, 0, 1), seoul))
    }

    @Test fun anotherYearShowsTheYear() {
        assertEquals("2025년 11월 17일 09:12", savedAt(at(2025, 11, 17, 9, 12), at(2026, 11, 17, 9, 12), seoul))
    }
}
