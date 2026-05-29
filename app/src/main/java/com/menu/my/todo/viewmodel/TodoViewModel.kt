package com.menu.my.todo.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.model.Priority
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.notification.ReminderManager
import java.util.Calendar

enum class Screen {
    List, Input
}

enum class TodoCategory {
    TODAY, UPCOMING, COMPLETED, ALL
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val reminderManager = ReminderManager(application)
    
    val todoList = mutableStateListOf<TodoItem>()

    var isDarkTheme by mutableStateOf(value = false)
        private set

    var currentCategory by mutableStateOf(TodoCategory.ALL)

    var currentScreen by mutableStateOf(Screen.List)
    var editingTodo by mutableStateOf<TodoItem?>(null)

    init {
        loadTodos()
        isDarkTheme = prefs.getBoolean("is_dark_theme", false)
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        prefs.edit { putBoolean("is_dark_theme", isDarkTheme) }
    }

    private fun loadTodos() {
        val json = prefs.getString("todo_list", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<TodoItem>>() {}.type
                val list: List<TodoItem> = gson.fromJson(json, type)
                // Self-heal nulls from Gson (common when fields are added later)
                val cleanList = list.map { 
                    it.copy(
                        priority = it.priority ?: Priority.LOW,
                        repeatType = it.repeatType ?: RepeatType.NONE,
                    )
                }
                todoList.clear()
                todoList.addAll(cleanList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveTodos() {
        val json = gson.toJson(todoList.toList())
        prefs.edit { putString("todo_list", json) }
    }

    fun getFilteredList(): List<TodoItem> {
        val now = Calendar.getInstance()
        val todayStart = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + (24 * 60 * 60 * 1000)

        return when (currentCategory) {
            TodoCategory.TODAY -> todoList.filter { 
                !it.isDone && it.dueDate != null && it.dueDate in todayStart until todayEnd 
            }
            TodoCategory.UPCOMING -> todoList.filter { 
                !it.isDone && it.dueDate != null && it.dueDate >= todayEnd 
            }
            TodoCategory.COMPLETED -> todoList.filter { it.isDone }
            TodoCategory.ALL -> todoList
        }
    }

    fun addTodo(
        title: String, 
        description: String, 
        priority: Priority = Priority.LOW,
        dueDate: Long? = null,
        reminderTime: Long? = null,
        repeatType: RepeatType = RepeatType.NONE,
        advanceMinutes: Int = 0
    ) {
        val id = if (todoList.isEmpty()) 0 else todoList.maxOf { it.id } + 1
        val item = TodoItem(
            id = id, 
            title = title, 
            description = description,
            isDone = false,
            priority = priority,
            dueDate = dueDate,
            reminderTime = reminderTime,
            repeatType = repeatType,
            advanceReminderMinutes = advanceMinutes
        )
        todoList.add(item)
        saveTodos()
        if (item.reminderTime != null) {
            reminderManager.scheduleReminder(item)
        }
    }

    fun updateTodo(updatedItem: TodoItem) {
        val index = todoList.indexOfFirst { it.id == updatedItem.id }
        if (index != -1) {
            reminderManager.cancelReminder(todoList[index].id)
            todoList[index] = updatedItem
            saveTodos()
            if (updatedItem.reminderTime != null) {
                reminderManager.scheduleReminder(updatedItem)
            }
        }
    }

    fun deleteTodo(todoId: Int) {
        val index = todoList.indexOfFirst { it.id == todoId }
        if (index != -1) {
            reminderManager.cancelReminder(todoId)
            todoList.removeAt(index)
            saveTodos()
        }
    }

    fun toggleDone(item: TodoItem) {
        val updatedItem = item.copy(isDone = !item.isDone)
        updateTodo(updatedItem)
    }
}
