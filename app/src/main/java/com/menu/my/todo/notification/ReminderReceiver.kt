package com.menu.my.todo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.menu.my.todo.MainActivity
import com.menu.my.todo.R
import com.menu.my.todo.data.TodoStorage
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import java.util.TimeZone
import kotlin.math.roundToInt

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE)
            ?: context.getString(R.string.notification_default_title)
        val description = intent.getStringExtra(ReminderManager.EXTRA_DESCRIPTION) ?: ""
        val todoId = intent.getIntExtra(ReminderManager.EXTRA_TODO_ID, 0)
        val repeatType = runCatching {
            RepeatType.valueOf(intent.getStringExtra(ReminderManager.EXTRA_REPEAT_TYPE) ?: RepeatType.NONE.name)
        }.getOrDefault(RepeatType.NONE)
        val triggerTime = intent.getLongExtra(ReminderManager.EXTRA_TRIGGER_TIME, 0L)

        showNotification(context, todoId, title, description)

        // Repeating reminders are scheduled one cycle at a time, so queue the next one now.
        if (repeatType != RepeatType.NONE && triggerTime > 0L) {
            advanceRepeatingTodo(context, todoId, triggerTime)
            ReminderManager(context).scheduleNext(todoId, title, description, repeatType, triggerTime)
        }
    }

    /**
     * Moves the stored task onto the occurrence this alarm is for. Notifying alone leaves the task
     * behind: a completed "daily" task would stay COMPLETED forever, and an untouched one would keep
     * the due date of its very first occurrence.
     *
     * The roll only happens when an alarm fires, so the due date trails the calendar: it names the
     * occurrence that is *ending*, not the one coming up. A daily task with a 22:00 reminder is
     * therefore not in the TODAY filter at noon — it gets there at 22:00. Matching the filter would
     * mean rolling on a date boundary (or lazily, on read) instead, which is more than a broadcast
     * receiver can do.
     */
    private fun advanceRepeatingTodo(context: Context, todoId: Int, triggerTime: Long) {
        val prefs = TodoStorage.prefs(context)
        val todos = TodoStorage.load(prefs) ?: return
        val index = todos.indexOfFirst { it.id == todoId }
        if (index == -1) return

        val advanced = todos[index].advanceToOccurrence(triggerTime) ?: return
        TodoStorage.save(prefs, todos.toMutableList().also { it[index] = advanced })
    }

    private fun showNotification(context: Context, todoId: Int, title: String, description: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "todo_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            todoId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(todoId, notification)
    }
}

/**
 * This task rolled onto the occurrence the alarm at [triggerTime] belongs to: the due date and the
 * reminder time step forward by however many whole repeat cycles have passed since the first
 * trigger, and the task counts as not done again. Returns null when there is nothing to move — the
 * task does not repeat, has no reminder, or the alarm is still the first occurrence's.
 *
 * The steps are calendar days and weeks in [zone] (the device's own by default), so a task lands on
 * every calendar day even across a daylight-saving change, and keeps its time of day — with one
 * exception. A time of day inside the hour a spring-forward skips does not exist on that day, so the
 * step resolves backwards to the last instant the day does hold, and because each roll starts from
 * the value the previous one wrote, the task stays there: an America/New_York 02:30 daily is at
 * 01:30 from 2026-03-08 onwards, not just on it. See [occurrenceAfter].
 */
internal fun TodoItem.advanceToOccurrence(
    triggerTime: Long,
    zone: TimeZone = TimeZone.getDefault()
): TodoItem? {
    val repeat = repeatType ?: RepeatType.NONE
    val interval = repeat.intervalMillis ?: return null
    val reminder = reminderTime ?: return null
    // Alarms are set for the reminder time minus the advance warning, and every later one lands a
    // whole number of cycles after that, so the gap says how far the task has to move. The gap is
    // rounded to whole cycles rather than divided down, because a cycle spanning a daylight-saving
    // change is an hour shorter (or longer) than the nominal interval, and an alarm queued before
    // this fix carries that hour with it.
    val firstTrigger = reminder - advanceReminderMinutes.toLong() * MILLIS_PER_MINUTE
    val cycles = ((triggerTime - firstTrigger).toDouble() / interval).roundToInt()
    if (cycles <= 0) return null

    return copy(
        isDone = false,
        dueDate = dueDate?.let { repeat.occurrenceAfter(it, cycles, zone) },
        reminderTime = repeat.occurrenceAfter(reminder, cycles, zone)
    )
}
