package com.menu.my.todo

import com.menu.my.todo.model.Priority
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.viewmodel.mergeEditorResult

import org.junit.Test

import org.junit.Assert.*

/**
 * Covers what a save from the editor writes back. The editor works from a snapshot taken when it
 * opened, and ReminderReceiver can roll the task forward in the meantime, so the merge has to land
 * on the stored task rather than replace it.
 */
class EditorMergeTest {
    private val openedWith = TodoItem(
        id = 7,
        title = "Take pills",
        description = "one blue one white",
        isDone = true,
        dueDate = DAY_1,
        reminderTime = DAY_1 + 22 * HOUR,
        repeatType = RepeatType.DAILY
    )

    /** What the reminder left in storage while the editor sat open: next day, done tick cleared. */
    private val rolledForward = openedWith.copy(
        isDone = false,
        dueDate = DAY_1 + DAY,
        reminderTime = DAY_1 + DAY + 22 * HOUR
    )

    @Test
    fun savingAnEditDoesNotBringBackTheOldCompletion() {
        val saved = merge(title = "Take pills before bed")!!

        assertFalse(saved.isDone)
        assertEquals("Take pills before bed", saved.title)
    }

    @Test
    fun datesTheUserDidNotTouchKeepTheOccurrenceTheyRolledTo() {
        val saved = merge(title = "Take pills before bed")!!

        assertEquals(rolledForward.dueDate, saved.dueDate)
        assertEquals(rolledForward.reminderTime, saved.reminderTime)
    }

    @Test
    fun aDateTheUserPickedWins() {
        val saved = merge(dueDate = DAY_1 + 3 * DAY, reminderTime = DAY_1 + 3 * DAY + 7 * HOUR)!!

        assertEquals(DAY_1 + 3 * DAY, saved.dueDate)
        assertEquals(DAY_1 + 3 * DAY + 7 * HOUR, saved.reminderTime)
    }

    @Test
    fun everythingTheEditorOwnsIsCarriedOver() {
        val saved = merge(
            title = "Take pills before bed",
            description = "just the blue one",
            priority = Priority.HIGH,
            repeatType = RepeatType.WEEKLY,
            advanceMinutes = 15
        )!!

        assertEquals("just the blue one", saved.description)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(RepeatType.WEEKLY, saved.repeatType)
        assertEquals(15, saved.advanceReminderMinutes)
        assertEquals(openedWith.id, saved.id)
    }

    @Test
    fun aTaskDeletedWhileTheEditorWasOpenIsNotResurrected() {
        val saved = mergeEditorResult(
            current = emptyList(),
            editing = openedWith,
            title = "Take pills before bed",
            description = openedWith.description,
            priority = Priority.LOW,
            dueDate = openedWith.dueDate,
            reminderTime = openedWith.reminderTime,
            repeatType = RepeatType.DAILY,
            advanceMinutes = 0
        )

        assertNull(saved)
    }

    /** A save from the editor, whose fields are seeded from [openedWith] unless overridden here. */
    private fun merge(
        title: String = openedWith.title,
        description: String = openedWith.description,
        priority: Priority = Priority.LOW,
        dueDate: Long? = openedWith.dueDate,
        reminderTime: Long? = openedWith.reminderTime,
        repeatType: RepeatType = RepeatType.DAILY,
        advanceMinutes: Int = 0
    ): TodoItem? = mergeEditorResult(
        current = listOf(rolledForward),
        editing = openedWith,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        reminderTime = reminderTime,
        repeatType = repeatType,
        advanceMinutes = advanceMinutes
    )
}

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR
private const val DAY_1 = 1_700_000_000_000L
