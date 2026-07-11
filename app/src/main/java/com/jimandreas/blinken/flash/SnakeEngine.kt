package com.jimandreas.blinken.flash

import com.jimandreas.blinken.notification.VisibleSegment

data class GridPos(val x: Int, val y: Int)

// history[0] is the current head position, most-recent-first, capped at SNAKE_HISTORY_CAP so a
// long-running continuous session doesn't grow this list unbounded. horizontalDir/verticalDir are
// always +1 or -1; verticalDir is only consulted when the head needs to step to a new row.
data class SnakeState(
    val head: GridPos,
    val horizontalDir: Int,
    val verticalDir: Int,
    val history: List<GridPos>,
)

const val SNAKE_HISTORY_CAP = 256

fun initialSnakeState(gridWidth: Int, gridHeight: Int): SnakeState {
    require(gridWidth >= 2 && gridHeight >= 2) { "grid must be at least 2x2, was ${gridWidth}x$gridHeight" }
    val head = GridPos(0, 0)
    return SnakeState(head = head, horizontalDir = 1, verticalDir = 1, history = listOf(head))
}

// Boustrophedon ("lawnmower") sweep: the head moves across the current row until it would leave
// the grid horizontally, then steps one row in verticalDir and reverses horizontal direction.
// When stepping a row would itself leave the grid (top/bottom edge reached), verticalDir also
// bounces - producing a continuous back-and-forth sweep of the whole grid rather than a
// perimeter-only loop.
fun stepSnake(state: SnakeState, gridWidth: Int, gridHeight: Int): SnakeState {
    require(gridWidth >= 2 && gridHeight >= 2) { "grid must be at least 2x2, was ${gridWidth}x$gridHeight" }

    val tryX = state.head.x + state.horizontalDir
    val (newHead, newHorizontalDir, newVerticalDir) = if (tryX in 0 until gridWidth) {
        Triple(state.head.copy(x = tryX), state.horizontalDir, state.verticalDir)
    } else {
        val tryY = state.head.y + state.verticalDir
        if (tryY in 0 until gridHeight) {
            Triple(state.head.copy(y = tryY), -state.horizontalDir, state.verticalDir)
        } else {
            val bouncedVertical = -state.verticalDir
            Triple(state.head.copy(y = state.head.y + bouncedVertical), -state.horizontalDir, bouncedVertical)
        }
    }

    return state.copy(
        head = newHead,
        horizontalDir = newHorizontalDir,
        verticalDir = newVerticalDir,
        history = (listOf(newHead) + state.history).take(SNAKE_HISTORY_CAP),
    )
}

data class SnakeSegment(val position: GridPos, val segment: VisibleSegment)

// Zips the oldest-first, already cap-filtered VisibleSegment list (see
// notification.resolveVisibleSegments) against the snake's history (also head-first), so the
// oldest active notification renders at the snake's head. Truncates to whichever list is shorter -
// either the snake hasn't moved enough ticks yet to have a position for every notification, or
// there are fewer notifications than tracked history depth.
fun mapSegments(state: SnakeState, segmentsOldestFirst: List<VisibleSegment>): List<SnakeSegment> {
    val count = minOf(segmentsOldestFirst.size, state.history.size)
    return (0 until count).map { i -> SnakeSegment(state.history[i], segmentsOldestFirst[i]) }
}
