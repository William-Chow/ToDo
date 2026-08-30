package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.notification.reminderTrigger

import org.junit.Test

import org.junit.Assert.*

import java.util.Calendar
import java.util.TimeZone

/**
 * When a reminder would actually go off — and, the reason this is worth its own function, when it
 * would not go off at all.
 *
 * A one-shot reminder whose moment has gone is dropped rather than scheduled. That was always true;
 * what was missing was anywhere for the editor to ask, so a task could be saved naming a time that
 * nothing was ever going to act on. A repeating reminder is never dropped, because its stored time
 * is only where the series starts.
 *
 * Everything here is in UTC so that a reminder an hour either side of a boundary means the same
 * thing wherever the test runs. What calendar steps do to days that are not 24 hours long is
 * ReminderDstTest's question, not this one's.
 */
class ReminderTriggerTest {

    /** Sunday 2026-08-30, 15:00 UTC. */
    private val now = at(2026, 8, 30, 15, 0)

    @Test
    fun aOneShotReminderStillToComeKeepsItsTime() {
        val reminder = at(2026, 8, 30, 18, 0)

        assertEquals(reminder, reminderTrigger(reminder, 0, RepeatType.NONE, now, UTC))
    }

    @Test
    fun aOneShotReminderThatHasGoneByIsNotScheduledAtAll() {
        // This is the one the editor could not see: a reminder set for earlier in the same day was
        // stored, shown on the button, and then never heard from again.
        val reminder = at(2026, 8, 30, 9, 0)

        assertNull(reminderTrigger(reminder, 0, RepeatType.NONE, now, UTC))
    }

    @Test
    fun aOneShotReminderDueThisInstantStillCounts() {
        assertEquals(now, reminderTrigger(now, 0, RepeatType.NONE, now, UTC))
    }

    @Test
    fun theAdvanceWarningMovesTheAlarmEarlier() {
        val reminder = at(2026, 8, 30, 18, 0)

        assertEquals(at(2026, 8, 30, 17, 30), reminderTrigger(reminder, 30, RepeatType.NONE, now, UTC))
    }

    @Test
    fun anAdvanceWarningThatReachesIntoThePastTakesTheAlarmWithIt() {
        // 16:00 is still to come, but "four hours before it" is not, and it is the alarm that has to
        // be set rather than the reminder it is for.
        val reminder = at(2026, 8, 30, 16, 0)

        assertNull(reminderTrigger(reminder, 4 * 60, RepeatType.NONE, now, UTC))
    }

    @Test
    fun aDailyReminderThatStartedInThePastRollsOnToTheNextOne() {
        val reminder = at(2026, 8, 1, 9, 0)

        assertEquals(at(2026, 8, 31, 9, 0), reminderTrigger(reminder, 0, RepeatType.DAILY, now, UTC))
    }

    @Test
    fun aDailyReminderIsNeverDroppedHoweverFarBackItStarted() {
        val reminder = at(2020, 1, 1, 9, 0)

        assertNotNull(reminderTrigger(reminder, 0, RepeatType.DAILY, now, UTC))
    }

    @Test
    fun aWeeklyReminderStepsInWholeWeeksFromWhereItStarted() {
        // Started Wednesday 2026-08-05; the first Wednesday 09:00 still to come is 2026-09-02.
        val reminder = at(2026, 8, 5, 9, 0)

        assertEquals(at(2026, 9, 2, 9, 0), reminderTrigger(reminder, 0, RepeatType.WEEKLY, now, UTC))
    }

    @Test
    fun aRepeatingReminderKeepsItsAdvanceWarningOnEveryCycle() {
        val reminder = at(2026, 8, 1, 9, 0)

        assertEquals(at(2026, 8, 31, 8, 30), reminderTrigger(reminder, 30, RepeatType.DAILY, now, UTC))
    }

    @Test
    fun aRepeatingReminderStillToComeIsLeftWhereItIs() {
        val reminder = at(2026, 9, 5, 9, 0)

        assertEquals(reminder, reminderTrigger(reminder, 0, RepeatType.DAILY, now, UTC))
    }
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance(UTC).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
