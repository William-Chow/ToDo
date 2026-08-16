package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.advanceToOccurrence
import com.menu.my.todo.notification.occurrenceAfter
import com.menu.my.todo.notification.occurrenceAtOrAfter

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * Covers the arithmetic behind repeating reminders: when an alarm fires, the task has to land on the
 * occurrence that alarm was for, with completion cleared.
 *
 * Every case names its own time zone, so the results do not depend on where the build runs. UTC is
 * the plain case (all days are 24 hours); [ReminderDstTest] covers the days that are not.
 */
class ReminderOccurrenceTest {
    private val dailyAtEight = TodoItem(
        id = 1,
        title = "Take pills",
        description = "",
        isDone = true,
        dueDate = DAY_0,
        reminderTime = DAY_0 + 8 * 60 * 60 * 1000L,
        repeatType = RepeatType.DAILY
    )

    @Test
    fun firstTriggerLeavesTheTaskAlone() {
        assertNull(dailyAtEight.advanceToOccurrence(dailyAtEight.reminderTime!!, UTC))
    }

    @Test
    fun nextDayRollsDueDateAndClearsCompletion() {
        val reminderTime = dailyAtEight.reminderTime!!

        val rolled = dailyAtEight.advanceToOccurrence(reminderTime + DAY, UTC)!!

        assertEquals(DAY_0 + DAY, rolled.dueDate)
        assertEquals(reminderTime + DAY, rolled.reminderTime)
        assertFalse(rolled.isDone)
    }

    @Test
    fun missedCyclesAreSkippedInOneStep() {
        val rolled = dailyAtEight.advanceToOccurrence(dailyAtEight.reminderTime!! + 5 * DAY, UTC)!!

        assertEquals(DAY_0 + 5 * DAY, rolled.dueDate)
    }

    @Test
    fun advanceWarningIsTakenOffTheTriggerTime() {
        val withAdvance = dailyAtEight.copy(advanceReminderMinutes = 30)
        val reminderTime = withAdvance.reminderTime!!

        val rolled = withAdvance.advanceToOccurrence(reminderTime - 30 * 60_000L + DAY, UTC)!!

        assertEquals(DAY_0 + DAY, rolled.dueDate)
        assertEquals(reminderTime + DAY, rolled.reminderTime)
    }

    @Test
    fun weeklyRepeatsMoveAWholeWeek() {
        val weekly = dailyAtEight.copy(repeatType = RepeatType.WEEKLY)

        val rolled = weekly.advanceToOccurrence(weekly.reminderTime!! + 7 * DAY, UTC)!!

        assertEquals(DAY_0 + 7 * DAY, rolled.dueDate)
    }

    @Test
    fun nonRepeatingTasksNeverRoll() {
        val oneShot = dailyAtEight.copy(repeatType = RepeatType.NONE)

        assertNull(oneShot.advanceToOccurrence(oneShot.reminderTime!! + DAY, UTC))
    }

    @Test
    fun aTaskWithoutADueDateKeepsNone() {
        val noDueDate = dailyAtEight.copy(dueDate = null)

        val rolled = noDueDate.advanceToOccurrence(noDueDate.reminderTime!! + DAY, UTC)!!

        assertNull(rolled.dueDate)
        assertFalse(rolled.isDone)
    }
}

/**
 * The same arithmetic on the two days a year that are not 24 hours long, in a zone that observes
 * them: US daylight saving starts on 2026-03-08 (that day is 23 hours) and ends on 2026-11-01 (25
 * hours). A repeat has to keep its wall-clock time and visit every calendar day across both.
 */
class ReminderDstTest {
    /** Due dates are stored as the midnight that starts the day, which is what the filters key on. */
    private val nightlyBeforeSpringForward = TodoItem(
        id = 1,
        title = "Take pills",
        description = "",
        isDone = true,
        dueDate = newYork(2026, 3, 7),
        reminderTime = newYork(2026, 3, 7, 23, 30),
        repeatType = RepeatType.DAILY
    )

    @Test
    fun springForwardDoesNotSkipACalendarDay() {
        // The alarm chain a version before this fix queued: the nominal 24 hours, an hour late.
        val trigger = nightlyBeforeSpringForward.reminderTime!! + DAY

        val rolled = nightlyBeforeSpringForward.advanceToOccurrence(trigger, NEW_YORK)!!

        assertEquals(newYork(2026, 3, 8), rolled.dueDate)
        assertEquals(newYork(2026, 3, 8, 23, 30), rolled.reminderTime)
        assertFalse(rolled.isDone)
    }

    @Test
    fun aCycleShortenedBySpringForwardStillCountsAsOneCycle() {
        // What the fixed chain queues: 2026-03-08 23:30, which is only 23 hours later.
        val trigger = RepeatType.DAILY.occurrenceAfter(
            nightlyBeforeSpringForward.reminderTime!!,
            1,
            NEW_YORK
        )

        val rolled = nightlyBeforeSpringForward.advanceToOccurrence(trigger, NEW_YORK)!!

        assertEquals(newYork(2026, 3, 8), rolled.dueDate)
        assertEquals(newYork(2026, 3, 8, 23, 30), rolled.reminderTime)
    }

    @Test
    fun fallBackKeepsTheReminderAtItsWallClockTime() {
        val nightly = nightlyBeforeSpringForward.copy(
            dueDate = newYork(2026, 10, 31),
            reminderTime = newYork(2026, 10, 31, 23, 30)
        )

        val rolled = nightly.advanceToOccurrence(nightly.reminderTime!! + DAY, NEW_YORK)!!

        assertEquals(newYork(2026, 11, 1), rolled.dueDate)
        assertEquals(newYork(2026, 11, 1, 23, 30), rolled.reminderTime)
    }

    /**
     * The 25-hour day is where a nominal 24-hour shift walks a due date *backwards* over midnight:
     * the occurrence for the 2nd would keep the 1st's due date, so two occurrences sit on one day
     * and the TODAY filter never has this task on the 2nd.
     */
    @Test
    fun fallBackDoesNotPinTwoOccurrencesOnOneDay() {
        val nightly = nightlyBeforeSpringForward.copy(
            dueDate = newYork(2026, 11, 1),
            reminderTime = newYork(2026, 11, 1, 23, 30)
        )

        val rolled = nightly.advanceToOccurrence(nightly.reminderTime!! + DAY, NEW_YORK)!!

        assertEquals(newYork(2026, 11, 2), rolled.dueDate)
        assertEquals(newYork(2026, 11, 2, 23, 30), rolled.reminderTime)
    }

    @Test
    fun weeklyRepeatsKeepTheirTimeOfDayAcrossSpringForward() {
        val weekly = nightlyBeforeSpringForward.copy(
            dueDate = newYork(2026, 3, 3),
            reminderTime = newYork(2026, 3, 3, 9, 0),
            repeatType = RepeatType.WEEKLY
        )

        val rolled = weekly.advanceToOccurrence(weekly.reminderTime!! + 7 * DAY, NEW_YORK)!!

        assertEquals(newYork(2026, 3, 10), rolled.dueDate)
        assertEquals(newYork(2026, 3, 10, 9, 0), rolled.reminderTime)
    }

    @Test
    fun theNextAlarmKeepsItsWallClockTimeAcrossSpringForward() {
        val lastTrigger = newYork(2026, 3, 7, 23, 30)

        assertEquals(
            newYork(2026, 3, 8, 23, 30),
            RepeatType.DAILY.occurrenceAfter(lastTrigger, 1, NEW_YORK)
        )
    }

    @Test
    fun missedAlarmsRollForwardToTheNextWallClockOccurrence() {
        val firstTrigger = newYork(2026, 3, 1, 8, 0)
        val now = newYork(2026, 3, 9, 7, 0)

        assertEquals(
            newYork(2026, 3, 9, 8, 0),
            RepeatType.DAILY.occurrenceAtOrAfter(firstTrigger, now, NEW_YORK)
        )
    }

    @Test
    fun anAlarmThatHasNotHappenedYetIsLeftWhereItIs() {
        val trigger = newYork(2026, 3, 9, 8, 0)

        assertEquals(
            trigger,
            RepeatType.DAILY.occurrenceAtOrAfter(trigger, newYork(2026, 3, 9, 7, 0), NEW_YORK)
        )
    }
}

private const val DAY = 24L * 60 * 60 * 1000
private const val DAY_0 = 1_700_000_000_000L
private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

/** [month] is 1-based, unlike Calendar's. */
private fun newYork(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(NEW_YORK).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
