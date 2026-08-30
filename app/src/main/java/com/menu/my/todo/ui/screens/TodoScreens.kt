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
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.ads.MobileAds
import com.menu.my.todo.BuildConfig
import com.menu.my.todo.R
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
import kotlinx.coroutines.flow.collectLatest
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
    // Read here and not at the call site: the snackbar is raised from a coroutine, which is not a
    // composition and cannot reach resources.
    val deletedMessage = stringResource(R.string.snackbar_task_deleted)
    val undoLabel = stringResource(R.string.snackbar_undo)
    var sortMenuOpen by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }

    // Manual sort is the only order the user owns, so it is the only one that can be dragged.
    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(listState, onMove = onMoveTodo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_bar_title), color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    if (BuildConfig.DEBUG) {
                        // Ad inspector: live view of every ad request, its fill and latency.
                        // Only works on devices registered as test devices.
                        val context = LocalContext.current
                        IconButton(onClick = { MobileAds.openAdInspector(context) { } }) {
                            Icon(Icons.Default.BugReport, contentDescription = stringResource(R.string.action_ad_inspector), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            CheckableMenuItem(stringResource(R.string.sort_manual), SortOrder.MANUAL, currentSort) { onSortChange(it); sortMenuOpen = false }
                            CheckableMenuItem(stringResource(R.string.sort_due_date), SortOrder.DUE_DATE, currentSort) { onSortChange(it); sortMenuOpen = false }
                            CheckableMenuItem(stringResource(R.string.sort_priority), SortOrder.PRIORITY, currentSort) { onSortChange(it); sortMenuOpen = false }
                        }
                    }
                    Box {
                        IconButton(onClick = { themeMenuOpen = true }) {
                            Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.action_theme), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                            CheckableMenuItem(stringResource(R.string.theme_system), ThemeMode.SYSTEM, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            CheckableMenuItem(stringResource(R.string.theme_light), ThemeMode.LIGHT, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            CheckableMenuItem(stringResource(R.string.theme_dark), ThemeMode.DARK, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                CheckableMenuItem(stringResource(R.string.theme_dynamic), ThemeMode.DYNAMIC, themeMode) { onThemeModeChange(it); themeMenuOpen = false }
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
                            label = { Text(stringResource(categoryLabelRes(category))) },
                            icon = {
                                val icon = when (category) {
                                    TodoCategory.TODAY -> Icons.Default.Today
                                    TodoCategory.UPCOMING -> Icons.Default.Upcoming
                                    TodoCategory.COMPLETED -> Icons.Default.CheckCircle
                                    TodoCategory.ALL -> Icons.AutoMirrored.Filled.List
                                }
                                Icon(imageVector = icon, contentDescription = stringResource(categoryLabelRes(category)))
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodoClick, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_task), tint = MaterialTheme.colorScheme.onPrimary)
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
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
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
                    items(todoList, key = { item -> item.id }) { item ->
                        val dragging = reorderState.draggingId == item.id
                        // The gesture key stays constant: the dragged row changes position while the
                        // finger is down, and re-keying here would cancel the drag mid-swap. Its id
                        // does not change, and the id is all the drag needs.
                        val id by rememberUpdatedState(item.id)
                        TodoRow(
                            item = item,
                            onToggleDone = { onToggleDone(item) },
                            onEdit = { onEditTodoClick(item) },
                            onDelete = {
                                onDeleteTodo(item.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
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
                                Modifier
                                    // A tap moves no pointers, so nothing would consume it and it
                                    // would fall through to the Card's clickable and open the
                                    // editor. The handle drags; it does not open anything.
                                    .pointerInput(Unit) { detectTapGestures { } }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { reorderState.onDragStart(id) },
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

// How near a viewport edge a dragged row has to be held before the list starts moving underneath
// it, and how fast the list moves once the row is right at that edge.
private val AUTO_SCROLL_EDGE = 96.dp
private val AUTO_SCROLL_MAX_SPEED = 900.dp // per second
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Builds the [ReorderState] for [listState] and keeps the list moving for as long as a dragged row
 * is held against one of the viewport's edges.
 */
@Composable
private fun rememberReorderState(
    listState: LazyListState,
    onMove: (fromId: Int, toId: Int) -> Unit
): ReorderState {
    val move by rememberUpdatedState(onMove)
    val density = LocalDensity.current
    val edge = with(density) { AUTO_SCROLL_EDGE.toPx() }
    val maxSpeed = with(density) { AUTO_SCROLL_MAX_SPEED.toPx() }
    val state = remember(listState, edge, maxSpeed) {
        ReorderState(listState, edge, maxSpeed) { fromId, toId -> move(fromId, toId) }
    }
    LaunchedEffect(state) { state.autoScrollWhileDragging() }
    return state
}

/**
 * Drag-to-reorder bookkeeping for the manual sort order.
 *
 * The stored list order *is* the manual order, so a drag only has to name the two tasks to [onMove];
 * persisting is the ViewModel's job. A swap is handed over as soon as the dragged row's own midpoint
 * lands inside another row, and a drag that runs off either end of the list lands on the first or
 * last row instead of quietly doing nothing.
 *
 * The row under the finger is followed by task id, and never by position. Everything positional —
 * which row is being dragged, how far it has to be translated — is read back out of the layout the
 * list has *right now*, so the reorder state cannot claim a move the list did not make: if the
 * ViewModel declines the move (the task was deleted, a reminder reloaded the list under the finger)
 * the row simply stays where it is and keeps tracking the finger. The visual follows the commit.
 *
 * Only rows in [listState]'s visible window can be targets, which is why holding a row near a
 * viewport edge scrolls the list — see [autoScrollWhileDragging]. Without that a row could never
 * travel further than one screenful, and on the default sort that is most of the list.
 */
private class ReorderState(
    private val listState: LazyListState,
    private val autoScrollEdge: Float,
    private val autoScrollMaxSpeed: Float,
    private val onMove: (fromId: Int, toId: Int) -> Unit
) {
    /** Id of the task under the finger, or null when no drag is in progress. */
    var draggingId by mutableStateOf<Int?>(null)
        private set

    /** Total vertical finger travel since the drag started. */
    private var draggedDelta by mutableFloatStateOf(0f)

    /** Where in the viewport the row sat, and how tall it was, when the finger went down. */
    private var grabbedAt = 0f
    private var grabbedSize = 0f

    /** Where the dragged row belongs on screen: where the finger has put it, and nothing else. */
    private val drawnAt: Float get() = grabbedAt + draggedDelta

    private val draggedRow: LazyListItemInfo?
        get() = draggingId?.let { id -> rowFor(id) }

    private fun rowFor(id: Int): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    /**
     * Pixels to translate the dragged row by. Derived from the current layout rather than
     * accumulated, so a swap that lands and a swap that does not both leave the row under the finger.
     */
    val dragOffset: Float
        get() = draggedRow?.let { drawnAt - it.offset } ?: 0f

    fun onDragStart(id: Int) {
        val row = rowFor(id) ?: return
        draggingId = id
        grabbedAt = row.offset.toFloat()
        grabbedSize = row.size.toFloat()
        draggedDelta = 0f
    }

    /**
     * Records finger travel and nothing else. Where that leaves the row is worked out once a frame
     * by [autoScrollWhileDragging], against a layout that has actually been through a pass — several
     * pointer events can arrive between two frames, and resolving each of them against the same
     * stale layout would swap more than once for the same movement.
     */
    fun onDrag(delta: Float) {
        if (draggingId == null) return
        draggedDelta += delta
    }

    fun onDragEnd() {
        draggingId = null
        draggedDelta = 0f
    }

    /** Hands over a move once the dragged row's midpoint has landed on another row. */
    private fun swapIfLanded() {
        val fromId = draggingId ?: return
        val row = draggedRow ?: return
        val rows = listState.layoutInfo.visibleItemsInfo
        val target = dragTarget(
            rows = rows.map { RowBounds(it.index, it.offset.toFloat(), it.size.toFloat()) },
            draggedIndex = row.index,
            draggedCenter = drawnAt + row.size / 2f,
            itemCount = listState.layoutInfo.totalItemsCount
        ) ?: return
        val toId = rows.firstOrNull { it.index == target.index }?.key as? Int ?: return

        // A swap across the top of the window has to be pinned by position, not by key. The list
        // keeps its scroll anchored to whichever task was showing first, so moving a task onto that
        // slot slides the anchor down a row and lays the dragged row out just above the window —
        // where it stops being a visible row, which is the one thing every part of this class reads
        // the layout for. The drag would go inert with the finger still down.
        val first = listState.firstVisibleItemIndex
        if (row.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        onMove(fromId, toId)
    }

    /**
     * Drives the whole drag, a frame at a time, for as long as one is in progress: pulls the list
     * along while the row is held near a viewport edge, then works out where the row has landed.
     *
     * Both have to happen every frame rather than on each drag event. The rows slide under a finger
     * that is not itself moving, so the events stop arriving exactly when the reorder needs them
     * most — and they stop for good once the list reaches its end and there is nothing left to
     * scroll, which is precisely when the row is over the first or last slot it was aiming for.
     */
    suspend fun autoScrollWhileDragging() {
        snapshotFlow { draggingId != null }.collectLatest { dragging ->
            if (!dragging) return@collectLatest
            var previousFrame = 0L
            while (true) {
                val step = withFrameNanos { frame ->
                    val seconds =
                        if (previousFrame == 0L) 0f else (frame - previousFrame) / NANOS_PER_SECOND
                    previousFrame = frame
                    currentScrollSpeed() * seconds
                }
                if (step != 0f) listState.scrollBy(step)
                swapIfLanded()
            }
        }
    }

    /**
     * Pixels per second the list should travel under the dragged row, negative towards its start.
     *
     * Measured from where the finger is, not from where the row is laid out, so that a row which
     * has ended up outside the window is scrolled *back into* it rather than stranded there.
     */
    private fun currentScrollSpeed(): Float {
        if (draggingId == null) return 0f
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            rowStart = drawnAt,
            rowEnd = drawnAt + grabbedSize,
            viewportStart = info.viewportStartOffset.toFloat(),
            viewportEnd = info.viewportEndOffset.toFloat(),
            edge = autoScrollEdge,
            maxSpeed = autoScrollMaxSpeed
        )
        return when {
            speed > 0f && !listState.canScrollForward -> 0f
            speed < 0f && !listState.canScrollBackward -> 0f
            else -> speed
        }
    }
}

/** One laid-out row, reduced to what target resolution needs of it. */
internal data class RowBounds(val index: Int, val start: Float, val size: Float) {
    val end: Float get() = start + size
}

/**
 * The row that a dragged row whose midpoint sits at [draggedCenter] should change places with, or
 * null when it should stay where it is. [rows] is the laid-out window in index order, and
 * [itemCount] is the length of the whole list, most of which may not be in it.
 *
 * Landing inside a row picks that row, however far away it is — a fast drag skips rows rather than
 * walking them. Past either end the drag clamps onto the outermost row, but only once that row is
 * the list's own first or last: while there is still list off screen the answer is "not yet", and
 * the auto-scroll brings the rest of it into view.
 */
internal fun dragTarget(
    rows: List<RowBounds>,
    draggedIndex: Int,
    draggedCenter: Float,
    itemCount: Int
): RowBounds? {
    rows.firstOrNull {
        it.index != draggedIndex && draggedCenter >= it.start && draggedCenter <= it.end
    }?.let { return it }

    val first = rows.firstOrNull() ?: return null
    val last = rows.last()
    return when {
        draggedCenter < first.start && first.index == 0 -> first
        draggedCenter > last.end && last.index == itemCount - 1 -> last
        else -> null
    }?.takeIf { it.index != draggedIndex }
}

/**
 * How fast, in pixels per second, the list should travel under a dragged row occupying
 * [rowStart]..[rowEnd] — negative towards the start of the list, zero when the row is clear of both
 * edges. The speed ramps from nothing at [edge] pixels inside the viewport boundary up to [maxSpeed]
 * at the boundary itself, which spreads the slow, aimable part of the range across most of the band.
 */
internal fun edgeScrollSpeed(
    rowStart: Float,
    rowEnd: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edge: Float,
    maxSpeed: Float
): Float {
    if (edge <= 0f) return 0f
    val intoStart = (viewportStart + edge) - rowStart
    val intoEnd = rowEnd - (viewportEnd - edge)
    return when {
        intoEnd > 0f && intoEnd > intoStart -> maxSpeed * (intoEnd / edge).coerceAtMost(1f)
        intoStart > 0f && intoStart > intoEnd -> -maxSpeed * (intoStart / edge).coerceAtMost(1f)
        else -> 0f
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
                    stringResource(R.string.today_progress_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.today_progress_count, done, total),
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
                stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onAddTodoClick) { Text(stringResource(R.string.action_add_task)) }
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
                    if ((item.repeatType ?: RepeatType.NONE) != RepeatType.NONE) {
                        Pill(
                            text = stringResource(repeatLabelRes(item.repeatType)),
                            icon = Icons.Default.Repeat,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Pill(
                        text = stringResource(priorityLabelRes(item.priority)),
                        icon = null,
                        container = priorityColor(item.priority).copy(alpha = 0.16f),
                        content = priorityColor(item.priority)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.row_delete_desc, item.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        contentDescription = stringResource(R.string.row_drag_desc, item.title),
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
    val datePattern = stringResource(R.string.date_format_full)
    val timePattern = stringResource(R.string.time_format)
    val dateSdf = remember(locale, datePattern) { SimpleDateFormat(datePattern, locale) }
    val timeSdf = remember(locale, timePattern) { SimpleDateFormat(timePattern, locale) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (todoItem == null) R.string.editor_title_add else R.string.editor_title_edit),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onPrimary)
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
                label = { Text(stringResource(R.string.field_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.field_description)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Priority Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.field_priority), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(stringResource(priorityLabelRes(p))) }
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
                Text(
                    if (dueDate == null) stringResource(R.string.due_date_set)
                    else stringResource(R.string.due_date_value, dateSdf.format(Date(dueDate!!)))
                )
            }
            if (dueDate != null) {
                TextButton(onClick = { dueDate = null }) { Text(stringResource(R.string.due_date_clear)) }
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
                Text(
                    if (reminderTime == null) stringResource(R.string.reminder_set)
                    else stringResource(R.string.reminder_value, timeSdf.format(Date(reminderTime!!)))
                )
            }
            if (reminderTime != null) {
                TextButton(onClick = {
                    reminderTime = null
                    repeatType = RepeatType.NONE
                    advanceMinutes = 0
                }) { Text(stringResource(R.string.reminder_clear)) }
            }

            // Repeat Type
            if (reminderTime != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.field_repeat), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatType.entries.forEach { r ->
                            FilterChip(
                                selected = repeatType == r,
                                onClick = { repeatType = r },
                                label = { Text(stringResource(repeatLabelRes(r))) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = if (advanceMinutes == 0) "" else advanceMinutes.toString(),
                    onValueChange = { advanceMinutes = (it.toIntOrNull() ?: 0).coerceIn(0, MAX_ADVANCE_MINUTES) },
                    label = { Text(stringResource(R.string.field_advance_minutes)) },
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
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showPermissionDialog) {
        val notificationsEnabled = areNotificationsEnabled(context)
        val exactAlarmsAllowed = canScheduleExactAlarms(context)
        val notificationsWarning = stringResource(R.string.reminder_permission_notifications)
        val exactAlarmWarning = stringResource(R.string.reminder_permission_exact_alarm)
        val settingsPrompt = stringResource(R.string.reminder_permission_prompt)
        val message = buildString {
            if (!notificationsEnabled) append(notificationsWarning)
            if (!exactAlarmsAllowed) append(exactAlarmWarning)
            append(settingsPrompt)
        }
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.reminder_permission_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    openReminderSettings(context, notificationsEnabled)
                }) { Text(stringResource(R.string.reminder_permission_open)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    save()
                }) { Text(stringResource(R.string.reminder_permission_save_anyway)) }
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

@StringRes
private fun categoryLabelRes(category: TodoCategory): Int = when (category) {
    TodoCategory.TODAY -> R.string.category_today
    TodoCategory.UPCOMING -> R.string.category_upcoming
    TodoCategory.COMPLETED -> R.string.category_completed
    TodoCategory.ALL -> R.string.category_all
}

private fun priorityColor(priority: Priority?): Color = when (priority ?: Priority.LOW) {
    Priority.HIGH -> PriorityHigh
    Priority.MID -> PriorityMid
    Priority.LOW -> PriorityLow
}

@StringRes
private fun priorityLabelRes(priority: Priority?): Int = when (priority ?: Priority.LOW) {
    Priority.HIGH -> R.string.priority_high
    Priority.MID -> R.string.priority_mid
    Priority.LOW -> R.string.priority_low
}

/**
 * Every repeat setting has a name, NONE included — the editor's chips have to offer "no repeat" as
 * something to pick, and naming it there is what the raw enum name used to be doing. Whether a row
 * *shows* the name stays the row's own call: a task that does not repeat says nothing about it
 * rather than saying so.
 */
@StringRes
private fun repeatLabelRes(repeatType: RepeatType?): Int = when (repeatType ?: RepeatType.NONE) {
    RepeatType.NONE -> R.string.repeat_none
    RepeatType.DAILY -> R.string.repeat_daily
    RepeatType.WEEKLY -> R.string.repeat_weekly
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

@Composable
private fun dueLabel(dueDate: Long, locale: Locale): String = when (dueDayDiff(dueDate)) {
    0 -> stringResource(R.string.due_today)
    1 -> stringResource(R.string.due_tomorrow)
    -1 -> stringResource(R.string.due_yesterday)
    else -> SimpleDateFormat(stringResource(R.string.date_format_short), locale).format(Date(dueDate))
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
