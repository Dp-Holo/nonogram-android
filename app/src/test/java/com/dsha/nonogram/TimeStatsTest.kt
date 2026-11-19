package com.dsha.nonogram

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeStatsTest {

    @Test
    fun emptyListGivesZero() {
        val s = TimeStats.summarize(emptyList())
        assertEquals(0, s.count)
        assertEquals(0L, s.meanMs)
        assertEquals(0L, s.medianMs)
        assertEquals(0L, s.bestMs)
    }

    @Test
    fun oddCountMedianIsMiddle() {
        val s = TimeStats.summarize(listOf(10L, 30L, 20L))
        assertEquals(3, s.count)
        assertEquals(20L, s.meanMs)
        assertEquals(20L, s.medianMs)
        assertEquals(10L, s.bestMs)
    }

    @Test
    fun evenCountMedianAveragesMiddleTwo() {
        val s = TimeStats.summarize(listOf(40L, 10L, 30L, 20L))
        assertEquals(4, s.count)
        assertEquals(25L, s.meanMs)
        assertEquals(25L, s.medianMs)
        assertEquals(10L, s.bestMs)
    }

    @Test
    fun meanRoundsDown() {
        val s = TimeStats.summarize(listOf(1L, 2L))
        assertEquals(1L, s.meanMs)
        assertEquals(1L, s.medianMs)
    }
}
