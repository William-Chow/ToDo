package com.menu.my.todo

import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.viewmodel.TodoCategory
import com.menu.my.todo.viewmodel.categoryFilter
import com.menu.my.todo.viewmodel.dayBounds
import com.menu.my.todo.viewmodel.millisUntilNextDay

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * Where today starts and stops, which is what splits TODAY from UPCOMING.
 *
 * US daylight saving starts on 2026-03-08, a day 23 hours long, and ends on 2026-11-01, a day of
 * 25. Today used to end 24 hours after it started, which on the short day is an hour into
 * tomorrow — far enough to take in every task due tomorrow, since a due date is stored as the
 * midnight that starts its day.
 */
class DayBoundsTest {

    @Test
    fun anOrdinaryDayRunsMidnightToMidnight() {
        val (start, end) = dayBounds(newYork(2026, 3, 4, 15, 0), NEW_YORK)

        assertEquals(newYork(2026, 3, 4), start)
        assertEquals(newYork(2026, 3, 5), end)
    }

    @Test
    fun theSpringForwardDayEndsAtTomorrowsMidnight() {
        val (start, end) = dayBounds(newYork(2026, 3, 8, 15, 0), NEW_YORK)

        assertEquals(newYork(2026, 3, 8), start)
        assertEquals(newYork(2026, 3, 9), end)
        assertEquals(23 * HOUR, end - start)
    }

    @Test
    fun theFallBackDayEndsAtTomorrowsMidnight() {
        val (start, end) = dayBounds(newYork(2026, 11, 1, 15, 0), NEW_YORK)

        assertEquals(newYork(2026, 11, 1), start)
        assertEquals(newYork(2026, 11, 2), end)
        assertEquals(25 * HOUR, end - start)
    }

    @Test
    fun theLastMinuteOfTheDayIsStillThatDay() {
        val (start, _) = dayBounds(newYork(2026, 3, 8, 23, 59), NEW_YORK)

        assertEquals(newYork(2026, 3, 8), start)
    }

    @Test
    fun aDaysOwnMidnightIsThatDaysStart() {
        // The ViewModel keeps the day as its midnight and asks for that day's bounds again from it,
        // so a midnight has to come back as itself — on the short day too.
        for (day in listOf(newYork(2026, 3, 4), newYork(2026, 3, 8), newYork(2026, 11, 1))) {
            assertEquals(day, dayBounds(day, NEW_YORK).first)
        }
    }

    // ---- Waiting for the day to turn over while the app is open -------------

    @Test
    fun theWaitRunsToTheNextMidnight() {
        assertEquals(9 * HOUR, millisUntilNextDay(newYork(2026, 3, 4, 15, 0), NEW_YORK))
    }

    @Test
    fun theWaitOnTheSpringForwardDayIsAnHourShorter() {
        // From the day's first minute the wait is the rest of a 23-hour day. A fixed 24-hour wait
        // would wake an hour into tomorrow.
        assertEquals(23 * HOUR - MINUTE, millisUntilNextDay(newYork(2026, 3, 8, 0, 1), NEW_YORK))
    }

    @Test
    fun theWaitIsNeverZero() {
        val lastMillisecond = newYork(2026, 3, 5) - 1

        assertEquals(1L, millisUntilNextDay(lastMillisecond, NEW_YORK))
        assertEquals(DAY, millisUntilNextDay(newYork(2026, 3, 5), NEW_YORK))
    }

    @Test
    fun aTaskDueTomorrowIsUpcomingOnTheSpringForwardDay() {
        val (_, todayEnd) = dayBounds(newYork(2026, 3, 8, 10, 0), NEW_YORK)
        val tomorrow = TodoItem(id = 1, title = "task", description = "", dueDate = newYork(2026, 3, 9))
        val list = listOf(tomorrow)

        assertEquals(listOf(tomorrow), categoryFilter(list, TodoCategory.UPCOMING, todayEnd))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.TODAY, todayEnd))
    }
}

private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

private const val MINUTE = 60L * 1000
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

private fun newYork(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(NEW_YORK).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
