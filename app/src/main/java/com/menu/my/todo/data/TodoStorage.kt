package com.menu.my.todo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menu.my.todo.model.Priority
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem

/**
 * Single access point for the persisted todo list. The ViewModel and both broadcast receivers read
 * (and now write) the same SharedPreferences entry, so the key names and the Gson wiring live here
 * instead of being repeated in three places.
 */
object TodoStorage {
    /** The preference the list is stored under, for callers that watch it for outside changes. */
    const val KEY_TODO_LIST = "todo_list"

    private const val PREFS_NAME = "todo_prefs"

    private val gson = Gson()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The stored list, or null when nothing has been stored yet or the JSON cannot be read. */
    fun load(prefs: SharedPreferences): List<TodoItem>? {
        val json = prefs.getString(KEY_TODO_LIST, null) ?: return null
        return try {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            val list: List<TodoItem> = gson.fromJson(json, type) ?: return null
            // Self-heal nulls from Gson (common when fields are added later)
            list.map {
                it.copy(
                    priority = it.priority ?: Priority.LOW,
                    repeatType = it.repeatType ?: RepeatType.NONE,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun save(prefs: SharedPreferences, todos: List<TodoItem>) {
        prefs.edit { putString(KEY_TODO_LIST, gson.toJson(todos)) }
    }
}
