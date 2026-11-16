package com.dsha.nonogram

import java.util.Random

/**
 * 随机关卡生成器。
 *
 * 思路：随机涂色 -> 计算行列提示 -> 用「行/列约束传播」求解。
 * 如果只用逻辑推理（单行单列推导）就能推出所有格子，那么这个解就是唯一的，
 * 玩家一定能靠推理解出来，不需要猜。生成失败则重试。
 */
object Generator {

    const val UNKNOWN = 0
    const val FILLED = 1
    const val EMPTY = 2

    /** 生成一张 size×size 的答案位图；失败返回 null */
    fun generate(size: Int, random: Random = Random()): BooleanArray? {
        if (size < 3 || size > 30) return null
        val rowClues = ArrayList<List<Int>>(size)
        val colClues = ArrayList<List<Int>>(size)

        for (round in 0 until 4) {
            // 密度从高到低：密度高时提示更明确，更容易唯一可解
            val density = 0.62 - round * 0.035
            repeat(30) {
                val sol = BooleanArray(size * size) { random.nextDouble() < density }
                val ratio = sol.count { it }.toDouble() / (size * size)
                if (ratio < 0.28 || ratio > 0.80) return@repeat

                rowClues.clear()
                colClues.clear()
                for (r in 0 until size) {
                    rowClues.add(clueRuns(IntArray(size) { c -> if (sol[r * size + c]) 1 else 0 }))
                }
                for (c in 0 until size) {
                    colClues.add(clueRuns(IntArray(size) { r -> if (sol[r * size + c]) 1 else 0 }))
                }

                val state = IntArray(size * size)
                if (propagate(size, rowClues, colClues, state)) return sol
            }
        }
        return null
    }

    /**
     * 行列约束传播。
     * @return true 表示所有格子都被推出来（= 逻辑可解 = 唯一解）
     */
    fun propagate(
        size: Int,
        rowClues: List<List<Int>>,
        colClues: List<List<Int>>,
        state: IntArray
    ): Boolean {
        val line = IntArray(size)
        val out = IntArray(size)
        var guard = 0
        val maxRounds = 8 * size
        while (guard++ < maxRounds) {
            var changed = false

            for (r in 0 until size) {
                for (c in 0 until size) line[c] = state[r * size + c]
                if (!analyzeLine(line, rowClues[r], out)) return false
                for (c in 0 until size) {
                    val idx = r * size + c
                    if (state[idx] == UNKNOWN && out[c] != UNKNOWN) {
                        state[idx] = out[c]
                        changed = true
                    }
                }
            }

            for (c in 0 until size) {
                for (r in 0 until size) line[r] = state[r * size + c]
                if (!analyzeLine(line, colClues[c], out)) return false
                for (r in 0 until size) {
                    val idx = r * size + c
                    if (state[idx] == UNKNOWN && out[r] != UNKNOWN) {
                        state[idx] = out[r]
                        changed = true
                    }
                }
            }

            if (!changed) break
        }
        for (v in state) if (v == UNKNOWN) return false
        return true
    }

    /**
     * 分析单行：在给定提示下，每个未知格是「必填 / 必空 / 未定」。
     * @return false 表示与提示矛盾（无解）
     */
    private fun analyzeLine(line: IntArray, clue: List<Int>, out: IntArray): Boolean {
        val n = line.size
        if (countWays(line, clue) == 0L) return false

        if (clue.size == 1 && clue[0] == 0) {
            for (i in 0 until n) out[i] = EMPTY
            return true
        }

        val work = line.copyOf()
        for (i in 0 until n) {
            if (work[i] != UNKNOWN) {
                out[i] = work[i]
                continue
            }
            work[i] = FILLED
            val canFill = countWays(work, clue) > 0L
            work[i] = EMPTY
            val canEmpty = countWays(work, clue) > 0L
            work[i] = UNKNOWN
            out[i] = when {
                canFill && !canEmpty -> FILLED
                canEmpty && !canFill -> EMPTY
                else -> UNKNOWN
            }
        }
        return true
    }

    /**
     * 统计一行在提示下的所有合法摆法数量（饱和到 Long.MAX_VALUE）。
     *
     * 定义 dp[i][j] = 前 i 个格子放好前 j 段、且第 i 个格子为空时的摆法数。
     * 为了处理“最后一段贴着行尾”的情况，末尾补一个虚拟空格。
     */
    private fun countWays(cells: IntArray, clue: List<Int>): Long {
        val n = cells.size
        val m = clue.size

        if (m == 0 || (m == 1 && clue[0] <= 0)) {
            return if (cells.none { it == FILLED }) 1L else 0L
        }

        val extSize = n + 1 // 末尾留一个虚拟空格
        val dp = Array(extSize + 1) { LongArray(m + 1) }
        dp[0][0] = 1L

        for (i in 1..extSize) {
            val cell = if (i <= n) cells[i - 1] else EMPTY
            if (cell == FILLED) continue // dp[i][*] 必须为 0
            for (j in 0..m) {
                var ways = dp[i - 1][j]
                if (j >= 1) {
                    val len = clue[j - 1]
                    val start = i - 1 - len
                    if (len > 0 && start >= 0) {
                        var canPlace = true
                        for (k in start until i - 1) {
                            if (k < n && cells[k] == EMPTY) {
                                canPlace = false
                                break
                            }
                        }
                        if (canPlace) {
                            ways = addSat(ways, dp[start][j - 1])
                        }
                    }
                }
                dp[i][j] = ways
            }
        }
        return dp[extSize][m]
    }

    private fun addSat(a: Long, b: Long): Long {
        val r = a + b
        return if (r < 0L) Long.MAX_VALUE else r
    }
}
