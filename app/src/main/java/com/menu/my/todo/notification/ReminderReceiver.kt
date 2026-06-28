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
import com.menu.my.todo.model.RepeatType

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE) ?: "Todo Reminder"
        val description = intent.getStringExtra(ReminderManager.EXTRA_DESCRIPTION) ?: ""
        val todoId = intent.getIntExtra(ReminderManager.EXTRA_TODO_ID, 0)
        val repeatType = runCatching {
            RepeatType.valueOf(intent.getStringExtra(ReminderManager.EXTRA_REPEAT_TYPE) ?: RepeatType.NONE.name)
        }.getOrDefault(RepeatType.NONE)
        val triggerTime = intent.getLongExtra(ReminderManager.EXTRA_TRIGGER_TIME, 0L)

        showNotification(context, todoId, title, description)

        // Repeating reminders are scheduled one cycle at a time, so queue the next one now.
        if (repeatType != RepeatType.NONE && triggerTime > 0L) {
            ReminderManager(context).scheduleNext(todoId, title, description, repeatType, triggerTime)
        }
    }

    private fun showNotification(context: Context, todoId: Int, title: String, description: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "todo_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Todo Reminders",
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
