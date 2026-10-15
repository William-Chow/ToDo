package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.advanceToOccurrence
import com.menu.my.todo.notification.rolledOntoDay
import com.menu.my.todo.ui.screens.isOverdue
import com.menu.my.todo.viewmodel.TodoCategory
import com.menu.my.todo.viewmodel.categoryFilter
import com.menu.my.todo.viewmodel.dayBounds
import com.menu.my.todo.viewmodel.withDone

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * A repeating task moving onto the occurrence whose day has started.
 *
 * It used to move only when its alarm fired, so a daily task with a 22:00 reminder spent every day
 * until 22:00 still naming yesterday — drawn as overdue in TODAY if it had not been ticked off, and
 * hidden in COMPLETED through the day it was next owed if it had.
 *
 * Monday 2026-08-31 in New York is "today" unless a test says otherwise.
 */
class RollOntoDayTest {

    private val today = newYork(2026, 8, 31)
    private val nowToday = newYork(2026, 8, 31, 10, 0)

    /** Due Sunday, reminder at 22:00, every day. */
    private val nightly = TodoItem(
        id = 1,
        title = "Take pills",
        description = "",
        dueDate = newYork(2026, 8, 30),
        reminderTime = newYork(2026, 8, 30, 22, 0),
        repeatType = RepeatType.DAILY
    )

    @Test
    fun aDailyTaskDueYesterdayMovesOntoToday() {
        val rolled = nightly.rolledOntoDay(today, NEW_YORK)!!

        assertEquals(newYork(2026, 8, 31), rolled.dueDate)
        assertEquals(newYork(2026, 8, 31, 22, 0), rolled.reminderTime)
    }

    @Test
    fun onceMovedItIsInTodayAndNotLate() {
        val rolled = nightly.rolledOntoDay(today, NEW_YORK)!!
        val (_, todayEnd) = dayBounds(nowToday, NEW_YORK)

        assertFalse(isOverdue(rolled.dueDate!!, nowToday, NEW_YORK))
        assertEquals(listOf(rolled), categoryFilter(listOf(rolled), TodoCategory.TODAY, todayEnd))
    }

    @Test
    fun aDailyTaskFinishedYesterdayIsOwedAgainToday() {
        val rolled = nightly.withDone(true, newYork(2026, 8, 30, 9, 0)).rolledOntoDay(today, NEW_YORK)!!

        assertFalse(rolled.isDone)
        assertNull(rolled.completedAt)
    }

    @Test
    fun aTaskAlreadyOnTodaysOccurrenceStaysPut() {
        val onToday = nightly.copy(dueDate = today, reminderTime = newYork(2026, 8, 31, 22, 0))

        assertNull(onToday.rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun aTaskFinishedTodayIsLeftFinished() {
        val onToday = nightly.copy(dueDate = today, reminderTime = newYork(2026, 8, 31, 22, 0))
            .withDone(true, nowToday)

        assertNull(onToday.rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun severalMissedDaysAreCaughtUpInOneStep() {
        val rolled = nightly.copy(
            dueDate = newYork(2026, 8, 27),
            reminderTime = newYork(2026, 8, 27, 22, 0)
        ).rolledOntoDay(today, NEW_YORK)!!

        assertEquals(today, rolled.dueDate)
        assertEquals(newYork(2026, 8, 31, 22, 0), rolled.reminderTime)
    }

    @Test
    fun aTaskAheadOfTodayStaysPut() {
        // An advance warning that reaches back past midnight fires the next occurrence's alarm the
        // evening before, and the alarm's roll moves the task onto tomorrow early.
        val ahead = nightly.copy(dueDate = newYork(2026, 9, 1), reminderTime = newYork(2026, 9, 1, 0, 10))

        assertNull(ahead.rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun aWeeklyTaskWaitsForItsOwnDay() {
        // Due last Wednesday: missed, and still owed until next Wednesday replaces it.
        val weekly = nightly.copy(
            dueDate = newYork(2026, 8, 26),
            reminderTime = newYork(2026, 8, 26, 22, 0),
            repeatType = RepeatType.WEEKLY
        )

        assertNull(weekly.rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun aWeeklyTaskMovesOnItsDay() {
        val weekly = nightly.copy(
            dueDate = newYork(2026, 8, 24),
            reminderTime = newYork(2026, 8, 24, 22, 0),
            repeatType = RepeatType.WEEKLY
        )

        assertEquals(today, weekly.rolledOntoDay(today, NEW_YORK)!!.dueDate)
    }

    @Test
    fun aWeeklyTaskMovesOnlyAsFarAsItsLatestOccurrence() {
        // Nine days behind: one week is owed, and that occurrence (Saturday) is itself still late.
        val weekly = nightly.copy(
            dueDate = newYork(2026, 8, 22),
            reminderTime = newYork(2026, 8, 22, 22, 0),
            repeatType = RepeatType.WEEKLY
        )

        assertEquals(newYork(2026, 8, 29), weekly.rolledOntoDay(today, NEW_YORK)!!.dueDate)
    }

    @Test
    fun aOneShotTaskNeverMoves() {
        assertNull(nightly.copy(repeatType = RepeatType.NONE).rolledOntoDay(today, NEW_YORK))
        assertNull(nightly.copy(repeatType = null).rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun anUndatedDailyTaskMovesItsReminder() {
        val undated = nightly.copy(dueDate = null, reminderTime = newYork(2026, 8, 30, 8, 0))

        val rolled = undated.rolledOntoDay(today, NEW_YORK)!!

        assertNull(rolled.dueDate)
        assertEquals(newYork(2026, 8, 31, 8, 0), rolled.reminderTime)
    }

    @Test
    fun aCorruptDueDateIsLeftAlone() {
        assertNull(nightly.copy(dueDate = Long.MIN_VALUE).rolledOntoDay(today, NEW_YORK))
    }

    @Test
    fun theSpringForwardDayKeepsTheTimeOfDay() {
        val beforeTheChange = nightly.copy(
            dueDate = newYork(2026, 3, 7),
            reminderTime = newYork(2026, 3, 7, 22, 0)
        )

        val rolled = beforeTheChange.rolledOntoDay(newYork(2026, 3, 8), NEW_YORK)!!

        assertEquals(newYork(2026, 3, 8), rolled.dueDate)
        assertEquals(newYork(2026, 3, 8, 22, 0), rolled.reminderTime)
    }

    @Test
    fun todaysAlarmDoesNotUndoACompletionMadeAfterTheMove() {
        // The alarm's own roll counts cycles from the stored reminder, so once the task is on today's
        // occurrence, today's alarm finds nothing to move — and a tick made since stays.
        val finished = nightly.rolledOntoDay(today, NEW_YORK)!!.withDone(true, nowToday)

        assertNull(finished.advanceToOccurrence(finished.reminderTime!!, NEW_YORK))
    }
}

private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

private fun newYork(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(NEW_YORK).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
