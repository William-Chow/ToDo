package com.menu.my.todo

import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.viewmodel.TodoCategory
import com.menu.my.todo.viewmodel.categoryFilter

import org.junit.Test

import org.junit.Assert.*

/**
 * Which list a task turns up in.
 *
 * The case worth pinning is the late one: a task whose due date has gone past used to fall between
 * TODAY, which stopped at this morning, and UPCOMING, which starts tomorrow, and so appeared under
 * no filter at all. It belongs in TODAY, which is the list of what is still owed.
 */
class CategoryFilterTest {

    @Test
    fun anOverdueTaskIsInToday() {
        val late = task(id = 1, due = TODAY_START - 3 * DAY)

        assertEquals(listOf(late), categoryFilter(listOf(late), TodoCategory.TODAY, TODAY_END))
    }

    @Test
    fun anOverdueTaskIsNotUpcoming() {
        val late = task(id = 1, due = TODAY_START - 3 * DAY)

        assertEquals(emptyList<TodoItem>(), categoryFilter(listOf(late), TodoCategory.UPCOMING, TODAY_END))
    }

    @Test
    fun aTaskDueTodayIsInToday() {
        val today = task(id = 1, due = TODAY_START)

        assertEquals(listOf(today), categoryFilter(listOf(today), TodoCategory.TODAY, TODAY_END))
    }

    @Test
    fun aTaskDueTomorrowIsUpcomingAndNotToday() {
        val tomorrow = task(id = 1, due = TODAY_END + 1)
        val list = listOf(tomorrow)

        assertEquals(listOf(tomorrow), categoryFilter(list, TodoCategory.UPCOMING, TODAY_END))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.TODAY, TODAY_END))
    }

    @Test
    fun midnightTonightBelongsToTomorrow() {
        // The bound is exclusive on both sides of the split, so the instant the day ends is the
        // first instant of the next one and lands in exactly one list.
        val boundary = task(id = 1, due = TODAY_END)
        val list = listOf(boundary)

        assertEquals(listOf(boundary), categoryFilter(list, TodoCategory.UPCOMING, TODAY_END))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.TODAY, TODAY_END))
    }

    @Test
    fun aFinishedOverdueTaskIsOnlyInCompleted() {
        // Reaching back must not drag every task ever finished into TODAY along with the late ones.
        val done = task(id = 1, due = TODAY_START - 30 * DAY, done = true)
        val list = listOf(done)

        assertEquals(listOf(done), categoryFilter(list, TodoCategory.COMPLETED, TODAY_END))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.TODAY, TODAY_END))
    }

    @Test
    fun anUndatedTaskIsOnlyInAll() {
        val undated = task(id = 1, due = null)
        val list = listOf(undated)

        assertEquals(listOf(undated), categoryFilter(list, TodoCategory.ALL, TODAY_END))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.TODAY, TODAY_END))
        assertEquals(emptyList<TodoItem>(), categoryFilter(list, TodoCategory.UPCOMING, TODAY_END))
    }

    @Test
    fun completedHoldsFinishedTasksWhateverTheirDate() {
        val list = listOf(
            task(id = 1, due = TODAY_START - DAY, done = true),
            task(id = 2, due = TODAY_START, done = true),
            task(id = 3, due = TODAY_END + DAY, done = true),
            task(id = 4, due = null, done = true),
            task(id = 5, due = TODAY_START)
        )

        assertEquals(listOf(1, 2, 3, 4), categoryFilter(list, TodoCategory.COMPLETED, TODAY_END).map { it.id })
    }

    @Test
    fun allHoldsEverything() {
        val list = listOf(
            task(id = 1, due = TODAY_START - DAY),
            task(id = 2, due = null, done = true),
            task(id = 3, due = TODAY_END + DAY)
        )

        assertEquals(list, categoryFilter(list, TodoCategory.ALL, TODAY_END))
    }

    @Test
    fun filteringKeepsTheListsOwnOrder() {
        // MANUAL sort is the list order itself, so a filter that reordered would quietly undo every
        // drag the user has made.
        val list = listOf(
            task(id = 3, due = TODAY_START),
            task(id = 1, due = TODAY_START - 5 * DAY),
            task(id = 2, due = TODAY_START - DAY)
        )

        assertEquals(listOf(3, 1, 2), categoryFilter(list, TodoCategory.TODAY, TODAY_END).map { it.id })
    }

    private fun task(id: Int, due: Long?, done: Boolean = false) = TodoItem(
        id = id,
        title = "task $id",
        description = "",
        isDone = done,
        dueDate = due
    )
}

private const val DAY = 24L * 60 * 60 * 1000

/** Midnight tonight — where [categoryFilter] splits what is owed from what is coming. */
private const val TODAY_END = 1_700_000_000_000L
private const val TODAY_START = TODAY_END - DAY
