package com.menu.my.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menu.my.todo.model.TodoItem

/**
 * AlarmManager alarms do not survive a device reboot — Android clears them by design.
 * This receiver listens for BOOT_COMPLETED, reloads the persisted todo list from the same
 * SharedPreferences the ViewModel uses, and re-schedules every reminder that still has a
 * reminder time set. ReminderManager already skips alarms whose trigger time is in the past.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("todo_list", null) ?: return

        val todos: List<TodoItem> = try {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            Gson().fromJson(json, type) ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val reminderManager = ReminderManager(context)
        todos.filter { !it.isDone && it.reminderTime != null }
            .forEach { reminderManager.scheduleReminder(it) }
    }
}
