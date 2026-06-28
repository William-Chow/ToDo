package com.menu.my.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem

class ReminderManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules the reminder for [todo].
     *
     * For repeating reminders we deliberately avoid AlarmManager.setRepeating: since API 19 it is
     * inexact and cannot pierce Doze, and it is silently dropped when the first trigger time is
     * already in the past (e.g. setting a "daily 8:00" reminder in the afternoon). Instead we
     * schedule a single exact alarm for the next *future* occurrence and let ReminderReceiver queue
     * the following cycle once it fires (see [scheduleNext]).
     */
    fun scheduleReminder(todo: TodoItem) {
        val reminderTime = todo.reminderTime ?: return
        val repeatType = todo.repeatType ?: RepeatType.NONE
        val baseTrigger = reminderTime - todo.advanceReminderMinutes.toLong() * MILLIS_PER_MINUTE

        // A one-shot reminder whose time has already passed simply isn't scheduled.
        val triggerTime = nextOccurrence(baseTrigger, repeatType) ?: return
        schedule(todo.id, todo.title, todo.description, repeatType, triggerTime)
    }

    /**
     * Queues the next cycle of a repeating reminder. Called by ReminderReceiver right after it shows
     * a notification, so daily/weekly reminders keep firing exactly without relying on setRepeating.
     */
    fun scheduleNext(
        todoId: Int,
        title: String,
        description: String,
        repeatType: RepeatType,
        lastTrigger: Long
    ) {
        val interval = repeatType.intervalMillis ?: return
        val triggerTime = rollForward(lastTrigger + interval, interval)
        schedule(todoId, title, description, repeatType, triggerTime)
    }

    private fun schedule(
        todoId: Int,
        title: String,
        description: String,
        repeatType: RepeatType,
        triggerTime: Long
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_REPEAT_TYPE, repeatType.name)
            putExtra(EXTRA_TRIGGER_TIME, triggerTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todoId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExact(triggerTime, pendingIntent)
    }

    /** Next future trigger time, or null for a one-shot reminder whose time has already passed. */
    private fun nextOccurrence(baseTrigger: Long, repeatType: RepeatType): Long? {
        val interval = repeatType.intervalMillis
            ?: return baseTrigger.takeIf { it >= System.currentTimeMillis() }
        return rollForward(baseTrigger, interval)
    }

    /** Advances [start] by whole [interval]s until it lands in the future. */
    private fun rollForward(start: Long, interval: Long): Long {
        val now = System.currentTimeMillis()
        if (start >= now) return start
        val cyclesMissed = (now - start) / interval + 1
        return start + cyclesMissed * interval
    }

    /**
     * Schedules a one-shot exact alarm. On Android 12+ (API 31) exact alarms require the
     * SCHEDULE_EXACT_ALARM permission, which can be revoked by the user. If it isn't granted,
     * setExactAndAllowWhileIdle throws SecurityException, so we fall back to an inexact
     * (but still doze-aware) alarm instead of crashing.
     */
    private fun scheduleExact(triggerTime: Long, pendingIntent: PendingIntent) {
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelReminder(todoId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todoId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_TODO_ID = "todoId"
        const val EXTRA_REPEAT_TYPE = "repeatType"
        const val EXTRA_TRIGGER_TIME = "triggerTime"
    }
}

private const val MILLIS_PER_MINUTE = 60_000L

/** Repeat interval in millis, or null for a non-repeating reminder. */
private val RepeatType.intervalMillis: Long?
    get() = when (this) {
        RepeatType.NONE -> null
        RepeatType.DAILY -> AlarmManager.INTERVAL_DAY
        RepeatType.WEEKLY -> AlarmManager.INTERVAL_DAY * 7
    }
