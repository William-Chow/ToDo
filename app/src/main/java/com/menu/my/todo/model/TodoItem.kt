package com.menu.my.todo.model

enum class Priority {
    LOW, MID, HIGH
}

enum class RepeatType {
    NONE, DAILY, WEEKLY
}

data class TodoItem(
    val id: Int,
    val title: String,
    val description: String,
    val isDone: Boolean = false,
    val priority: Priority? = Priority.LOW,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val repeatType: RepeatType? = RepeatType.NONE,
    val advanceReminderMinutes: Int = 0
)
