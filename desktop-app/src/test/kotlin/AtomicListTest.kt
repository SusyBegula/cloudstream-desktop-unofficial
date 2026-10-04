package com.lagradost.cloudstream3.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AtomicListTest {
    @Test
    fun testAtomicListEquality() {
        val list1 = AtomicList(listOf("a", "b", "c"))
        val list2 = AtomicList(listOf("a", "b", "c"))
        val list3 = AtomicList(listOf("a", "b", "d"))

        assertEquals(list1, list2)
        assertEquals(list1, listOf("a", "b", "c"))
        assertNotEquals(list1, list3)
    }

    @Test
    fun testFilterEquality() {
        val original = AtomicList(listOf(1, 2, 3, 4, 5))
        val filtered1 = original.filter { it % 2 == 0 }
        val filtered2 = original.filter { it % 2 == 0 }

        assertEquals(filtered1, filtered2)
        assertEquals(filtered1.hashCode(), filtered2.hashCode())
    }
}
