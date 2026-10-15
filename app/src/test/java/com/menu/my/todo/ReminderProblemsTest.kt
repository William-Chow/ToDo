package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.ui.screens.reminderProblems

import org.junit.Test

import org.junit.Assert.*

/**
 * What the editor warns about before it saves a reminder.
 *
 * The case worth pinning is the one that used to nag: a one-shot reminder that has already gone off
 * is in the past, and a task opened for any other edit — a typo in the title — was warned that its
 * reminder "will never ring", on that save and every one after it. A reminder left as it was is not
 * being set, and is not warned about.
 */
class ReminderProblemsTest {

    /** A task whose one-shot reminder went off an hour ago. */
    private val fired = TodoItem(
        id = 1,
        title = "Call the bank",
        description = "",
        reminderTime = NOW - HOUR,
        repeatType = RepeatType.NONE
    )

    @Test
    fun aReminderThatAlreadyWentOffIsNotWarnedAboutOnAnUnrelatedEdit() {
        assertEquals(NOTHING, problems(opened = fired, reminder = NOW - HOUR))
    }

    @Test
    fun noPermissionIsWarnedAboutForAReminderNothingWillSchedule() {
        // The nag came back through the permissions too: nothing is queued for a dropped reminder,
        // so what it would have needed is beside the point.
        assertEquals(
            NOTHING,
            problems(opened = fired, reminder = NOW - HOUR, notifications = false, exact = false)
        )
    }

    @Test
    fun aNewTaskGivenAPastReminderIsWarned() {
        assertEquals(listOf(R.string.reminder_problem_past), problems(opened = null, reminder = NOW - HOUR))
    }

    @Test
    fun movingAReminderOntoAPastTimeIsWarned() {
        assertEquals(
            listOf(R.string.reminder_problem_past),
            problems(opened = fired, reminder = NOW - 2 * HOUR)
        )
    }

    @Test
    fun anAdvanceWarningThatPutsTheAlarmBehindUsIsWarned() {
        val upcoming = fired.copy(reminderTime = NOW + 10 * MINUTE)

        assertEquals(
            listOf(R.string.reminder_problem_past),
            problems(opened = upcoming, reminder = NOW + 10 * MINUTE, advance = 30)
        )
    }

    @Test
    fun turningARepeatOffOnAPastReminderIsWarned() {
        // A repeating reminder's stored time is where the series is up to, often already behind
        // us. Made one-shot, that time is all that is left of it, and it is gone.
        val daily = fired.copy(repeatType = RepeatType.DAILY)

        assertEquals(
            listOf(R.string.reminder_problem_past),
            problems(opened = daily, reminder = NOW - HOUR, repeat = RepeatType.NONE)
        )
    }

    @Test
    fun aRepeatingReminderBehindUsIsNeverWarned() {
        assertEquals(NOTHING, problems(opened = null, reminder = NOW - HOUR, repeat = RepeatType.DAILY))
    }

    @Test
    fun aTaskStoredWithoutARepeatTypeCountsAsOneShotWhenComparing() {
        // Tasks stored before repeatType existed read it back as null.
        val legacy = fired.copy(repeatType = null)

        assertEquals(NOTHING, problems(opened = legacy, reminder = NOW - HOUR))
    }

    @Test
    fun anUpcomingReminderIsWarnedAboutEachMissingPermissionInOrder() {
        assertEquals(
            listOf(R.string.reminder_problem_notifications, R.string.reminder_problem_exact_alarm),
            problems(opened = null, reminder = NOW + HOUR, notifications = false, exact = false)
        )
    }

    @Test
    fun anUpcomingReminderWithEverythingInPlaceIsNotWarned() {
        assertEquals(NOTHING, problems(opened = null, reminder = NOW + HOUR))
    }

    private fun problems(
        opened: TodoItem?,
        reminder: Long,
        advance: Int = 0,
        repeat: RepeatType = RepeatType.NONE,
        notifications: Boolean = true,
        exact: Boolean = true
    ) = reminderProblems(opened, reminder, advance, repeat, NOW, notifications, exact)
}

private val NOTHING = emptyList<Int>()

private const val MINUTE = 60L * 1000
private const val HOUR = 60 * MINUTE
private const val NOW = 1_700_000_000_000L
