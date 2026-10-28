package com.menu.my.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.menu.my.todo.R
import com.menu.my.todo.data.TodoStorage
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.viewmodel.withDone

/**
 * The two buttons on a reminder notification: done, and remind me again in [SNOOZE_MINUTES].
 *
 * Either way the notification goes, since it has been answered. Done writes the task to storage
 * directly, as ReminderReceiver does when it rolls a repeating task on; the list watches that entry,
 * so an open app shows the tick at once.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Ids start at 0, so a missing id needs a value no task can have.
        val todoId = intent.getIntExtra(ReminderManager.EXTRA_TODO_ID, -1)
        if (todoId < 0) return
        val now = System.currentTimeMillis()
        val reminders = ReminderManager(context)

        when (intent.action) {
            ACTION_DONE -> {
                val prefs = TodoStorage.prefs(context)
                TodoStorage.load(prefs)?.withTaskDone(todoId, now)?.let { TodoStorage.save(prefs, it) }
                // Done answers every showing of this reminder, including one snoozed from an
                // earlier notification that would otherwise bring a finished task back up.
                reminders.cancelSnooze(todoId)
            }
            ACTION_SNOOZE -> reminders.snooze(
                todoId,
                title = intent.getStringExtra(ReminderManager.EXTRA_TITLE)
                    ?: context.getString(R.string.notification_default_title),
                description = intent.getStringExtra(ReminderManager.EXTRA_DESCRIPTION) ?: "",
                triggerTime = snoozedUntil(now)
            )
            else -> return
        }
        NotificationManagerCompat.from(context).cancel(todoId)
    }

    companion object {
        const val ACTION_DONE = "com.menu.my.todo.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.menu.my.todo.action.REMINDER_SNOOZE"
    }
}

/** How long the snooze button puts a reminder off. */
internal const val SNOOZE_MINUTES = 10

/** When a reminder snoozed at [now] goes off again. */
internal fun snoozedUntil(now: Long): Long = now + SNOOZE_MINUTES * MILLIS_PER_MINUTE

/**
 * These tasks with [todoId] ticked off at [now], or null when there is nothing to write: the task
 * has been deleted since its reminder went off, or was already ticked off in the app — in which case
 * its first completion time is the one worth keeping.
 *
 * Only the tick changes. A repeating task keeps its dates and its alarm, and the next occurrence
 * makes it owed again as it always does.
 */
internal fun List<TodoItem>.withTaskDone(todoId: Int, now: Long): List<TodoItem>? {
    val index = indexOfFirst { it.id == todoId }
    if (index == -1 || this[index].isDone) return null
    return toMutableList().also { it[index] = it[index].withDone(true, now) }
}
