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

        // A one-shot reminder whose time has already passed simply isn't scheduled.
        val triggerTime = reminderTrigger(
            reminderTime = reminderTime,
            advanceMinutes = todo.advanceReminderMinutes,
            repeatType = repeatType,
            now = System.currentTimeMillis()
        ) ?: return
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
 * When a reminder set for [reminderTime] would actually go off, given that the alarm is set
 * [advanceMinutes] early, or null when no alarm would be set at all.
 *
 * Null is the whole point of asking: a one-shot reminder whose moment has gone is not scheduled,
 * and it is not scheduled *silently* — there is no alarm to cancel and nothing to notice later. The
 * editor asks this before it saves so that it can say so, which is why the rule lives here, on its
 * own, rather than inline in [ReminderManager.scheduleReminder] where the editor could only
 * approximate it and then drift.
 *
 * A repeating reminder never answers null. Its stored time is only the first occurrence, so one
 * already behind [now] means "started in the past", not "over" — [occurrenceAtOrAfter] walks it up
 * to the next one that is still to come.
 */
internal fun reminderTrigger(
    reminderTime: Long,
    advanceMinutes: Int,
    repeatType: RepeatType,
    now: Long,
    zone: TimeZone = TimeZone.getDefault()
): Long? {
    val baseTrigger = reminderTime - advanceMinutes.toLong() * MILLIS_PER_MINUTE
    if (repeatType.intervalMillis == null) return baseTrigger.takeIf { it >= now }
    return repeatType.occurrenceAtOrAfter(baseTrigger, now, zone)
}

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
 *
 * The one time of day this cannot keep is one a spring-forward skips, because on that day it does
 * not exist. Calendar resolves it *backwards*, to the last instant the day does hold: an
 * America/New_York 02:30 daily steps to 01:30 on 2026-03-08, and an America/Santiago due date stored
 * as local midnight steps to 01:00 on 2026-09-06. The calendar day is still the right one, and
 * counting [cycles] from a [start] that was never shifted recovers the time of day the day after
 * (02:30 on 2026-03-09). The live chain does not get that far: [ReminderManager.scheduleNext] and
 * [advanceToOccurrence] each step one cycle from the *previous* occurrence, so the shifted time is
 * the next step's input and the reminder stays an hour early from then on. Restoring the time of day
 * afterwards would only swap that for an hour late — 02:30 is not there to restore, and Calendar
 * resolves it forwards to 03:30 when it is set rather than added — so putting this right means
 * keeping the originally chosen time of day in a stored field it cannot drift out of, which is a
 * change to the task, not to the arithmetic. ReminderGapTest pins what happens today.
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
 * How many calendar cycles [occurrenceAtOrAfter] may walk past its nominal estimate before it treats
 * the start it was given as unusable. The estimate is short by one cycle on purpose and truncating
 * division can take another; past that, the only thing separating n calendar cycles from n nominal
 * intervals is how far the zone's UTC offset moved in between, and no zone in tzdata spans more than
 * about 26 hours of that (Pacific/Apia is the widest, -11:30 to +14:00) — under two daily cycles, and
 * a fraction of a weekly one. Four steps would always be enough; eight leaves room and is still
 * nothing on the main thread.
 *
 * It is not only headroom, though. An estimate can sit right on Int.MAX_VALUE and still be a cycle
 * short, and it is the walk's own `cycles++` that gives out there: the count wraps to Int.MIN_VALUE,
 * the occurrence lands in the far past, and `occurrence < now` then holds for every step after it —
 * a loop with no way back, which clamping the estimate alone does not reach. The bound is what turns
 * that into a return.
 */
private const val MAX_CATCH_UP_STEPS = 8

/**
 * The first occurrence at or after [now], counting whole cycles from [start] (which is returned
 * unchanged for a non-repeating reminder, or one that is still in the future).
 *
 * The nominal interval only estimates how far ahead that is, and the estimate is deliberately one
 * cycle short: cycles that cross a daylight-saving change are shorter or longer than nominal, so
 * the last step or two is walked one calendar cycle at a time — at most [MAX_CATCH_UP_STEPS] of
 * them.
 *
 * Both the estimate and the walk are bounded because [start] is read back from storage. A corrupted
 * or hand-edited file can hold a timestamp so far in the past that the cycle count overruns Int — or
 * that `now - start` overflows outright — and an estimate that wrapped instead of saturating leaves
 * the walk billions of calendar days to cover one Calendar at a time. This runs on BootReceiver's
 * main thread once per stored task, so that is a boot-time ANR rather than a slow reminder. An input
 * the bounds reject falls back to [now]: not the occurrence the cycles say, but still a future alarm,
 * and the cycle queued after it is measured from a timestamp that makes sense again.
 */
internal fun RepeatType.occurrenceAtOrAfter(
    start: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault()
): Long {
    val interval = intervalMillis ?: return start
    if (start >= now) return start
    // start < now, so a negative elapsed is the subtraction wrapping and nothing else.
    val elapsed = now - start
    val estimate = if (elapsed < 0) Long.MAX_VALUE else elapsed / interval - 1
    if (estimate > Int.MAX_VALUE) return now
    var cycles = estimate.coerceAtLeast(0L).toInt()
    var occurrence = occurrenceAfter(start, cycles, zone)
    var steps = 0
    while (occurrence < now) {
        if (++steps > MAX_CATCH_UP_STEPS) return now
        cycles++
        occurrence = occurrenceAfter(start, cycles, zone)
    }
    return occurrence
}
