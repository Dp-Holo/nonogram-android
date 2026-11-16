package com.dsha.nonogram

/**
 * 单局游戏状态：格子内容、撤销栈（支持一次手势整体撤销）、过关判定。
 */
class GameState(val puzzle: Puzzle) {

    val n: Int = puzzle.size
    val cells = IntArray(n * n)

    private class Move(val indices: IntArray, val before: IntArray)

    private val undoStack = ArrayList<Move>(64)

    // 正在进行的“一笔”（拖拽涂色/打叉）会先攒在 stroke 里，抬手时整体入撤销栈
    private var strokeActive = false
    private val strokeIndices = ArrayList<Int>(16)
    private val strokeBefore = ArrayList<Int>(16)

    fun beginStroke() {
        strokeActive = true
        strokeIndices.clear()
        strokeBefore.clear()
    }

    fun endStroke() {
        if (!strokeActive) return
        strokeActive = false
        if (strokeIndices.isNotEmpty()) {
            undoStack.add(Move(strokeIndices.toIntArray(), strokeBefore.toIntArray()))
            if (undoStack.size > 500) undoStack.removeAt(0)
        }
        strokeIndices.clear()
        strokeBefore.clear()
    }

    /** 当前是否有未结束的一笔 */
    fun strokeOpen(): Boolean = strokeActive

    fun set(index: Int, value: Int): Boolean {
        if (index < 0 || index >= cells.size) return false
        if (cells[index] == value) return false
        if (strokeActive) {
            if (!strokeIndices.contains(index)) {
                strokeIndices.add(index)
                strokeBefore.add(cells[index])
            }
        } else {
            undoStack.add(Move(intArrayOf(index), intArrayOf(cells[index])))
            if (undoStack.size > 500) undoStack.removeAt(0)
        }
        cells[index] = value
        return true
    }

    fun undoAvailable(): Boolean = undoStack.isNotEmpty()

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val move = undoStack.removeAt(undoStack.size - 1)
        for (i in move.indices.indices) {
            cells[move.indices[i]] = move.before[i]
        }
        return true
    }

    fun reset() {
        strokeActive = false
        strokeIndices.clear()
        strokeBefore.clear()
        cells.fill(EMPTY)
        undoStack.clear()
    }

    /** 涂色格子与答案完全一致才算过关（多涂、漏涂都不行；打叉不影响） */
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
