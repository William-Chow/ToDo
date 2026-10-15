package com.menu.my.todo

import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.notification.advanceToOccurrence
import com.menu.my.todo.viewmodel.TodoCategory
import com.menu.my.todo.viewmodel.categoryFilter
import com.menu.my.todo.viewmodel.todayProgress
import com.menu.my.todo.viewmodel.withDone

import org.junit.Test

import org.junit.Assert.*

import java.util.TimeZone

/**
 * The 今日进度 header, measured against the TODAY list it sits above.
 *
 * TODAY took in late and undated tasks while the header went on counting only tasks due today, so
 * a TODAY list of seven could sit under no header at all, or under "0 / 1", and finishing a late
 * task never moved the bar. The header now counts what is still owed plus what was finished today,
 * which needs to know *when* a task was finished — [TodoItem.completedAt].
 */
class TodayProgressTest {

    private val late = task(id = 1, due = TODAY_START - 3 * DAY)
    private val undated = task(id = 2, due = null)
    private val dueToday = task(id = 3, due = TODAY_START)
    private val dueTomorrow = task(id = 4, due = TODAY_END)

    @Test
    fun lateAndUndatedTasksAreOnTodaysPlate() {
        assertEquals(0 to 3, progress(late, undated, dueToday, dueTomorrow))
    }

    @Test
    fun finishingALateTaskMovesTheBar() {
        assertEquals(1 to 3, progress(late.withDone(true, NOW), undated, dueToday))
    }

    @Test
    fun finishingAnUndatedTaskMovesTheBar() {
        assertEquals(1 to 3, progress(late, undated.withDone(true, NOW), dueToday))
    }

    @Test
    fun whatIsLeftIsAlwaysExactlyTheTodayList() {
        val list = listOf(
            late.withDone(true, NOW),
            undated,
            dueToday.withDone(true, TODAY_START + HOUR),
            dueTomorrow,
            task(id = 5, due = TODAY_START - DAY).withDone(true, TODAY_START - HOUR)
        )

        val (done, total) = todayProgress(list, TODAY_START, TODAY_END)

        assertEquals(categoryFilter(list, TodoCategory.TODAY, TODAY_END).size, total - done)
    }

    @Test
    fun aTaskFinishedYesterdayIsNotTodaysProgress() {
        assertEquals(0 to 2, progress(late.withDone(true, TODAY_START - HOUR), undated, dueToday))
    }

    @Test
    fun aTaskFinishedEarlyWasNeverOnTodaysList() {
        assertEquals(0 to 1, progress(dueToday, dueTomorrow.withDone(true, NOW)))
    }

    @Test
    fun aTaskFinishedBeforeCompletionTimesWereKeptIsNotCounted() {
        // Stored before completedAt existed: done, with nothing to say on which day.
        assertEquals(0 to 1, progress(dueToday.copy(isDone = true), undated))
    }

    @Test
    fun nothingOwedAndNothingFinishedIsNoProgressToShow() {
        assertEquals(0 to 0, progress(dueTomorrow))
    }

    @Test
    fun tickingATaskOffRecordsWhen() {
        val done = dueToday.withDone(true, NOW)

        assertTrue(done.isDone)
        assertEquals(NOW, done.completedAt)
    }

    @Test
    fun untickingATaskForgetsWhen() {
        val undone = dueToday.withDone(true, NOW).withDone(false, NOW + HOUR)

        assertFalse(undone.isDone)
        assertNull(undone.completedAt)
    }

    @Test
    fun aRepeatingTaskRolledOnByItsAlarmIsNoLongerFinished() {
        val nightly = task(id = 6, due = TODAY_START - DAY).copy(
            reminderTime = TODAY_START - DAY + 22 * HOUR,
            repeatType = RepeatType.DAILY
        ).withDone(true, TODAY_START - 2 * HOUR)

        val rolled = nightly.advanceToOccurrence(nightly.reminderTime!! + DAY, UTC)!!

        assertFalse(rolled.isDone)
        assertNull(rolled.completedAt)
    }

    private fun progress(vararg todos: TodoItem) = todayProgress(todos.toList(), TODAY_START, TODAY_END)

    private fun task(id: Int, due: Long?) = TodoItem(id = id, title = "task $id", description = "", dueDate = due)
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR

/** Midnight tonight, UTC; today is the 24 hours before it. */
private const val TODAY_END = 1_699_920_000_000L + DAY
private const val TODAY_START = TODAY_END - DAY
private const val NOW = TODAY_START + 10 * HOUR
