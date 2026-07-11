package com.jimandreas.blinken.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationsStoreTest {

    @Test
    fun `truth-only key is added with its own postedAtMs`() {
        val truth = listOf(ActiveNotification("com.example.mail", "key1", 5000L))
        val result = mergeReconciled(existing = emptyList(), truth = truth)
        assertEquals(truth, result)
    }

    @Test
    fun `existing key preserves its original postedAtMs instead of truth's`() {
        val existing = listOf(ActiveNotification("com.example.mail", "key1", 1000L))
        val truth = listOf(ActiveNotification("com.example.mail", "key1", 9999L))
        val result = mergeReconciled(existing, truth)
        assertEquals(listOf(ActiveNotification("com.example.mail", "key1", 1000L)), result)
    }

    @Test
    fun `key absent from truth is dropped`() {
        val existing = listOf(
            ActiveNotification("com.example.mail", "key1", 1000L),
            ActiveNotification("com.example.chat", "key2", 2000L),
        )
        val truth = listOf(ActiveNotification("com.example.chat", "key2", 2000L))
        val result = mergeReconciled(existing, truth)
        assertEquals(truth, result)
    }

    @Test
    fun `result is sorted oldest-first by postedAtMs`() {
        val truth = listOf(
            ActiveNotification("com.example.chat", "key2", 5000L),
            ActiveNotification("com.example.mail", "key1", 1000L),
        )
        val result = mergeReconciled(existing = emptyList(), truth = truth)
        assertEquals(listOf("key1", "key2"), result.map { it.key })
    }

    @Test
    fun `empty truth clears everything`() {
        val existing = listOf(ActiveNotification("com.example.mail", "key1", 1000L))
        val result = mergeReconciled(existing, truth = emptyList())
        assertEquals(emptyList<ActiveNotification>(), result)
    }
}
