package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.notification.reminderTrigger
import com.menu.my.todo.ui.screens.pickedReminderTime
import com.menu.my.todo.ui.screens.undatedReminderForAdvance

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * Which moment a reminder time picked in the editor stands for.
 *
 * A task with no due date takes the next time the clock reads what was picked, and that used to mean
 * the next time the *reminder* was still to come. The alarm goes off the advance warning earlier,
 * though, and the scheduler drops a one-shot alarm that is already behind it — so 09:10 picked at
 * 09:00 with a 30-minute warning stayed on today and never fired.
 *
 * Sunday 2026-08-30, 09:00 UTC is "now" unless a test says otherwise.
 */
class PickedReminderTimeTest {

    private val now = utc(2026, 8, 30, 9, 0)

    @Test
    fun anUndatedTimeStillToComeIsToday() {
        assertEquals(utc(2026, 8, 30, 10, 0), pick(10, 0))
    }

    @Test
    fun anUndatedTimeThatHasGoneByIsTomorrow() {
        assertEquals(utc(2026, 8, 31, 8, 0), pick(8, 0))
    }

    @Test
    fun anUndatedTimeWhoseAlarmHasGoneByIsTomorrow() {
        val picked = pick(9, 10, advance = 30)

        assertEquals(utc(2026, 8, 31, 9, 10), picked)
        assertNotNull(reminderTrigger(picked, 30, RepeatType.NONE, now, UTC))
    }

    @Test
    fun anUndatedTimeWhoseAlarmIsStillToComeIsToday() {
        assertEquals(utc(2026, 8, 30, 9, 40), pick(9, 40, advance = 30))
    }

    @Test
    fun thisVeryMinuteStillCounts() {
        // The scheduler keeps an alarm due this instant, so the picker does too.
        assertEquals(now, pick(9, 0))
    }

    @Test
    fun aFullDaysWarningCanTakeItTwoDaysOut() {
        // Tomorrow's 08:50 has its alarm at 08:50 today, which has also gone by.
        assertEquals(utc(2026, 9, 1, 8, 50), pick(8, 50, advance = 24 * 60))
    }

    @Test
    fun anUndatedReminderIsNeverOneTheSchedulerDrops() {
        for (hour in 0..23) for (minute in listOf(0, 59)) for (advance in listOf(0, 1, 30, 61, 600, 1439, 1440)) {
            val picked = pick(hour, minute, advance = advance)
            assertNotNull(
                "$hour:$minute with $advance minutes' warning",
                reminderTrigger(picked, advance, RepeatType.NONE, now, UTC)
            )
        }
    }

    @Test
    fun aDatedTimeStaysOnItsDateEvenIfItHasGoneBy() {
        assertEquals(utc(2026, 8, 30, 8, 0), pick(8, 0, due = utc(2026, 8, 30)))
    }

    @Test
    fun aDatedTimeIsOnItsDate() {
        assertEquals(utc(2026, 9, 2, 10, 0), pick(10, 0, due = utc(2026, 9, 2)))
    }

    @Test
    fun rollingOverTheSpringForwardNightKeepsTheClockTime() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val lateSaturday = at(newYork, 2026, 3, 7, 23, 0)

        val picked = pickedReminderTime(22, 0, null, 0, lateSaturday, newYork)

        assertEquals(at(newYork, 2026, 3, 8, 22, 0), picked)
    }

    // ---- When the warning changes after the time was picked ----------------

    @Test
    fun aWarningThatPutsTodaysAlarmBehindUsMovesTheReminderToTomorrow() {
        // The usual order: a time first, the warning after.
        assertEquals(
            utc(2026, 8, 31, 9, 10),
            undatedReminderForAdvance(utc(2026, 8, 30, 9, 10), 30, now, UTC)
        )
    }

    @Test
    fun aWarningThatLeavesTheAlarmAheadLeavesTheReminderAlone() {
        // Even days out — an undated reminder can sit on any day once its due date is cleared.
        val friday = utc(2026, 9, 4, 9, 0)

        assertEquals(friday, undatedReminderForAdvance(friday, 30, now, UTC))
    }

    @Test
    fun aReminderLongGoneIsPickedAgainFromItsClockTime() {
        assertEquals(
            utc(2026, 8, 31, 7, 0),
            undatedReminderForAdvance(utc(2026, 8, 20, 7, 0), 10, now, UTC)
        )
    }

    private fun pick(hour: Int, minute: Int, advance: Int = 0, due: Long? = null) =
        pickedReminderTime(hour, minute, due, advance, now, UTC)
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    at(UTC, year, month, day, hour, minute)

private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
