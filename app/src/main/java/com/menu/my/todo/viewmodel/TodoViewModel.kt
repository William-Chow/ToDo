package com.menu.my.todo.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.menu.my.todo.data.TodoStorage
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
    private val prefs = TodoStorage.prefs(application)
    private val reminderManager = ReminderManager(application)
    // Seeded here rather than at the first add: the stored list is the only record of which ids have
    // been used, and it only shrinks (see [TodoIdCounter]).
    private val idCounter = TodoIdCounter(
        stored = if (prefs.contains(KEY_NEXT_ID)) prefs.getInt(KEY_NEXT_ID, 0) else null,
        existing = TodoStorage.load(prefs).orEmpty(),
        persist = { next -> prefs.edit { putInt(KEY_NEXT_ID, next) } }
    )

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

    // ReminderReceiver rolls repeating tasks onto their next occurrence by writing storage directly,
    // in this same process. Watching the entry keeps the in-memory list from overwriting that on the
    // next edit; loadTodos ignores content that already matches, so our own saves don't bounce back.
    private val storageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TodoStorage.KEY_TODO_LIST) loadTodos()
    }

    init {
        loadTodos()
        prefs.registerOnSharedPreferenceChangeListener(storageListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(storageListener)
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
        val stored = TodoStorage.load(prefs) ?: return
        if (stored == todoList.toList()) return
        todoList.clear()
        todoList.addAll(stored)
    }

    private fun saveTodos() {
        TodoStorage.save(prefs, todoList.toList())
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
        val (_, todayEnd) = todayBounds()

        val byCategory = categoryFilter(todoList, currentCategory, todayEnd)

        val bySearch = if (searchQuery.isBlank()) {
            byCategory
        } else {
            byCategory.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }
        }

        return when (currentSort) {
            // MANUAL is the list's own order, which is what drag-to-reorder rewrites via moveTodo.
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
        val id = idCounter.next()
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
        reminderManager.scheduleReminder(item)
    }

    /**
     * Saves what the editor produced onto the task it was opened for. The editor holds a snapshot
     * taken when it opened, so the merge goes through [mergeEditorResult] instead of writing that
     * snapshot back wholesale.
     */
    fun saveEditedTodo(
        title: String,
        description: String,
        priority: Priority,
        dueDate: Long?,
        reminderTime: Long?,
        repeatType: RepeatType,
        advanceMinutes: Int
    ) {
        val editing = editingTodo ?: return
        val merged = mergeEditorResult(
            current = todoList,
            editing = editing,
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate,
            reminderTime = reminderTime,
            repeatType = repeatType,
            advanceMinutes = advanceMinutes
        ) ?: return
        updateTodo(merged)
    }

    fun updateTodo(updatedItem: TodoItem) {
        val index = todoList.indexOfFirst { it.id == updatedItem.id }
        if (index != -1) {
            reminderManager.cancelReminder(todoList[index].id)
            todoList[index] = updatedItem
            saveTodos()
            reminderManager.scheduleReminder(updatedItem)
        }
    }

    /**
     * Moves the task [fromId] to where [toId] sits — the manual order is the list order itself.
     *
     * Both ends are named by id rather than by position because the list the drag is reading is the
     * filtered, on-screen one, not this one, and it can have changed since the finger went down.
     * That also makes a reorder performed under a search or a category filter well defined: only the
     * dragged task changes position, so every task the filter hid keeps its order relative to every
     * other task, and the manual order the user sees when they clear the filter is the one they left.
     * See [moveIndices].
     */
    fun moveTodo(fromId: Int, toId: Int) {
        val (from, to) = moveIndices(todoList, fromId, toId) ?: return
        todoList.add(to, todoList.removeAt(from))
        saveTodos()
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
        // The snapshot is as stale as the editor's (see [mergeEditorResult]): the list can have been
        // reloaded from storage since the delete. If the id is back in it, an outside write already
        // restored the task, and adding the snapshot too would give two tasks the same id.
        if (todoList.any { it.id == item.id }) return
        todoList.add(index.coerceAtMost(todoList.size), item)
        saveTodos()
        reminderManager.scheduleReminder(item)
    }

    fun toggleDone(item: TodoItem) {
        val updatedItem = item.copy(isDone = !item.isDone)
        updateTodo(updatedItem)
    }
}

/**
 * The take-from and put-back-to positions for moving the task [fromId] onto [toId]'s place in
 * [current], or null when there is nothing to do — either id can have gone from the list since the
 * drag that asked for the move began, and a drag that resolves onto its own row asks for no move.
 *
 * Applying it is `add(to, removeAt(from))`. Removing first is what makes a downward move land
 * *after* the target rather than in front of it, which is what a finger dragged past a row means.
 */
internal fun moveIndices(current: List<TodoItem>, fromId: Int, toId: Int): Pair<Int, Int>? {
    val from = current.indexOfFirst { it.id == fromId }
    val to = current.indexOfFirst { it.id == toId }
    return if (from == -1 || to == -1 || from == to) null else from to to
}

/**
 * The tasks [category] holds, out of [todos], for a day that ends at [todayEnd] (exclusive).
 *
 * TODAY reaches back as well as around the current day. A task whose due date has gone past is not
 * upcoming and is not completed, so bounding TODAY at the start of the day as well left it matching
 * no category at all: it was reachable only under ALL, which is both the longest list to lose it in
 * and the one place its overdue styling was least likely to be looked at. Being late is something
 * the row says; which list a task is in only ever answers "is it still owed".
 *
 * A task with no due date at all belongs to no day and still shows up only under ALL. That is the
 * same hole one filter over, and closing it means deciding where an undated task ought to live
 * rather than moving a boundary, so it is left as it stands.
 */
internal fun categoryFilter(
    todos: List<TodoItem>,
    category: TodoCategory,
    todayEnd: Long
): List<TodoItem> = when (category) {
    TodoCategory.TODAY -> todos.filter { !it.isDone && it.dueDate != null && it.dueDate < todayEnd }
    TodoCategory.UPCOMING -> todos.filter { !it.isDone && it.dueDate != null && it.dueDate >= todayEnd }
    TodoCategory.COMPLETED -> todos.filter { it.isDone }
    TodoCategory.ALL -> todos.toList()
}

/**
 * What a save from the editor should write: the fields the editor owns, applied to the task as it
 * is stored *now*. Returns null when that task has since disappeared from [current].
 *
 * [editing] is the snapshot the editor opened with, and it can be minutes old: ReminderReceiver
 * rolls a repeating task onto its next occurrence — clearing the done tick and moving the dates —
 * in this same process, while the editor sits open. Writing the snapshot back would silently undo
 * that, re-marking the task done. So completion (and anything else the editor cannot change) comes
 * from the stored task outright, and a date the user did not touch keeps the stored value rather
 * than the one the editor was seeded with.
 */
internal fun mergeEditorResult(
    current: List<TodoItem>,
    editing: TodoItem,
    title: String,
    description: String,
    priority: Priority,
    dueDate: Long?,
    reminderTime: Long?,
    repeatType: RepeatType,
    advanceMinutes: Int
): TodoItem? {
    val stored = current.firstOrNull { it.id == editing.id } ?: return null
    return stored.copy(
        title = title,
        description = description,
        priority = priority,
        dueDate = if (dueDate == editing.dueDate) stored.dueDate else dueDate,
        reminderTime = if (reminderTime == editing.reminderTime) stored.reminderTime else reminderTime,
        repeatType = repeatType,
        advanceReminderMinutes = advanceMinutes
    )
}

/**
 * Hands out todo ids. They double as notification ids and PendingIntent request codes, so a new
 * task must not inherit an id a removed task's notification or alarm can still land on.
 *
 * The counter only moves forward and is persisted on every hand-out, so ids are not recycled when
 * the list shrinks. On an install from before the counter existed [stored] is absent and it is
 * seeded once, eagerly, from the ids [existing] holds at that moment — that list is the whole
 * record, and the ids of tasks deleted *before* the upgrade left none, so what those ids were is not
 * knowable from here. What survives of them is exactly one thing: the old policy was "max id + 1",
 * so the ids it had handed out were the contiguous run 0..n, and seeding at max + 1 hands the tail
 * of that run — the whole contiguous stretch above the highest surviving id — out a second time, one
 * id at a time, before the counter reaches ground no task has ever stood on. That is the residual,
 * and it is neither more nor less: an upgraded list holding 0, 1 and 2 whose 3, 4 and 5 were deleted
 * first hands out 3, 4 and 5 again, and an emptied one starts from 0 again. Ids sitting in gaps
 * *below* the highest surviving id are the other side of it — those are never handed out again, so
 * a list holding only 0 and 5 goes on from 6 and 1..4 stay retired for good. Seeding lazily at the
 * first add — as the old "max id + 1" did — would stretch the reused run to cover every task deleted
 * between the upgrade and that add, which is why it happens as soon as the list is read.
 *
 * Once [stored] exists it is authoritative and is never reconciled against [existing], which admits a
 * worse case than the one above: a stored counter sitting *behind* the live list walks into ids that
 * tasks are still holding, so a new task can be handed a live task's id rather than a dead one's.
 * Nothing in the app can produce that — todo_list and next_todo_id share one prefs file, so they
 * cannot desync on their own — it needs corrupt or hand-edited prefs, the same input class the
 * catch-up walk is bounded against.
 */
internal class TodoIdCounter(
    stored: Int?,
    existing: List<TodoItem>,
    private val persist: (Int) -> Unit
) {
    private var next: Int = stored ?: ((existing.maxOfOrNull { it.id } ?: -1) + 1)

    init {
        if (stored == null) persist(next)
    }

    fun next(): Int {
        val id = next
        next = id + 1
        persist(next)
        return id
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_CATEGORY = "category"
private const val KEY_SORT = "sort"
private const val KEY_NEXT_ID = "next_todo_id"
private const val LEGACY_DARK_THEME = "is_dark_theme"
