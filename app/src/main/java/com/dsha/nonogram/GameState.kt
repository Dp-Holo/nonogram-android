package com.dsha.nonogram

/**
 * 单局游戏状态：格子内容、撤销栈、过关判定。
 */
class GameState(val puzzle: Puzzles.Puzzle) {

    val n: Int = puzzle.size
    val cells = IntArray(n * n)

    private val undoStack = ArrayList<IntArray>(64)

    fun set(index: Int, value: Int): Boolean {
        if (index < 0 || index >= cells.size) return false
        if (cells[index] == value) return false
        undoStack.add(intArrayOf(index, cells[index], value))
        if (undoStack.size > 2000) undoStack.removeAt(0)
        cells[index] = value
        return true
    }

    fun undoAvailable(): Boolean = undoStack.isNotEmpty()

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val move = undoStack.removeAt(undoStack.size - 1)
        cells[move[0]] = move[1]
        return true
    }

    fun reset() {
        cells.fill(EMPTY)
        undoStack.clear()
    }

    /** 涂色格子与答案完全一致才算过关（多涂、漏涂都不行）。 */
    fun isSolved(): Boolean {
        for (i in cells.indices) {
            if (puzzle.solution[i]) {
                if (cells[i] != FILLED) return false
            } else if (cells[i] == FILLED) {
                return false
            }
        }
        return true
    }

    companion object {
        const val EMPTY = 0
        const val FILLED = 1
        const val MARKED = 2
    }
}
