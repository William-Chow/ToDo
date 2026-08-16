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

    /**
     * The gap between the first trigger and this one is rounded to whole cycles, so the halfway mark
     * is where the answer changes. An alarm that late is one the OS delivered late, not a different
     * occurrence, and rounding is what keeps a cycle shortened by a daylight-saving change counting
     * as one whole cycle rather than none.
     */
    @Test
    fun anAlarmLessThanHalfACycleLateIsStillTheSameOccurrence() {
        val reminderTime = dailyAtEight.reminderTime!!

        assertNull(dailyAtEight.advanceToOccurrence(reminderTime + 11 * HOUR + 59 * MINUTE, UTC))
    }

    @Test
    fun anAlarmMoreThanHalfACycleLateCountsAsTheNextOccurrence() {
        val reminderTime = dailyAtEight.reminderTime!!

        val rolled = dailyAtEight.advanceToOccurrence(reminderTime + 12 * HOUR + MINUTE, UTC)!!

        assertEquals(DAY_0 + DAY, rolled.dueDate)
    }

    /** A weekly repeat rounds over a much wider window: half of seven days, not half of one. */
    @Test
    fun aWeeklyRepeatRoundsOverHalfAWeek() {
        val weekly = dailyAtEight.copy(repeatType = RepeatType.WEEKLY)
        val reminderTime = weekly.reminderTime!!

        assertNull(weekly.advanceToOccurrence(reminderTime + 3 * DAY + 11 * HOUR, UTC))
        assertEquals(
            DAY_0 + 7 * DAY,
            weekly.advanceToOccurrence(reminderTime + 3 * DAY + 13 * HOUR, UTC)!!.dueDate
        )
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

/**
 * Covers finding the next live occurrence of a repeating reminder from a start that is behind the
 * clock — the boot path, where every stored task is rescheduled on the main thread. The nominal
 * interval only estimates the cycle count, so this has to land on the first occurrence at or after
 * "now" whatever the calendar did in between, and it has to do it in bounded time even when the
 * start it is handed is a timestamp no user could have produced.
 */
class ReminderCatchUpTest {
    @Test
    fun anOccurrenceLandingExactlyOnNowIsTheOneScheduled() {
        val start = newYork(2026, 5, 1, 8, 0)
        val now = newYork(2026, 5, 3, 8, 0)

        assertEquals(now, RepeatType.DAILY.occurrenceAtOrAfter(start, now, NEW_YORK))
    }

    @Test
    fun monthsOfMissedCyclesLandOnTheNextWallClockOccurrence() {
        val first = newYork(2026, 2, 1, 8, 0)

        listOf(
            newYork(2026, 3, 8, 7, 0) to newYork(2026, 3, 8, 8, 0),
            newYork(2026, 3, 8, 9, 0) to newYork(2026, 3, 9, 8, 0),
            newYork(2026, 3, 9, 7, 0) to newYork(2026, 3, 9, 8, 0),
            newYork(2026, 11, 2, 7, 0) to newYork(2026, 11, 2, 8, 0),
            newYork(2027, 6, 1, 7, 0) to newYork(2027, 6, 1, 8, 0)
        ).forEach { (now, expected) ->
            assertEquals(expected, RepeatType.DAILY.occurrenceAtOrAfter(first, now, NEW_YORK))
        }
    }

    /**
     * Swept against a brute-force count of the same cycles: the answer has to be the *first*
     * occurrence at or after now, never a later one, on both sides of both transitions.
     */
    @Test
    fun theAnswerIsAlwaysTheFirstOccurrenceNotJustAFutureOne() {
        listOf(RepeatType.DAILY, RepeatType.WEEKLY).forEach { repeat ->
            val start = newYork(2026, 2, 20, 8, 0)
            var now = newYork(2026, 2, 20, 0, 0)
            val end = newYork(2026, 11, 10, 0, 0)

            while (now < end) {
                var cycles = 0
                var expected = start
                while (expected < now) {
                    cycles++
                    expected = repeat.occurrenceAfter(start, cycles, NEW_YORK)
                }

                assertEquals(
                    "$repeat from $start at $now",
                    expected,
                    repeat.occurrenceAtOrAfter(start, now, NEW_YORK)
                )
                now += 7 * HOUR + 13 * MINUTE
            }
        }
    }

    /** Pacific/Apia crossed the date line in 2011 and skipped 2011-12-30 outright. */
    @Test
    fun aZoneThatDroppedAWholeCalendarDayIsStillCaughtUp() {
        val apia = TimeZone.getTimeZone("Pacific/Apia")
        val start = at(apia, 2011, 12, 28, 9, 0)

        assertEquals(
            at(apia, 2012, 1, 5, 9, 0),
            RepeatType.DAILY.occurrenceAtOrAfter(start, at(apia, 2012, 1, 5, 8, 0), apia)
        )
    }

    @Test
    fun aNonRepeatingReminderIsNeverMoved() {
        val trigger = newYork(2026, 3, 7, 23, 30)

        assertEquals(trigger, RepeatType.NONE.occurrenceAfter(trigger, 5, NEW_YORK))
        assertEquals(trigger, RepeatType.NONE.occurrenceAtOrAfter(trigger, trigger + 100 * DAY, NEW_YORK))
    }

    /**
     * A start out of a corrupted store, from far enough back that the cycle count does not fit in an
     * Int: 26_688_018_334 cycles, which wrapped to 918_214_558 when it was cast, leaving the walk
     * billions of calendar days to cover a Calendar at a time. BootReceiver reschedules every stored
     * task on the main thread, so this has to return rather than be worth waiting for.
     */
    @Test(timeout = 5_000)
    fun aStartTooFarBackToCountDoesNotWalkThere() {
        val now = 1_775_000_000_000L

        assertEquals(now, RepeatType.DAILY.occurrenceAtOrAfter(Long.MIN_VALUE / 4, now, NEW_YORK))
    }

    /** Far enough back that `now - start` overflows, so the estimate cannot even be taken. */
    @Test(timeout = 5_000)
    fun aStartThatOverflowsTheElapsedTimeDoesNotWalkEither() {
        val now = 1_775_000_000_000L

        assertEquals(now, RepeatType.WEEKLY.occurrenceAtOrAfter(Long.MIN_VALUE, now, NEW_YORK))
    }

    /**
     * The largest start the cycle-count bound still lets through — its estimate is exactly
     * Int.MAX_VALUE — and the one case the walk bound catches on its own. The estimate is a cycle
     * short as always, but there is no room left to add it: the walk's `cycles++` wraps to
     * Int.MIN_VALUE, the occurrence lands 11 million years back, and it stays below now for every
     * step after that. Clamping the count does not help here; only stopping the walk does.
     */
    @Test(timeout = 5_000)
    fun aStartWhoseCycleCountOnlyJustFitsAnIntDoesNotWalkEither() {
        val now = 1_775_000_000_000L
        val start = now - (Int.MAX_VALUE.toLong() + 1) * DAY

        assertEquals(now, RepeatType.DAILY.occurrenceAtOrAfter(start, now, NEW_YORK))
    }
}

private const val MINUTE = 60L * 1000
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val DAY_0 = 1_700_000_000_000L
private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

/** [month] is 1-based, unlike Calendar's. */
private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

private fun newYork(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    at(NEW_YORK, year, month, day, hour, minute)
