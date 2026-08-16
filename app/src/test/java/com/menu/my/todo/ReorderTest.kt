package com.menu.my.todo

import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.ui.screens.RowBounds
import com.menu.my.todo.ui.screens.dragTarget
import com.menu.my.todo.ui.screens.edgeScrollSpeed
import com.menu.my.todo.viewmodel.moveIndices

import org.junit.Test

import org.junit.Assert.*

/**
 * Covers the arithmetic behind drag-to-reorder: which row a drag has landed on, how hard the list
 * should be pulled when the row is held against a viewport edge, and what a move does to the list —
 * including the tasks a search or a category filter has hidden from the drag.
 *
 * The gesture itself is not reachable from here; these are the parts of it that are decidable
 * without a finger.
 */
class ReorderTest {

    // ---- Which row the drag has landed on ---------------------------------

    /** Four rows of 100px, laid out from the top of the viewport, as indices 0..3. */
    private val fourRows = (0..3).map { RowBounds(index = it, start = it * 100f, size = 100f) }

    @Test
    fun landingInsideARowPicksThatRow() {
        val target = dragTarget(fourRows, draggedIndex = 3, draggedCenter = 250f, itemCount = 4)

        assertEquals(2, target?.index)
    }

    @Test
    fun aDragThatOutrunsItsNeighbourLandsWhereItActuallyIs() {
        // Half a screen of travel between two drag events must not walk one row at a time.
        val target = dragTarget(fourRows, draggedIndex = 3, draggedCenter = 150f, itemCount = 4)

        assertEquals(1, target?.index)
    }

    @Test
    fun aRowStillOverItsOwnSlotDoesNotMove() {
        val target = dragTarget(fourRows, draggedIndex = 3, draggedCenter = 350f, itemCount = 4)

        assertNull(target)
    }

    @Test
    fun draggingIntoTheEmptySpaceBelowAShortListLandsOnTheLastRow() {
        // The whole list is on screen, so there is nothing below row 3 to scroll to and the drag
        // has to mean "put it last" rather than silently mean nothing.
        val target = dragTarget(fourRows, draggedIndex = 1, draggedCenter = 900f, itemCount = 4)

        assertEquals(3, target?.index)
    }

    @Test
    fun draggingAboveTheTopOfTheListLandsOnTheFirstRow() {
        val target = dragTarget(fourRows, draggedIndex = 2, draggedCenter = -50f, itemCount = 4)

        assertEquals(0, target?.index)
    }

    @Test
    fun draggingPastTheLastVisibleRowWaitsWhenTheListGoesOnBelowIt() {
        // Rows 4..29 are off screen. Clamping onto row 3 here would drop the task in the middle of
        // the list; the auto-scroll is what is supposed to answer this.
        val target = dragTarget(fourRows, draggedIndex = 1, draggedCenter = 900f, itemCount = 30)

        assertNull(target)
    }

    @Test
    fun draggingAboveTheWindowWaitsWhenTheListGoesOnAboveIt() {
        val window = (5..8).map { RowBounds(index = it, start = (it - 5) * 100f, size = 100f) }

        val target = dragTarget(window, draggedIndex = 6, draggedCenter = -50f, itemCount = 30)

        assertNull(target)
    }

    @Test
    fun theFirstRowDraggedOffTheTopHasNowhereToGo() {
        val target = dragTarget(fourRows, draggedIndex = 0, draggedCenter = -200f, itemCount = 4)

        assertNull(target)
    }

    @Test
    fun theLastRowDraggedOffTheBottomHasNowhereToGo() {
        val target = dragTarget(fourRows, draggedIndex = 3, draggedCenter = 900f, itemCount = 4)

        assertNull(target)
    }

    @Test
    fun aWindowWithNothingInItResolvesToNothing() {
        assertNull(dragTarget(emptyList(), draggedIndex = 0, draggedCenter = 0f, itemCount = 0))
    }

    // ---- How hard the list is pulled --------------------------------------

    private fun speed(rowStart: Float, rowEnd: Float, edge: Float = EDGE) = edgeScrollSpeed(
        rowStart = rowStart,
        rowEnd = rowEnd,
        viewportStart = 0f,
        viewportEnd = 1000f,
        edge = edge,
        maxSpeed = MAX_SPEED
    )

    @Test
    fun aRowClearOfBothEdgesDoesNotMoveTheList() {
        assertEquals(0f, speed(rowStart = 400f, rowEnd = 500f), 0f)
    }

    @Test
    fun aRowHeldNearTheBottomPullsTheListForwards() {
        assertEquals(MAX_SPEED / 2, speed(rowStart = 850f, rowEnd = 950f), 0.01f)
    }

    @Test
    fun aRowHeldNearTheTopPullsTheListBackwards() {
        assertEquals(-MAX_SPEED / 2, speed(rowStart = 50f, rowEnd = 150f), 0.01f)
    }

    @Test
    fun theSpeedRampsWithHowFarIntoTheBandTheRowIs() {
        val shallow = speed(rowStart = 825f, rowEnd = 925f)
        val deep = speed(rowStart = 875f, rowEnd = 975f)

        assertEquals(MAX_SPEED / 4, shallow, 0.01f)
        assertEquals(3 * MAX_SPEED / 4, deep, 0.01f)
    }

    @Test
    fun aRowDraggedClearOffTheEdgeGoesNoFasterThanTheTop() {
        assertEquals(MAX_SPEED, speed(rowStart = 1000f, rowEnd = 1100f), 0f)
        assertEquals(-MAX_SPEED, speed(rowStart = -400f, rowEnd = -300f), 0f)
    }

    @Test
    fun theBandIsMeasuredFromTheViewportAndNotFromZero() {
        // A viewport that starts at 200 — content padding, a header — moves the top band with it.
        val inset = edgeScrollSpeed(
            rowStart = 220f,
            rowEnd = 320f,
            viewportStart = 200f,
            viewportEnd = 1000f,
            edge = EDGE,
            maxSpeed = MAX_SPEED
        )

        assertEquals(-MAX_SPEED * 0.8f, inset, 0.01f)
    }

    @Test
    fun noBandMeansNoAutoScroll() {
        assertEquals(0f, speed(rowStart = 990f, rowEnd = 1090f, edge = 0f), 0f)
    }

    // ---- What a move does to the list -------------------------------------

    /** Ids 0..5 as A..F, in that order. */
    private val master = listOf("A", "B", "C", "D", "E", "F")
        .mapIndexed { index, title -> TodoItem(id = index, title = title, description = "") }

    /** What a search or a category filter leaves on screen: A, C and E. */
    private val visibleIds = setOf(0, 2, 4)

    @Test
    fun movingATaskUpPutsItWhereTheTargetWas() {
        assertEquals(listOf("F", "A", "B", "C", "D", "E"), master.movedById(from = 5, to = 0).titles())
    }

    @Test
    fun movingATaskDownPutsItAfterTheTargetAndNotBeforeIt() {
        assertEquals(listOf("B", "C", "D", "E", "A", "F"), master.movedById(from = 0, to = 4).titles())
    }

    @Test
    fun aReorderOnAFilteredViewReordersTheRowsTheUserCanSee() {
        // Dragging E, the third visible row, onto A, the first.
        val moved = master.movedById(from = 4, to = 0)

        assertEquals(listOf("E", "A", "C"), moved.filter { it.id in visibleIds }.titles())
    }

    @Test
    fun aReorderOnAFilteredViewLeavesEveryOtherTaskAlone() {
        val moved = master.movedById(from = 4, to = 0)

        // Take the dragged task out of both and nothing has changed: the tasks the filter hid keep
        // their order, and they keep it relative to the visible ones too.
        assertEquals(master.without(4).titles(), moved.without(4).titles())
        assertEquals(listOf("B", "D", "F"), moved.filter { it.id !in visibleIds }.titles())
    }

    @Test
    fun aReorderDownwardsOnAFilteredViewAlsoLeavesEveryOtherTaskAlone() {
        val moved = master.movedById(from = 0, to = 4)

        assertEquals(master.without(0).titles(), moved.without(0).titles())
        assertEquals(listOf("C", "E", "A"), moved.filter { it.id in visibleIds }.titles())
    }

    @Test
    fun aMoveOntoARowsOwnPlaceIsNoMove() {
        assertNull(moveIndices(master, fromId = 2, toId = 2))
    }

    @Test
    fun aMoveNamingATaskThatIsNoLongerThereIsNoMove() {
        // The row can be deleted, or reloaded away by the reminder receiver, mid-gesture. Nothing
        // downstream may act on a stale position, which is why the move is named by id at all.
        assertNull(moveIndices(master, fromId = 4, toId = 99))
        assertNull(moveIndices(master, fromId = 99, toId = 4))
        assertNull(moveIndices(emptyList(), fromId = 0, toId = 1))
    }

    // ---- Helpers ----------------------------------------------------------

    private fun List<TodoItem>.movedById(from: Int, to: Int): List<TodoItem> {
        val (fromIndex, toIndex) = moveIndices(this, fromId = from, toId = to) ?: return this
        return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    private fun List<TodoItem>.without(id: Int) = filter { it.id != id }

    private fun List<TodoItem>.titles() = map { it.title }

    private companion object {
        const val EDGE = 100f
        const val MAX_SPEED = 2000f
    }
}
