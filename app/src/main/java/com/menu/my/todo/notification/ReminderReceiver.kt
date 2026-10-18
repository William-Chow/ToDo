package com.menu.my.todo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
     * On its own, rolling here would leave the due date trailing the calendar: it would name the
     * occurrence that is *ending* until the next one's alarm, so a daily task with a 22:00 reminder
     * would spend every day until 22:00 marked overdue from yesterday. The app moves the task on when
     * the occurrence's day starts instead (see [rolledOntoDay]), which makes this roll a no-op for an
     * occurrence the task has already reached. It still does the job on a day the app is never
     * opened.
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

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

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
        completedAt = null,
        dueDate = dueDate?.let { repeat.occurrenceAfter(it, cycles, zone) },
        reminderTime = repeat.occurrenceAfter(reminder, cycles, zone)
    )
}

/**
 * This task moved onto the latest occurrence whose day has begun by [todayStart]: the due date and
 * the reminder time step forward by whole repeat cycles, and the task counts as not done again.
 * Returns null when there is nothing to move — the task does not repeat, has neither date, or is
 * already on the current occurrence (or ahead of it, as an advance warning that reaches back past
 * midnight can leave it).
 *
 * This is [advanceToOccurrence] brought forward from the alarm to the start of the day, so that on
 * the day of an occurrence the task names that occurrence. Without it a daily task due yesterday
 * would read as overdue all day until its reminder fired, and one ticked off yesterday would sit in
 * COMPLETED through the day it is next owed. An occurrence the user did not get to is replaced, not
 * kept as a backlog, exactly as the alarm's roll has always done.
 *
 * Cycles are counted in calendar days from the due date — or from the reminder for an undated task —
 * so a weekly task moves only once the next occurrence's day arrives; until then a missed one stays
 * overdue, which it is.
 */
internal fun TodoItem.rolledOntoDay(
    todayStart: Long,
    zone: TimeZone = TimeZone.getDefault()
): TodoItem? {
    val repeat = repeatType ?: RepeatType.NONE
    val anchor = dueDate ?: reminderTime ?: return null
    val days = calendarDaysBetween(anchor, todayStart, zone)
    val cycles = when (repeat) {
        RepeatType.NONE -> return null
        RepeatType.DAILY -> days
        RepeatType.WEEKLY -> days / 7
    }
    // Past Int, the anchor is a corrupt timestamp rather than a missed occurrence; leave it alone.
    if (cycles <= 0 || cycles > Int.MAX_VALUE) return null

    return copy(
        isDone = false,
        completedAt = null,
        dueDate = dueDate?.let { repeat.occurrenceAfter(it, cycles.toInt(), zone) },
        reminderTime = reminderTime?.let { repeat.occurrenceAfter(it, cycles.toInt(), zone) }
    )
}
