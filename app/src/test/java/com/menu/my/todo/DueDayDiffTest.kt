package com.menu.my.todo

import com.menu.my.todo.ui.screens.dueDayDiff
import com.menu.my.todo.ui.screens.isOverdue

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * How many days off a task's due date is — what decides 今天 / 明天 / 昨天 on a row, and whether the
 * row is drawn as overdue.
 *
 * It used to divide the millis between two midnights by 24 hours. Across the 23-hour day daylight
 * saving starts on (2026-03-08 in New York) that came out at zero, so yesterday read as today and a
 * late task was not drawn as late.
 */
class DueDayDiffTest {

    @Test
    fun yesterdayIsYesterdayTheDayAfterSpringForward() {
        val now = newYork(2026, 3, 9, 10, 0)
        val due = newYork(2026, 3, 8)

        assertEquals(-1L, dueDayDiff(due, now, NEW_YORK))
        assertTrue(isOverdue(due, now, NEW_YORK))
    }

    @Test
    fun tomorrowIsTomorrowOnTheSpringForwardDay() {
        assertEquals(1L, dueDayDiff(newYork(2026, 3, 9), newYork(2026, 3, 8, 10, 0), NEW_YORK))
    }

    @Test
    fun twoDaysAcrossTheShortDayAreTwoDays() {
        assertEquals(2L, dueDayDiff(newYork(2026, 3, 9), newYork(2026, 3, 7, 10, 0), NEW_YORK))
    }

    @Test
    fun theFallBackDayCountsAsOneDayEitherWay() {
        assertEquals(1L, dueDayDiff(newYork(2026, 11, 2), newYork(2026, 11, 1, 10, 0), NEW_YORK))
        assertEquals(-1L, dueDayDiff(newYork(2026, 11, 1), newYork(2026, 11, 2, 10, 0), NEW_YORK))
    }

    @Test
    fun aRowReadsItsDateAgainstTheDayTheListIsDrawnFor() {
        // Rows are handed the midnight that starts the list's day rather than the time now. Read
        // against the next day's midnight — the list after the day has turned over — what was
        // tomorrow is today, and what was today is yesterday.
        val due = newYork(2026, 3, 9)

        assertEquals(1L, dueDayDiff(due, newYork(2026, 3, 8), NEW_YORK))
        assertEquals(0L, dueDayDiff(due, newYork(2026, 3, 9), NEW_YORK))
        assertEquals(-1L, dueDayDiff(due, newYork(2026, 3, 10), NEW_YORK))
    }

    @Test
    fun aTaskDueTodayIsNotOverdueLateAtNight() {
        val due = newYork(2026, 3, 8)
        val now = newYork(2026, 3, 8, 23, 59)

        assertEquals(0L, dueDayDiff(due, now, NEW_YORK))
        assertFalse(isOverdue(due, now, NEW_YORK))
    }
}

private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

private fun newYork(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(NEW_YORK).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
