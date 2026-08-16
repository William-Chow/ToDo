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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.ads.MobileAds
import com.menu.my.todo.BuildConfig
import com.menu.my.todo.ads.AdBanner
import com.menu.my.todo.model.Priority
import com.menu.my.todo.model.RepeatType
import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.ui.theme.PriorityHigh
import com.menu.my.todo.ui.theme.PriorityLow
import com.menu.my.todo.ui.theme.PriorityMid
import com.menu.my.todo.ui.theme.ThemeMode
import com.menu.my.todo.ui.theme.TodoTheme
import com.menu.my.todo.viewmodel.SortOrder
import com.menu.my.todo.viewmodel.TodoCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    todoList: List<TodoItem>,
    themeMode: ThemeMode,
    currentCategory: TodoCategory,
    searchQuery: String,
    currentSort: SortOrder,
    todayDone: Int,
    todayTotal: Int,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onCategorySelected: (TodoCategory) -> Unit,
    onAddTodoClick: () -> Unit,
    onEditTodoClick: (TodoItem) -> Unit,
    onToggleDone: (TodoItem) -> Unit,
    onDeleteTodo: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onMoveTodo: (fromId: Int, toId: Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }

    // Manual sort is the only order the user owns, so it is the only one that can be dragged.
    val listState = rememberLazyListState()
    val moveByPosition by rememberUpdatedState<(Int, Int) -> Unit> { from, to ->
        val fromId = todoList.getOrNull(from)?.id
        val toId = todoList.getOrNull(to)?.id
        if (fromId != null && toId != null) onMoveTodo(fromId, toId)
    }
    val reorderState = remember(listState) {
        ReorderState(listState) { from, to -> moveByPosition(from, to) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todo List", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    if (BuildConfig.DEBUG) {
                        // Ad inspector: live view of every ad request, its fill and latency.
                        // Only works on devices registered as test devices.
                        val context = LocalContext.current
                        IconButton(onClick = { MobileAds.openAdInspector(context) { } }) {
                            Icon(Icons.Default.BugReport, contentDescription = "Ad Inspector", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            CheckableMenuItem("手动排序", SortOrder.MANUAL, currentSort) { onSortChange(it); sortMenuOpen = false }
                            CheckableMenuItem("按截止日期", SortOrder.DUE_DATE, currentSort) { onSortChange(it); sortMenuOpen = false }
                            CheckableMenuItem("按优先级", SortOrder.PRIORITY, currentSort) { onSortChange(it); sortMenuOpen = false }
                        }
                    }
                    Box {
                        IconButton(onClick = { themeMenuOpen = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "主题", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                            CheckableMenuItem("跟随系统", ThemeMode.SYSTEM, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            CheckableMenuItem("浅色", ThemeMode.LIGHT, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            CheckableMenuItem("深色", ThemeMode.DARK, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                CheckableMenuItem("动态取色", ThemeMode.DYNAMIC, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AdBanner()
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
                            label = { Text(categoryLabel(category)) },
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
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodoClick, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text("搜索任务") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            if (todayTotal > 0) {
                TodayProgress(done = todayDone, total = todayTotal)
            }

            if (todoList.isEmpty()) {
                EmptyState(onAddTodoClick = onAddTodoClick)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(todoList, key = { _, item -> item.id }) { index, item ->
                        val dragging = reorderState.draggingPosition == index
                        // The gesture key stays constant: the dragged row changes position while the
                        // finger is down, and re-keying here would cancel the drag mid-swap.
                        val position by rememberUpdatedState(index)
                        TodoRow(
                            item = item,
                            onToggleDone = { onToggleDone(item) },
                            onEdit = { onEditTodoClick(item) },
                            onDelete = {
                                onDeleteTodo(item.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已删除任务",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                                }
                            },
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (dragging) reorderState.dragOffset else 0f
                                }
                                // The dragged row is placed by hand; the rest slide out of its way.
                                .then(if (dragging) Modifier else Modifier.animateItem()),
                            dragHandleModifier = if (currentSort == SortOrder.MANUAL) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { reorderState.onDragStart(position) },
                                        onDragEnd = { reorderState.onDragEnd() },
                                        onDragCancel = { reorderState.onDragEnd() }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        reorderState.onDrag(dragAmount.y)
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Drag-to-reorder bookkeeping for the manual sort order.
 *
 * The stored list order *is* the manual order, so a drag only has to hand neighbouring positions to
 * [onMove] as the dragged row passes their midpoint; persisting is the ViewModel's job. [dragOffset]
 * is the pixel translation to apply to the row currently under the finger, and it is corrected on
 * every swap so the row stays put while the list moves around it.
 */
private class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingPosition by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(position: Int) {
        draggingPosition = position
        dragOffset = 0f
    }

    fun onDrag(delta: Float) {
        val position = draggingPosition ?: return
        dragOffset += delta

        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.index == position } ?: return
        val draggedCenter = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull {
            it.index != position && draggedCenter >= it.offset && draggedCenter <= it.offset + it.size
        } ?: return

        // The row is about to be laid out where the target sits, so take that jump out of the offset.
        dragOffset += dragged.offset - target.offset
        draggingPosition = target.index
        onMove(position, target.index)
    }

    fun onDragEnd() {
        draggingPosition = null
        dragOffset = 0f
    }
}

@Composable
private fun <T> CheckableMenuItem(
    label: String,
    value: T,
    current: T,
    onPick: (T) -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onPick(value) },
        trailingIcon = {
            if (current == value) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

@Composable
private fun TodayProgress(done: Int, total: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "今日进度",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "$done / $total 完成",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun EmptyState(onAddTodoClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "这里还没有任务",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onAddTodoClick) { Text("添加任务") }
        }
    }
}

@Composable
fun TodoRow(
    item: TodoItem,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // Non-null only in manual sort mode, where the row can be dragged to a new position.
    dragHandleModifier: Modifier? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            // Priority accent bar.
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .padding(start = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (item.isDone) MaterialTheme.colorScheme.outline else priorityColor(item.priority))
            )
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggleDone() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                    color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    if (item.dueDate != null) {
                        val overdue = !item.isDone && isOverdue(item.dueDate)
                        Pill(
                            text = dueLabel(item.dueDate, locale),
                            icon = Icons.Default.Event,
                            container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            content = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    repeatLabel(item.repeatType)?.let { label ->
                        Pill(
                            text = label,
                            icon = Icons.Default.Repeat,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Pill(
                        text = priorityLabel(item.priority),
                        icon = null,
                        container = priorityColor(item.priority).copy(alpha = 0.16f),
                        content = priorityColor(item.priority)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除 ${item.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (dragHandleModifier != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "拖动排序 ${item.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector?, container: Color, content: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content)
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
            if (dueDate != null) {
                TextButton(onClick = { dueDate = null }) { Text("清除截止日期") }
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
            if (reminderTime != null) {
                TextButton(onClick = {
                    reminderTime = null
                    repeatType = RepeatType.NONE
                    advanceMinutes = 0
                }) { Text("清除提醒") }
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
            themeMode = ThemeMode.SYSTEM,
            currentCategory = TodoCategory.ALL,
            searchQuery = "",
            currentSort = SortOrder.MANUAL,
            todayDone = 1,
            todayTotal = 3,
            onSearchChange = {},
            onSortChange = {},
            onCategorySelected = {},
            onAddTodoClick = {},
            onEditTodoClick = {},
            onToggleDone = {},
            onDeleteTodo = {},
            onUndoDelete = {},
            onMoveTodo = { _, _ -> },
            onThemeModeChange = {}
        )
    }
}

// ---- Display helpers ------------------------------------------------------

private const val MAX_ADVANCE_MINUTES = 1440

private fun categoryLabel(category: TodoCategory): String = when (category) {
    TodoCategory.TODAY -> "今天"
    TodoCategory.UPCOMING -> "即将"
    TodoCategory.COMPLETED -> "已完成"
    TodoCategory.ALL -> "全部"
}

private fun priorityColor(priority: Priority?): Color = when (priority ?: Priority.LOW) {
    Priority.HIGH -> PriorityHigh
    Priority.MID -> PriorityMid
    Priority.LOW -> PriorityLow
}

private fun priorityLabel(priority: Priority?): String = when (priority ?: Priority.LOW) {
    Priority.HIGH -> "高"
    Priority.MID -> "中"
    Priority.LOW -> "低"
}

private fun repeatLabel(repeatType: RepeatType?): String? = when (repeatType ?: RepeatType.NONE) {
    RepeatType.NONE -> null
    RepeatType.DAILY -> "每天"
    RepeatType.WEEKLY -> "每周"
}

/** Whole-day difference between [dueDate] and today (negative = past). */
private fun dueDayDiff(dueDate: Long): Int {
    fun midnight(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return ((midnight(dueDate) - midnight(System.currentTimeMillis())) / (24L * 60 * 60 * 1000)).toInt()
}

private fun isOverdue(dueDate: Long): Boolean = dueDayDiff(dueDate) < 0

private fun dueLabel(dueDate: Long, locale: Locale): String = when (dueDayDiff(dueDate)) {
    0 -> "今天"
    1 -> "明天"
    -1 -> "昨天"
    else -> SimpleDateFormat("M月d日", locale).format(Date(dueDate))
}

// ---- Permission helpers ---------------------------------------------------

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
