package com.menu.my.todo.ui.screens

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.menu.my.todo.model.Priority
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.ui.theme.TodoTheme
import com.menu.my.todo.viewmodel.TodoCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    todoList: List<TodoItem>,
    isDarkTheme: Boolean,
    currentCategory: TodoCategory,
    onCategorySelected: (TodoCategory) -> Unit,
    onAddTodoClick: () -> Unit,
    onEditTodoClick: (TodoItem) -> Unit,
    onToggleDone: (TodoItem) -> Unit,
    onDeleteTodo: (Int) -> Unit,
    onToggleTheme: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todo List", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TodoCategory.entries.forEach { category ->
                    NavigationBarItem(
                        selected = currentCategory == category,
                        onClick = { onCategorySelected(category) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        label = { Text(category.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }) },
                        icon = {
                            val icon = when (category) {
                                TodoCategory.TODAY -> Icons.Default.Today
                                TodoCategory.UPCOMING -> Icons.Default.Upcoming
                                TodoCategory.COMPLETED -> Icons.Default.CheckCircle
                                TodoCategory.ALL -> Icons.AutoMirrored.Filled.List
                            }
                            Icon(imageVector = icon, contentDescription = category.name)
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodoClick, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (todoList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(todoList, key = { it.id }) { item ->
                        TodoRow(
                            item = item, 
                            onToggleDone = { onToggleDone(item) },
                            onEdit = { onEditTodoClick(item) },
                            onDelete = { onDeleteTodo(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodoRow(item: TodoItem, onToggleDone: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onEdit() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Checkbox(checked = item.isDone, onCheckedChange = { onToggleDone() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val priorityColor = when (item.priority ?: Priority.LOW) {
                        Priority.HIGH -> Color.Red
                        Priority.MID -> Color(0xFFFFA500) // Orange
                        Priority.LOW -> Color.Green
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(priorityColor, shape = MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.dueDate != null) {
                    val locale = LocalConfiguration.current.locales[0]
                    val sdf = remember(locale) { SimpleDateFormat("MMM dd, yyyy", locale) }
                    Text(
                        text = "Due: ${sdf.format(Date(item.dueDate))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoInputScreen(
    todoItem: TodoItem? = null,
    onSaveTodo: (String, String, Priority, Long?, Long?, RepeatType, Int) -> Unit, 
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(todoItem?.title ?: "") }
    var description by remember { mutableStateOf(todoItem?.description ?: "") }
    var priority by remember { mutableStateOf(todoItem?.priority ?: Priority.LOW) }
    var dueDate by remember { mutableStateOf(todoItem?.dueDate) }
    var reminderTime by remember { mutableStateOf(todoItem?.reminderTime) }
    var repeatType by remember { mutableStateOf(todoItem?.repeatType ?: RepeatType.NONE) }
    var advanceMinutes by remember { mutableIntStateOf(todoItem?.advanceReminderMinutes ?: 0) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun save() = onSaveTodo(title, description, priority, dueDate, reminderTime, repeatType, advanceMinutes)
    val locale = LocalConfiguration.current.locales[0]
    val dateSdf = remember(locale) { SimpleDateFormat("MMM dd, yyyy", locale) }
    val timeSdf = remember(locale) { SimpleDateFormat("HH:mm", locale) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (todoItem == null) "Add Todo" else "Edit Todo", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            // Priority Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name) }
                        )
                    }
                }
            }

            // Date Selection
            Button(
                onClick = {
                    val init = Calendar.getInstance().apply { dueDate?.let { timeInMillis = it } }
                    DatePickerDialog(context, { _, y, m, d ->
                        val cal = Calendar.getInstance().apply {
                            set(y, m, d, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        dueDate = cal.timeInMillis
                        // Keep an already-set reminder on the newly chosen day.
                        reminderTime?.let { rt ->
                            val r = Calendar.getInstance().apply { timeInMillis = rt }
                            cal.set(Calendar.HOUR_OF_DAY, r.get(Calendar.HOUR_OF_DAY))
                            cal.set(Calendar.MINUTE, r.get(Calendar.MINUTE))
                            reminderTime = cal.timeInMillis
                        }
                    }, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (dueDate == null) "Set Due Date" else "Due: ${dateSdf.format(Date(dueDate!!))}")
            }

            // Reminder Selection
            Button(
                onClick = {
                    val init = Calendar.getInstance().apply { reminderTime?.let { timeInMillis = it } }
                    TimePickerDialog(context, { _, h, m ->
                        // Anchor the reminder to the due date if one is set, otherwise today.
                        val cal = Calendar.getInstance().apply { dueDate?.let { timeInMillis = it } }
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, m)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        reminderTime = cal.timeInMillis
                    }, init.get(Calendar.HOUR_OF_DAY), init.get(Calendar.MINUTE), true).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (reminderTime == null) "Set Reminder Time" else "Remind at: ${timeSdf.format(Date(reminderTime!!))}")
            }

            // Repeat Type
            if (reminderTime != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatType.entries.forEach { r ->
                            FilterChip(
                                selected = repeatType == r,
                                onClick = { repeatType = r },
                                label = { Text(r.name) }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = if (advanceMinutes == 0) "" else advanceMinutes.toString(),
                    onValueChange = { advanceMinutes = (it.toIntOrNull() ?: 0).coerceIn(0, MAX_ADVANCE_MINUTES) },
                    label = { Text("Advance reminder (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    // Warn (once) if a reminder is set but the OS will silently drop or delay it.
                    if (reminderTime != null &&
                        (!areNotificationsEnabled(context) || !canScheduleExactAlarms(context))
                    ) {
                        showPermissionDialog = true
                    } else {
                        save()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }

    if (showPermissionDialog) {
        val notificationsEnabled = areNotificationsEnabled(context)
        val message = buildString {
            if (!notificationsEnabled) append("• 通知权限未开启，提醒将不会显示\n")
            if (!canScheduleExactAlarms(context)) append("• 精确闹钟权限未开启，提醒可能被延迟\n")
            append("\n是否前往系统设置开启？")
        }
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("提醒可能无法准时送达") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    openReminderSettings(context, notificationsEnabled)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    save()
                }) { Text("仍然保存") }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TodoListPreview() {
    TodoTheme {
        TodoListScreen(
            todoList = listOf(
                TodoItem(
                    id = 1,
                    title = "Buy milk",
                    description = "Low fat milk from market",
                    isDone = false,
                    priority = Priority.HIGH,
                    dueDate = null,
                    reminderTime = null,
                    repeatType = RepeatType.NONE,
                    advanceReminderMinutes = 0
                ),
                TodoItem(
                    id = 2,
                    title = "Walk the dog",
                    description = "Go to the park",
                    isDone = true,
                    priority = Priority.LOW,
                    dueDate = null,
                    reminderTime = null,
                    repeatType = RepeatType.NONE,
                    advanceReminderMinutes = 0
                )
            ),
            isDarkTheme = false,
            currentCategory = TodoCategory.ALL,
            onCategorySelected = {},
            onAddTodoClick = {},
            onEditTodoClick = {},
            onToggleDone = {},
            onDeleteTodo = {},
            onToggleTheme = {}
        )
    }
}

/** Upper bound for the "advance reminder" field (24h), guarding against absurd / overflowing input. */
private const val MAX_ADVANCE_MINUTES = 1440

private fun areNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

/** Opens the most relevant settings screen: notifications take priority, then exact alarms. */
private fun openReminderSettings(context: Context, notificationsEnabled: Boolean) {
    val intent = when {
        !notificationsEnabled -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null)
        )
        else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
