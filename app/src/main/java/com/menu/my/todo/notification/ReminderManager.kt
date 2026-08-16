package com.menu.my.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import java.util.Calendar
import java.util.TimeZone

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
     *
     * Every scheduling path goes through here — the ViewModel and BootReceiver both just hand over a
     * task — so the "is this reminder still live?" rules below are applied consistently instead of
     * being re-stated (and forgotten) at each call site.
     */
    fun scheduleReminder(todo: TodoItem) {
        val reminderTime = todo.reminderTime ?: return
        val repeatType = todo.repeatType ?: RepeatType.NONE
        // A finished one-shot task has nothing left to fire. A repeating one keeps its alarm even
        // when done, because the next occurrence firing is exactly what rolls it forward and clears
        // the tick again (see ReminderReceiver.advanceRepeatingTodo).
        if (todo.isDone && repeatType == RepeatType.NONE) return
        val baseTrigger = reminderTime - todo.advanceReminderMinutes.toLong() * MILLIS_PER_MINUTE

        // A one-shot reminder whose time has already passed simply isn't scheduled.
        val triggerTime = nextOccurrence(baseTrigger, repeatType) ?: return
        schedule(todo.id, todo.title, todo.description, repeatType, triggerTime)
    }

    /**
     * Queues the next cycle of a repeating reminder. Called by ReminderReceiver right after it shows
     * a notification, so daily/weekly reminders keep firing exactly without relying on setRepeating.
     * The next cycle is a calendar step, not a fixed number of milliseconds, so an "every day at
     * 8:00" reminder is still at 8:00 on the far side of a daylight-saving change.
     */
    fun scheduleNext(
        todoId: Int,
        title: String,
        description: String,
        repeatType: RepeatType,
        lastTrigger: Long
    ) {
        if (repeatType.intervalMillis == null) return
        val triggerTime = rollForward(repeatType.occurrenceAfter(lastTrigger, 1), repeatType)
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
        if (repeatType.intervalMillis == null) {
            return baseTrigger.takeIf { it >= System.currentTimeMillis() }
        }
        return rollForward(baseTrigger, repeatType)
    }

    /** Advances [start] by whole repeat cycles until it lands in the future. */
    private fun rollForward(start: Long, repeatType: RepeatType): Long =
        repeatType.occurrenceAtOrAfter(start, System.currentTimeMillis())

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

internal const val MILLIS_PER_MINUTE = 60_000L

/**
 * Nominal repeat interval in millis, or null for a non-repeating reminder. Nominal because a
 * calendar day is 23 or 25 hours long around a daylight-saving change: this is only used to ask
 * "roughly how many cycles is that?", never to move a timestamp — [occurrenceAfter] does that.
 */
internal val RepeatType.intervalMillis: Long?
    get() = when (this) {
        RepeatType.NONE -> null
        RepeatType.DAILY -> AlarmManager.INTERVAL_DAY
        RepeatType.WEEKLY -> AlarmManager.INTERVAL_DAY * 7
    }

/**
 * [start] moved on by [cycles] whole repeat cycles, as a wall-clock step in [zone]: calendar days
 * and weeks, not fixed blocks of milliseconds. Adding milliseconds instead shifts the time of day
 * by an hour across a daylight-saving change, and — because a due date is stored as the midnight
 * that starts the day — that hour is enough to land a daily task on the wrong calendar day.
 */
internal fun RepeatType.occurrenceAfter(
    start: Long,
    cycles: Int,
    zone: TimeZone = TimeZone.getDefault()
): Long {
    if (cycles == 0) return start
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = start }
    when (this) {
        RepeatType.NONE -> return start
        RepeatType.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, cycles)
        RepeatType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, cycles)
    }
    return calendar.timeInMillis
}

/**
 * The first occurrence at or after [now], counting whole cycles from [start] (which is returned
 * unchanged for a non-repeating reminder, or one that is still in the future).
 *
 * The nominal interval only estimates how far ahead that is, and the estimate is deliberately one
 * cycle short: cycles that cross a daylight-saving change are shorter or longer than nominal, so
 * the last step or two is walked one calendar cycle at a time.
 */
internal fun RepeatType.occurrenceAtOrAfter(
    start: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault()
): Long {
    val interval = intervalMillis ?: return start
    if (start >= now) return start
    var cycles = ((now - start) / interval - 1).coerceAtLeast(0L).toInt()
    var occurrence = occurrenceAfter(start, cycles, zone)
    while (occurrence < now) {
        cycles++
        occurrence = occurrenceAfter(start, cycles, zone)
    }
    return occurrence
}
