package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.SNOOZE_MINUTES
import com.menu.my.todo.notification.advanceToOccurrence
import com.menu.my.todo.notification.snoozedUntil
import com.menu.my.todo.notification.withTaskDone
import com.menu.my.todo.viewmodel.todayProgress
import com.menu.my.todo.viewmodel.withDone

import org.junit.Test

import org.junit.Assert.*

import java.util.TimeZone

/**
 * What the Done and Snooze buttons on a reminder notification do to the stored tasks.
 *
 * Done writes storage directly, from a receiver, while the app may or may not be open; what it
 * writes has to be exactly what ticking the task off in the app would have written, or the app's
 * own rules — today's progress, the next occurrence of a repeat — stop agreeing with it. The alarms
 * the buttons queue and cancel are not reachable from a JVM test; their arithmetic is.
 */
class NotificationActionsTest {

    private val oneShot = TodoItem(
        id = 0,
        title = "Call the bank",
        description = "",
        dueDate = TODAY_START,
        reminderTime = TODAY_START + 9 * HOUR
    )

    private val nightly = TodoItem(
        id = 1,
        title = "Take pills",
        description = "",
        dueDate = TODAY_START,
        reminderTime = TODAY_START + 22 * HOUR,
        repeatType = RepeatType.DAILY
    )

    private val other = TodoItem(id = 2, title = "Buy milk", description = "")

    // ---- Done ---------------------------------------------------------------

    @Test
    fun doneTicksTheTaskOffAndRecordsWhen() {
        val ticked = listOf(oneShot, other).withTaskDone(oneShot.id, NOW)!!.first { it.id == oneShot.id }

        assertTrue(ticked.isDone)
        assertEquals(NOW, ticked.completedAt)
    }

    @Test
    fun doneWritesWhatTickingItOffInTheAppWould() {
        assertEquals(oneShot.withDone(true, NOW), listOf(oneShot).withTaskDone(oneShot.id, NOW)!!.single())
    }

    @Test
    fun doneLeavesEveryOtherTaskAsItWasAndWhereItWas() {
        val list = listOf(other, oneShot, nightly)

        val after = list.withTaskDone(oneShot.id, NOW)!!

        assertEquals(list.map { it.id }, after.map { it.id })
        assertEquals(other, after[0])
        assertEquals(nightly, after[2])
    }

    @Test
    fun doneOnADeletedTaskWritesNothing() {
        // The task went while its notification was still up.
        assertNull(listOf(other).withTaskDone(oneShot.id, NOW))
    }

    @Test
    fun doneOnATaskAlreadyTickedOffWritesNothing() {
        // Ticked off in the app first: its own completion time is the one that counts, so the
        // button must not move it on to when the notification happened to be answered.
        val tickedEarlier = oneShot.withDone(true, NOW - HOUR)

        assertNull(listOf(tickedEarlier).withTaskDone(oneShot.id, NOW))
    }

    @Test
    fun doneOnARepeatingTaskLeavesItsDatesAlone() {
        val ticked = listOf(nightly).withTaskDone(nightly.id, NOW)!!.single()

        assertEquals(nightly.dueDate, ticked.dueDate)
        assertEquals(nightly.reminderTime, ticked.reminderTime)
        assertEquals(RepeatType.DAILY, ticked.repeatType)
    }

    @Test
    fun aTaskDoneFromTheNotificationCountsAsFinishedToday() {
        val list = listOf(oneShot, other)

        assertEquals(0 to 2, todayProgress(list, TODAY_START, TODAY_END))
        assertEquals(1 to 2, todayProgress(list.withTaskDone(oneShot.id, NOW)!!, TODAY_START, TODAY_END))
    }

    @Test
    fun aRepeatingTaskDoneFromTheNotificationIsOwedAgainAtItsNextOccurrence() {
        val ticked = listOf(nightly).withTaskDone(nightly.id, NOW)!!.single()

        val tomorrow = ticked.advanceToOccurrence(nightly.reminderTime!! + DAY, UTC)!!

        assertFalse(tomorrow.isDone)
        assertNull(tomorrow.completedAt)
        assertEquals(TODAY_START + DAY, tomorrow.dueDate)
    }

    // ---- Snooze -------------------------------------------------------------

    @Test
    fun snoozePutsTheReminderOffByTenMinutes() {
        assertEquals(10, SNOOZE_MINUTES)
        assertEquals(NOW + 10 * MINUTE, snoozedUntil(NOW))
    }

    @Test
    fun aSnoozeIsAlwaysStillToCome() {
        // Queued as an exact alarm: one already behind the clock would go off at once instead.
        assertTrue(snoozedUntil(NOW) > NOW)
    }
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private const val MINUTE = 60L * 1000
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Midnight UTC, and the day it starts. */
private const val TODAY_START = 1_699_920_000_000L
private const val TODAY_END = TODAY_START + DAY
private const val NOW = TODAY_START + 10 * HOUR
