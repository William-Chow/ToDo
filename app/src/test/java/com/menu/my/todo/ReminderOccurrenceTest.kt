package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.advanceToOccurrence

import org.junit.Test

import org.junit.Assert.*

/**
 * Covers the arithmetic behind repeating reminders: when an alarm fires, the task has to land on the
 * occurrence that alarm was for, with completion cleared.
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
        assertNull(dailyAtEight.advanceToOccurrence(dailyAtEight.reminderTime!!))
    }

    @Test
    fun nextDayRollsDueDateAndClearsCompletion() {
        val reminderTime = dailyAtEight.reminderTime!!

        val rolled = dailyAtEight.advanceToOccurrence(reminderTime + DAY)!!

        assertEquals(DAY_0 + DAY, rolled.dueDate)
        assertEquals(reminderTime + DAY, rolled.reminderTime)
        assertFalse(rolled.isDone)
    }

    @Test
    fun missedCyclesAreSkippedInOneStep() {
        val rolled = dailyAtEight.advanceToOccurrence(dailyAtEight.reminderTime!! + 5 * DAY)!!

        assertEquals(DAY_0 + 5 * DAY, rolled.dueDate)
    }

    @Test
    fun advanceWarningIsTakenOffTheTriggerTime() {
        val withAdvance = dailyAtEight.copy(advanceReminderMinutes = 30)
        val reminderTime = withAdvance.reminderTime!!

        val rolled = withAdvance.advanceToOccurrence(reminderTime - 30 * 60_000L + DAY)!!

        assertEquals(DAY_0 + DAY, rolled.dueDate)
        assertEquals(reminderTime + DAY, rolled.reminderTime)
    }

    @Test
    fun weeklyRepeatsMoveAWholeWeek() {
        val weekly = dailyAtEight.copy(repeatType = RepeatType.WEEKLY)

        val rolled = weekly.advanceToOccurrence(weekly.reminderTime!! + 7 * DAY)!!

        assertEquals(DAY_0 + 7 * DAY, rolled.dueDate)
    }

    @Test
    fun nonRepeatingTasksNeverRoll() {
        val oneShot = dailyAtEight.copy(repeatType = RepeatType.NONE)

        assertNull(oneShot.advanceToOccurrence(oneShot.reminderTime!! + DAY))
    }

    @Test
    fun aTaskWithoutADueDateKeepsNone() {
        val noDueDate = dailyAtEight.copy(dueDate = null)

        val rolled = noDueDate.advanceToOccurrence(noDueDate.reminderTime!! + DAY)!!

        assertNull(rolled.dueDate)
        assertFalse(rolled.isDone)
    }
}

private const val DAY = 24L * 60 * 60 * 1000
private const val DAY_0 = 1_700_000_000_000L
