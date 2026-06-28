package com.menu.my.todo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.menu.my.todo.ui.screens.TodoInputScreen
import com.menu.my.todo.ui.screens.TodoListScreen
import com.menu.my.todo.ui.theme.TodoTheme
import com.menu.my.todo.viewmodel.Screen
import com.menu.my.todo.viewmodel.TodoViewModel

class MainActivity : ComponentActivity() {
    // Android 13+ (API 33) requires POST_NOTIFICATIONS to be granted at runtime, otherwise
    // reminder notifications are silently suppressed. The result is ignored here: if denied,
    // the app still works, it just won't show notifications until the user grants it in settings.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val viewModel: TodoViewModel = viewModel()
            TodoTheme(darkTheme = viewModel.isDarkTheme) {
                TodoApp(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun TodoApp(viewModel: TodoViewModel) {
    when (viewModel.currentScreen) {
        Screen.List -> {
            val (todayDone, todayTotal) = viewModel.todayProgress()
            TodoListScreen(
                todoList = viewModel.getFilteredList(),
                isDarkTheme = viewModel.isDarkTheme,
                currentCategory = viewModel.currentCategory,
                searchQuery = viewModel.searchQuery,
                currentSort = viewModel.currentSort,
                todayDone = todayDone,
                todayTotal = todayTotal,
                onSearchChange = { viewModel.searchQuery = it },
                onSortChange = { viewModel.currentSort = it },
                onCategorySelected = { viewModel.currentCategory = it },
                onAddTodoClick = {
                    viewModel.editingTodo = null
                    viewModel.currentScreen = Screen.Input
                },
                onEditTodoClick = { item ->
                    viewModel.editingTodo = item
                    viewModel.currentScreen = Screen.Input
                },
                onToggleDone = { viewModel.toggleDone(it) },
                onDeleteTodo = { viewModel.deleteTodo(it) },
                onUndoDelete = { viewModel.undoDelete() },
                onToggleTheme = viewModel::toggleTheme
            )
        }

        Screen.Input -> {
            TodoInputScreen(
                todoItem = viewModel.editingTodo,
                onSaveTodo = { title, desc, priority, dueDate, reminderTime, repeatType, advance ->
                    if (viewModel.editingTodo == null) {
                        viewModel.addTodo(title, desc, priority, dueDate, reminderTime, repeatType, advance)
                    } else {
                        viewModel.updateTodo(
                            viewModel.editingTodo!!.copy(
                                title = title,
                                description = desc,
                                priority = priority,
                                dueDate = dueDate,
                                reminderTime = reminderTime,
                                repeatType = repeatType,
                                advanceReminderMinutes = advance
                            )
                        )
                    }
                    viewModel.currentScreen = Screen.List
                },
                onBack = { viewModel.currentScreen = Screen.List }
            )
        }
    }
}
