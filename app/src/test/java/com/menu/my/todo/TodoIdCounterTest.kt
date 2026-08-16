package com.menu.my.todo

import com.menu.my.todo.model.TodoItem
import com.menu.my.todo.viewmodel.TodoIdCounter

import org.junit.Test

import org.junit.Assert.*

/**
 * Covers the promise the id counter makes: an id it has handed out is never handed out again, so a
 * removed task's notification or alarm cannot land on a new one.
 */
class TodoIdCounterTest {
    @Test
    fun aFreshInstallStartsAtZero() {
        var persisted: Int? = null
        val counter = TodoIdCounter(stored = null, existing = emptyList()) { persisted = it }

        assertEquals(0, counter.next())
        assertEquals(1, persisted)
    }

    @Test
    fun anUpgradeStartsPastTheIdsTheListAlreadyHolds() {
        val counter = TodoIdCounter(stored = null, existing = listOf(todo(0), todo(1), todo(2))) {}

        assertEquals(3, counter.next())
    }

    @Test
    fun theSeedIsPersistedBeforeAnyIdIsHandedOut() {
        var persisted: Int? = null
        TodoIdCounter(stored = null, existing = listOf(todo(0), todo(1), todo(2))) { persisted = it }

        assertEquals(3, persisted)
    }

    @Test
    fun emptyingTheListAfterAnUpgradeDoesNotRecycleItsIds() {
        // Upgrade: the counter is seeded from the list as the ViewModel reads it at startup...
        val list = mutableListOf(todo(0), todo(1), todo(2))
        val counter = TodoIdCounter(stored = null, existing = list) {}

        // ...and then the user deletes every task before adding a new one. Reading the list again at
        // that point — which is what "max id + 1" amounts to — would hand out 0 for a second time.
        list.clear()

        assertEquals(3, counter.next())
        assertEquals(4, counter.next())
    }

    @Test
    fun deletingTheLastTaskDoesNotFreeItsIdEither() {
        val list = mutableListOf(todo(0), todo(1), todo(2))
        val counter = TodoIdCounter(stored = null, existing = list) {}

        list.removeAt(2)

        assertEquals(3, counter.next())
    }

    @Test
    fun aStoredCounterIsUsedAsItIs() {
        var persisted: Int? = null
        val counter = TodoIdCounter(stored = 9, existing = listOf(todo(0))) { persisted = it }

        assertNull("nothing to seed, so nothing to write", persisted)
        assertEquals(9, counter.next())
        assertEquals(10, counter.next())
        assertEquals(11, persisted)
    }
}

/**
 * Pins what the upgrade from "max id + 1" leaves behind, in both directions, because
 * [TodoIdCounter]'s KDoc states it exactly and a statement that is wrong is worse than none. Seeding
 * at max + 1 hands out the contiguous run above the highest surviving id a second time — the whole
 * run, not one id of it — and never hands out the gaps below that id at all.
 */
class TodoIdCounterUpgradeTest {
    @Test
    fun theWholeRunAboveTheHighestSurvivingIdComesBack() {
        // The old policy had handed out 0..5; 3, 4 and 5 were deleted before the upgrade.
        val counter = TodoIdCounter(stored = null, existing = listOf(todo(0), todo(1), todo(2))) {}

        assertEquals(listOf(3, 4, 5), (1..3).map { counter.next() })
    }

    @Test
    fun anEmptiedListPutsEveryIdItEverHeldBackInPlay() {
        val counter = TodoIdCounter(stored = null, existing = emptyList()) {}

        assertEquals((0..9).toList(), (1..10).map { counter.next() })
    }

    @Test
    fun idsLeftInGapsBelowTheHighestSurvivingIdAreNotHandedOutAgain() {
        val counter = TodoIdCounter(stored = null, existing = listOf(todo(0), todo(5))) {}

        assertEquals(
            "1..4 were deleted below the maximum, so they stay retired",
            listOf(6, 7, 8, 9, 10, 11),
            (1..6).map { counter.next() }
        )
    }

    /**
     * The counter is authoritative once it exists, even when it is behind the list it came with —
     * so it walks all the way into an id a *live* task is holding, not merely a deleted one's.
     */
    @Test
    fun aStoredCounterBehindTheListIsNotCorrected() {
        val counter = TodoIdCounter(stored = 5, existing = listOf(todo(0), todo(9))) {}

        val handedOut = (1..6).map { counter.next() }

        assertEquals(
            "it is not pulled forward past the ids the list already holds",
            listOf(5, 6, 7, 8, 9, 10),
            handedOut
        )
        assertTrue(
            "9 goes to a new task while the existing task with id 9 is still holding it",
            9 in handedOut
        )
    }

    /** What is persisted is the id to hand out *next*, which is what a later [stored] is read as. */
    @Test
    fun theCounterResumesWhereThePersistedValueLeftOff() {
        var persisted: Int? = null
        val counter = TodoIdCounter(stored = 5, existing = emptyList()) { persisted = it }

        assertEquals(5, counter.next())
        assertEquals(6, persisted)
        assertEquals(6, TodoIdCounter(stored = persisted, existing = emptyList()) {}.next())
    }
}

private fun todo(id: Int) = TodoItem(id = id, title = "task $id", description = "")
