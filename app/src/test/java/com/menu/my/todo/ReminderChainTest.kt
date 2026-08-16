package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.advanceToOccurrence
import com.menu.my.todo.notification.occurrenceAfter
import com.menu.my.todo.notification.occurrenceAtOrAfter

import org.junit.Test

import org.junit.Assert.*

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Runs the whole loop instead of one step of it: ReminderManager schedules the first occurrence, the
 * alarm fires, ReminderReceiver rolls the stored task onto it and queues the next cycle, and round
 * again. Each step can be right on its own while the chain still drifts, because every step starts
 * from what the last one wrote — which is exactly what the days that are not 24 hours long test.
 *
 * Expected calendar days come from [LocalDate] rather than from another Calendar walk, so the thing
 * under test is not also the thing describing the answer.
 */
class ReminderChainTest {
    @Test
    fun newYorkSpringForwardLateAtNight() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 3, 6, 23, 30)

    @Test
    fun newYorkSpringForwardInTheMorning() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 3, 6, 8, 0)

    @Test
    fun newYorkFallBackLateAtNight() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 10, 30, 23, 30)

    @Test
    fun newYorkFallBackJustAfterMidnight() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 10, 30, 0, 30)

    @Test
    fun londonSpringForward() =
        assertTheChainWalksADayAtATime("Europe/London", 2026, 3, 27, 23, 30)

    @Test
    fun sydneyLeavesDaylightSaving() =
        assertTheChainWalksADayAtATime("Australia/Sydney", 2026, 4, 3, 23, 30)

    /** Lord Howe Island shifts by half an hour rather than a whole one. */
    @Test
    fun lordHoweShiftsByHalfAnHour() =
        assertTheChainWalksADayAtATime("Australia/Lord_Howe", 2026, 10, 2, 23, 30)

    /** Two zones that never move their clocks, one of them at a half-hour offset from UTC. */
    @Test
    fun kolkataIsHalfAnHourOffTheHour() =
        assertTheChainWalksADayAtATime("Asia/Kolkata", 2026, 3, 6, 23, 30)

    @Test
    fun shanghaiNeverChangesItsClocks() =
        assertTheChainWalksADayAtATime("Asia/Shanghai", 2026, 3, 6, 23, 30)

    /** A full day of advance warning puts every alarm on the far side of a cycle boundary. */
    @Test
    fun aDayOfAdvanceWarningAcrossSpringForward() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 3, 6, 23, 30, advanceMinutes = 1440)

    /**
     * The advance warning lands the alarm itself inside the hour that is skipped, so the alarm drifts
     * an hour early while the task it belongs to must not: the roll counts whole cycles, and 23 hours
     * still rounds to one.
     */
    @Test
    fun anAdvanceWarningThatLandsTheAlarmInTheSkippedHour() =
        assertTheChainWalksADayAtATime("America/New_York", 2026, 3, 6, 3, 0, advanceMinutes = 60)

    @Test
    fun aWeeklyChainKeepsItsTimeOfDayAcrossBothTransitions() {
        val zone = TimeZone.getTimeZone("America/New_York")

        listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 10, 25)).forEach { firstDay ->
            val weekly = TodoItem(
                id = 1,
                title = "Take pills",
                description = "",
                isDone = true,
                dueDate = localTime(zone, firstDay.year, firstDay.monthValue, firstDay.dayOfMonth),
                reminderTime = localTime(zone, firstDay.year, firstDay.monthValue, firstDay.dayOfMonth, 9, 0),
                repeatType = RepeatType.WEEKLY
            )

            val rolls = chain(zone, weekly, 5).drop(1).map { it.second }

            assertEquals(
                "a weekly task has to keep landing on the same weekday",
                (1..rolls.size).map { "${firstDay.plusWeeks(it.toLong())} 09:00" },
                rolls.map { "${dayOf(zone, it.dueDate!!)} ${timeOfDay(zone, it.reminderTime!!)}" }
            )
        }
    }

    /**
     * Every alarm after the first has to roll the task onto its own calendar day, in order, keeping
     * the reminder's time of day and leaving the due date on the midnight that starts the day. The
     * first alarm is the first occurrence and deliberately rolls nothing, so it is not counted.
     */
    private fun assertTheChainWalksADayAtATime(
        zoneId: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        advanceMinutes: Int = 0
    ) {
        val zone = TimeZone.getTimeZone(zoneId)
        val daily = TodoItem(
            id = 1,
            title = "Take pills",
            description = "",
            isDone = true,
            dueDate = localTime(zone, year, month, day),
            reminderTime = localTime(zone, year, month, day, hour, minute),
            repeatType = RepeatType.DAILY,
            advanceReminderMinutes = advanceMinutes
        )

        val rolls = chain(zone, daily, 7).drop(1).map { it.second }
        val firstDay = LocalDate.of(year, month, day)

        assertEquals(
            "$zoneId: a daily task has to be due on every calendar day, once each",
            (1..rolls.size).map { "${firstDay.plusDays(it.toLong())} 00:00" },
            rolls.map { roll -> roll.dueDate!!.let { "${dayOf(zone, it)} ${timeOfDay(zone, it)}" } }
        )
        assertEquals(
            "$zoneId: the reminder drifted off its wall-clock time",
            rolls.map { "%02d:%02d".format(hour, minute) },
            rolls.map { timeOfDay(zone, it.reminderTime!!) }
        )
        rolls.forEach { assertFalse("$zoneId: a roll left the task done", it.isDone) }
    }
}

/**
 * The one wall-clock time a calendar step cannot keep: an hour a spring-forward skips does not exist
 * on the day being stepped to. Both occurrenceAfter's KDoc and advanceToOccurrence's used to claim
 * the step keeps the time of day full stop, so what it does instead is pinned here — it resolves
 * *backwards*, and because the next step starts from that value the reminder stays an hour early
 * rather than righting itself the following day.
 */
class ReminderGapTest {
    private val newYork: TimeZone = TimeZone.getTimeZone("America/New_York")

    /** New York goes 02:00 -> 03:00 on 2026-03-08, so a 02:30 daily has nowhere to land that day. */
    @Test
    fun aReminderInTheSkippedHourStepsBackAnHourAndStaysThere() {
        var occurrence = localTime(newYork, 2026, 3, 7, 2, 30)

        val week = (1..9).map {
            occurrence = RepeatType.DAILY.occurrenceAfter(occurrence, 1, newYork)
            "${dayOf(newYork, occurrence)} ${timeOfDay(newYork, occurrence)}"
        }

        assertEquals(
            listOf(
                "2026-03-08 01:30",
                "2026-03-09 01:30",
                "2026-03-10 01:30",
                "2026-03-11 01:30",
                "2026-03-12 01:30",
                "2026-03-13 01:30",
                "2026-03-14 01:30",
                "2026-03-15 01:30",
                "2026-03-16 01:30"
            ),
            week
        )
    }

    /**
     * Counting cycles from a start the shift never touched does recover the time of day the day
     * after — which is why this is a property of the chain rather than of the arithmetic.
     */
    @Test
    fun countingCyclesFromTheOriginalStartRecoversTheTimeOfDay() {
        val start = localTime(newYork, 2026, 3, 7, 2, 30)

        assertEquals(
            listOf("2026-03-08 01:30", "2026-03-09 02:30", "2026-03-10 02:30"),
            (1..3).map {
                val occurrence = RepeatType.DAILY.occurrenceAfter(start, it, newYork)
                "${dayOf(newYork, occurrence)} ${timeOfDay(newYork, occurrence)}"
            }
        )
    }

    /**
     * Santiago springs forward at midnight, so there it is due dates — stored as the midnight that
     * starts the day — that lose their time of day rather than reminders. 01:00 is genuinely the
     * first instant of 2026-09-06; every day after it is where the claim breaks.
     */
    @Test
    fun aDueDateStoredAsMidnightMovesToOneAmInSantiagoAndStays() {
        val santiago = TimeZone.getTimeZone("America/Santiago")
        var dueDate = localTime(santiago, 2026, 9, 4)

        val week = (1..5).map {
            dueDate = RepeatType.DAILY.occurrenceAfter(dueDate, 1, santiago)
            "${dayOf(santiago, dueDate)} ${timeOfDay(santiago, dueDate)}"
        }

        assertEquals(
            listOf(
                "2026-09-05 00:00",
                "2026-09-06 01:00",
                "2026-09-07 01:00",
                "2026-09-08 01:00",
                "2026-09-09 01:00"
            ),
            week
        )
    }

    /** The other transition has no such hole: 01:30 happens twice, and a step picks one of them. */
    @Test
    fun aReminderInTheRepeatedHourKeepsItsTimeOfDay() {
        var occurrence = localTime(newYork, 2026, 10, 31, 1, 30)

        val week = (1..4).map {
            occurrence = RepeatType.DAILY.occurrenceAfter(occurrence, 1, newYork)
            "${dayOf(newYork, occurrence)} ${timeOfDay(newYork, occurrence)}"
        }

        assertEquals(
            listOf(
                "2026-11-01 01:30",
                "2026-11-02 01:30",
                "2026-11-03 01:30",
                "2026-11-04 01:30"
            ),
            week
        )
    }
}

private const val ONE_MINUTE = 60L * 1000

/**
 * Every (alarm time, stored task as that alarm left it) the app would produce, first alarm included:
 * ReminderManager.scheduleReminder picks the first one, then each ReminderReceiver rolls the task
 * (advanceToOccurrence) and queues the cycle after it (ReminderManager.scheduleNext).
 */
private fun chain(zone: TimeZone, task: TodoItem, fires: Int): List<Pair<Long, TodoItem>> {
    val repeat = task.repeatType!!
    var stored = task
    val firstTrigger = stored.reminderTime!! - stored.advanceReminderMinutes * ONE_MINUTE
    var trigger = repeat.occurrenceAtOrAfter(firstTrigger, firstTrigger - 1, zone)
    return (1..fires).map {
        val fired = trigger
        stored = stored.advanceToOccurrence(fired, zone) ?: stored
        trigger = repeat.occurrenceAtOrAfter(repeat.occurrenceAfter(fired, 1, zone), fired, zone)
        fired to stored
    }
}

/** [month] is 1-based, unlike Calendar's. */
private fun localTime(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

private fun dayOf(zone: TimeZone, time: Long): String = format("yyyy-MM-dd", zone, time)

private fun timeOfDay(zone: TimeZone, time: Long): String = format("HH:mm", zone, time)

private fun format(pattern: String, zone: TimeZone, time: Long): String =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(time))
