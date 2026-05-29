package com.menu.my.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.model.RepeatType

class ReminderManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(todo: TodoItem) {
        val reminderTime = todo.reminderTime ?: return
        val triggerTime = reminderTime - (todo.advanceReminderMinutes * 60 * 1000)

        if (triggerTime < System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", todo.title)
            putExtra("description", todo.description)
            putExtra("todoId", todo.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (todo.repeatType ?: RepeatType.NONE) {
            RepeatType.NONE -> scheduleExact(triggerTime, pendingIntent)
            RepeatType.DAILY -> alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            RepeatType.WEEKLY -> alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                AlarmManager.INTERVAL_DAY * 7,
                pendingIntent
            )
        }
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
}
