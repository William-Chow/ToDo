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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds
import com.menu.my.todo.ads.InterstitialAdController
import com.menu.my.todo.ui.screens.TodoInputScreen
import com.menu.my.todo.ui.screens.TodoListScreen
import com.menu.my.todo.ui.theme.TodoTheme
import com.menu.my.todo.viewmodel.Screen
import com.menu.my.todo.viewmodel.TodoViewModel
import com.menu.my.todo.viewmodel.millisUntilNextDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Android 13+ (API 33) requires POST_NOTIFICATIONS to be granted at runtime, otherwise
    // reminder notifications are silently suppressed. The result is ignored here: if denied,
    // the app still works, it just won't show notifications until the user grants it in settings.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private lateinit var interstitialAds: InterstitialAdController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        interstitialAds = InterstitialAdController(this)
        // Initializing the ads SDK does disk and network I/O, so keep it off the main thread.
        // Banner requests made before this finishes are queued by the SDK, not dropped.
        lifecycleScope.launch(Dispatchers.IO) {
            MobileAds.initialize(this@MainActivity)
            withContext(Dispatchers.Main) { interstitialAds.preload() }
        }
        setContent {
            val viewModel: TodoViewModel = viewModel()
            // The day can turn over while the app is away — caught up as soon as it is back on
            // screen — or while it is open, which is what the wait for midnight is for. Stopping
            // cancels the wait, so a phone left asleep overnight catches up when it is woken rather
            // than relying on a timer that does not run in deep sleep.
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(viewModel, lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        viewModel.catchUpWithToday()
                        delay(millisUntilNextDay(System.currentTimeMillis()))
                    }
                }
            }
            TodoTheme(themeMode = viewModel.themeMode) {
                TodoApp(viewModel, onTaskSaved = { interstitialAds.onTaskSaved() })
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
fun TodoApp(viewModel: TodoViewModel, onTaskSaved: () -> Unit = {}) {
    when (viewModel.currentScreen) {
        Screen.List -> {
            val (todayDone, todayTotal) = viewModel.todayProgress()
            TodoListScreen(
                todoList = viewModel.getFilteredList(),
                themeMode = viewModel.themeMode,
                currentCategory = viewModel.currentCategory,
                searchQuery = viewModel.searchQuery,
                currentSort = viewModel.currentSort,
                todayStart = viewModel.todayStart,
                todayDone = todayDone,
                todayTotal = todayTotal,
                onSearchChange = { viewModel.searchQuery = it },
                onSortChange = { viewModel.setSort(it) },
                onCategorySelected = { viewModel.setCategory(it) },
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
                onMoveTodo = { fromId, toId -> viewModel.moveTodo(fromId, toId) },
                onThemeModeChange = { viewModel.setTheme(it) }
            )
        }

        Screen.Input -> {
            TodoInputScreen(
                todoItem = viewModel.editingTodo,
                onSaveTodo = { title, desc, priority, dueDate, reminderTime, repeatType, advance ->
                    if (viewModel.editingTodo == null) {
                        viewModel.addTodo(title, desc, priority, dueDate, reminderTime, repeatType, advance)
                    } else {
                        // Merged onto the stored task, not onto the snapshot the editor opened with,
                        // which a reminder firing meanwhile can have left behind.
                        viewModel.saveEditedTodo(title, desc, priority, dueDate, reminderTime, repeatType, advance)
                    }
                    viewModel.currentScreen = Screen.List
                    // Shown after the list is back on screen, never on top of the editor, and
                    // rate limited inside the controller.
                    onTaskSaved()
                },
                onBack = { viewModel.currentScreen = Screen.List }
            )
        }
    }
}
