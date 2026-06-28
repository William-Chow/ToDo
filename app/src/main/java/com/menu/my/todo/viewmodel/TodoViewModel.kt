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
import com.menu.my.todo.ui.theme.ThemeMode
import java.util.Calendar

enum class Screen {
    List, Input
}

enum class TodoCategory {
    TODAY, UPCOMING, COMPLETED, ALL
}

enum class SortOrder {
    MANUAL, DUE_DATE, PRIORITY
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val reminderManager = ReminderManager(application)
    
    val todoList = mutableStateListOf<TodoItem>()

    var themeMode by mutableStateOf(loadThemeMode())
        private set

    var currentCategory by mutableStateOf(loadCategory())
        private set
    var searchQuery by mutableStateOf("")
    var currentSort by mutableStateOf(loadSort())
        private set

    var currentScreen by mutableStateOf(Screen.List)
    var editingTodo by mutableStateOf<TodoItem?>(null)

    // Holds the most recently deleted item (with its position) so a delete can be undone.
    private var lastDeleted: Pair<Int, TodoItem>? = null

    init {
        loadTodos()
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setCategory(category: TodoCategory) {
        currentCategory = category
        prefs.edit { putString(KEY_CATEGORY, category.name) }
    }

    fun setSort(order: SortOrder) {
        currentSort = order
        prefs.edit { putString(KEY_SORT, order.name) }
    }

    private fun loadThemeMode(): ThemeMode {
        prefs.getString(KEY_THEME_MODE, null)?.let { stored ->
            return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
        }
        // Migrate the previous boolean dark-theme flag, if one was stored.
        return when {
            !prefs.contains(LEGACY_DARK_THEME) -> ThemeMode.SYSTEM
            prefs.getBoolean(LEGACY_DARK_THEME, false) -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }
    }

    private fun loadCategory(): TodoCategory =
        runCatching { TodoCategory.valueOf(prefs.getString(KEY_CATEGORY, "") ?: "") }
            .getOrDefault(TodoCategory.ALL)

    private fun loadSort(): SortOrder =
        runCatching { SortOrder.valueOf(prefs.getString(KEY_SORT, "") ?: "") }
            .getOrDefault(SortOrder.MANUAL)

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

    /** Start (inclusive) and end (exclusive) timestamps of the current calendar day. */
    private fun todayBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to start + MILLIS_PER_DAY
    }

    /** Tasks due today as (done, total) — drives the "today's progress" header. */
    fun todayProgress(): Pair<Int, Int> {
        val (start, end) = todayBounds()
        val todays = todoList.filter { it.dueDate != null && it.dueDate in start until end }
        return todays.count { it.isDone } to todays.size
    }

    fun getFilteredList(): List<TodoItem> {
        val (todayStart, todayEnd) = todayBounds()

        val byCategory = when (currentCategory) {
            TodoCategory.TODAY -> todoList.filter {
                !it.isDone && it.dueDate != null && it.dueDate in todayStart until todayEnd
            }
            TodoCategory.UPCOMING -> todoList.filter {
                !it.isDone && it.dueDate != null && it.dueDate >= todayEnd
            }
            TodoCategory.COMPLETED -> todoList.filter { it.isDone }
            TodoCategory.ALL -> todoList.toList()
        }

        val bySearch = if (searchQuery.isBlank()) {
            byCategory
        } else {
            byCategory.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }
        }

        return when (currentSort) {
            SortOrder.MANUAL -> bySearch
            SortOrder.DUE_DATE -> bySearch.sortedWith(compareBy(nullsLast()) { it.dueDate })
            SortOrder.PRIORITY -> bySearch.sortedByDescending { (it.priority ?: Priority.LOW).ordinal }
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
            lastDeleted = index to todoList[index]
            reminderManager.cancelReminder(todoId)
            todoList.removeAt(index)
            saveTodos()
        }
    }

    /** Restores the most recently deleted task to its original position. */
    fun undoDelete() {
        val (index, item) = lastDeleted ?: return
        lastDeleted = null
        todoList.add(index.coerceAtMost(todoList.size), item)
        saveTodos()
        if (item.reminderTime != null) {
            reminderManager.scheduleReminder(item)
        }
    }

    fun toggleDone(item: TodoItem) {
        val updatedItem = item.copy(isDone = !item.isDone)
        updateTodo(updatedItem)
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_CATEGORY = "category"
private const val KEY_SORT = "sort"
private const val LEGACY_DARK_THEME = "is_dark_theme"
