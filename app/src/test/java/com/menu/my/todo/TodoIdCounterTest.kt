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

    private fun todo(id: Int) = TodoItem(id = id, title = "task $id", description = "")
}
