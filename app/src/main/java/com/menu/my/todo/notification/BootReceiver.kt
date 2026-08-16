package com.menu.my.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.menu.my.todo.data.TodoStorage

/**
 * AlarmManager alarms do not survive a device reboot — Android clears them by design.
 * This receiver listens for BOOT_COMPLETED, reloads the persisted todo list from the same
 * SharedPreferences the ViewModel uses, and hands every task back to ReminderManager, which
 * decides what is still worth an alarm (no reminder time, a trigger already in the past, or a
 * finished one-shot task are all skipped there).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = TodoStorage.prefs(context)
        val todos = TodoStorage.load(prefs) ?: return

        val reminderManager = ReminderManager(context)
        todos.forEach { reminderManager.scheduleReminder(it) }
    }
}
