package com.bammellab.blinken.flash

import com.bammellab.blinken.notification.VisibleSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class SnakeEngineTest {

    @Test
    fun `moves one cell in the current horizontal direction mid-row`() {
        val state = SnakeState(head = GridPos(0, 0), horizontalDir = 1, verticalDir = 1, history = listOf(GridPos(0, 0)))
        val next = stepSnake(state, gridWidth = 5, gridHeight = 5)
        assertEquals(GridPos(1, 0), next.head)
        assertEquals(1, next.horizontalDir)
        assertEquals(1, next.verticalDir)
        assertEquals(listOf(GridPos(1, 0), GridPos(0, 0)), next.history)
    }

    @Test
    fun `hitting a side wall not on the last row steps down and reverses horizontal direction`() {
        val state = SnakeState(head = GridPos(2, 0), horizontalDir = 1, verticalDir = 1, history = listOf(GridPos(2, 0)))
        val next = stepSnake(state, gridWidth = 3, gridHeight = 5)
        assertEquals(GridPos(2, 1), next.head)
        assertEquals(-1, next.horizontalDir)
        assertEquals(1, next.verticalDir)
    }

    @Test
    fun `hitting a side wall on the last row bounces both horizontal and vertical direction`() {
        val state = SnakeState(head = GridPos(2, 2), horizontalDir = 1, verticalDir = 1, history = listOf(GridPos(2, 2)))
        val next = stepSnake(state, gridWidth = 3, gridHeight = 3)
        assertEquals(GridPos(2, 1), next.head)
        assertEquals(-1, next.horizontalDir)
        assertEquals(-1, next.verticalDir)
    }

    @Test
    fun `sweep reverses again at the opposite last row, never going out of bounds`() {
        // Drive a small grid through many ticks and confirm every visited head position stays in bounds.
        var state = initialSnakeState(gridWidth = 3, gridHeight = 3)
        repeat(200) {
            state = stepSnake(state, gridWidth = 3, gridHeight = 3)
            assertEquals(true, state.head.x in 0 until 3)
            assertEquals(true, state.head.y in 0 until 3)
        }
    }

    @Test
    fun `history is capped at SNAKE_HISTORY_CAP`() {
        var state = initialSnakeState(gridWidth = 10, gridHeight = 10)
        repeat(SNAKE_HISTORY_CAP + 10) {
            state = stepSnake(state, gridWidth = 10, gridHeight = 10)
        }
        assertEquals(SNAKE_HISTORY_CAP, state.history.size)
    }

    @Test
    fun `mapSegments truncates to history length when there are more segments than tracked positions`() {
        val state = initialSnakeState(gridWidth = 10, gridHeight = 10) // history has exactly 1 position
        val segments = listOf(
            VisibleSegment("com.example.a", "k1", 0xFF0000),
            VisibleSegment("com.example.b", "k2", 0x00FF00),
            VisibleSegment("com.example.c", "k3", 0x0000FF),
        )
        val result = mapSegments(state, segments)
        assertEquals(1, result.size)
        assertEquals(segments[0], result[0].segment)
        assertEquals(state.head, result[0].position)
    }

    @Test
    fun `mapSegments truncates to segment count when history is longer`() {
        var state = initialSnakeState(gridWidth = 10, gridHeight = 10)
        repeat(5) { state = stepSnake(state, gridWidth = 10, gridHeight = 10) } // history now has 6 positions
        val segments = listOf(VisibleSegment("com.example.a", "k1", 0xFF0000))

        val result = mapSegments(state, segments)

        assertEquals(1, result.size)
        assertEquals(state.history[0], result[0].position)
    }

    @Test
    fun `mapSegments assigns the oldest notification to the head position`() {
        var state = initialSnakeState(gridWidth = 10, gridHeight = 10)
        state = stepSnake(state, gridWidth = 10, gridHeight = 10)
        val oldest = VisibleSegment("com.example.a", "k1", 0xFF0000)
        val newest = VisibleSegment("com.example.b", "k2", 0x00FF00)

        val result = mapSegments(state, listOf(oldest, newest))

        assertEquals(state.head, result[0].position)
        assertEquals(oldest, result[0].segment)
        assertEquals(newest, result[1].segment)
    }
}
