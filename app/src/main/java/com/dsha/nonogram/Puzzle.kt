package com.dsha.nonogram

/**
 * 一个可玩的数织关卡。
 *
 * @param key        唯一标识，也是存档用的 key（内置关：builtin_0…；自建关：gen_/img_ 前缀）
 * @param name       显示名称
 * @param size       边长（N×N）
 * @param solution   答案，true = 需要涂色
 * @param builtinIndex 内置关卡的序号，>=0；自建关卡为 -1
 */
class Puzzle(
    val key: String,
    val name: String,
    val size: Int,
    val solution: BooleanArray,
    val builtinIndex: Int = -1
) {

    init {
        require(size > 0 && solution.size == size * size) { "关卡数据不合法: $key" }
    }

    /** 每一行的提示数字 */
    val rowClues: List<List<Int>> = (0 until size).map { r ->
        clueRuns(IntArray(size) { c -> if (solution[r * size + c]) 1 else 0 })
    }

    /** 每一列的提示数字 */
    val colClues: List<List<Int>> = (0 until size).map { c ->
        clueRuns(IntArray(size) { r -> if (solution[r * size + c]) 1 else 0 })
    }

    /** 行提示最多有几个数字（用于布局） */
    val clueWidth: Int = rowClues.maxOfOrNull { it.size } ?: 1

    /** 列提示最多有几个数字 */
    val clueHeight: Int = colClues.maxOfOrNull { it.size } ?: 1

    val filledCount: Int = solution.count { it }

    /** 导出为多行 '#/.' 文本，便于持久化 */
    fun toRowsString(): String = buildString {
        for (r in 0 until size) {
            for (c in 0 until size) append(if (solution[r * size + c]) '#' else '.')
            if (r != size - 1) append('\n')
        }
    }

    companion object {
        /** 从多行 '#/.' 文本构建关卡；格式不对返回 null */
        fun fromRows(key: String, name: String, rows: List<String>, builtinIndex: Int = -1): Puzzle? {
            val lines = rows.map { it.trim() }.filter { it.isNotEmpty() }
            val n = lines.size
            if (n <= 0) return null
            if (lines.any { it.length != n }) return null
            val sol = BooleanArray(n * n)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    sol[r * n + c] = lines[r][c] == '#'
                }
            }
            return Puzzle(key, name, n, sol, builtinIndex)
        }

        /** 由答案位图直接构建关卡 */
        fun fromSolution(key: String, name: String, size: Int, solution: BooleanArray): Puzzle =
            Puzzle(key, name, size, solution)

        /** 把答案位图导出为多行 '#/.' 文本，用于去重比较 */
        fun rowsOf(solution: BooleanArray, size: Int): String = buildString {
            for (r in 0 until size) {
                for (c in 0 until size) append(if (solution[r * size + c]) '#' else '.')
                if (r != size - 1) append('\n')
            }
        }
    }
}

/** 把一行/列的 0/1 序列切成连续涂色块长度；空行返回 [0] */
internal fun clueRuns(values: IntArray): List<Int> {
    val out = ArrayList<Int>(4)
    var count = 0
    for (v in values) {
        if (v != 0) {
            count++
        } else if (count > 0) {
            out.add(count)
            count = 0
        }
    }
    if (count > 0) out.add(count)
    if (out.isEmpty()) out.add(0)
    return out
}
