package com.dsha.nonogram

import java.util.Random

/**
 * 随机关卡生成器。
 *
 * 流程：随机涂色 -> 随机轴镜像 -> 元胞自动机平滑（去掉噪点、让图案成块）-> 去孤立点
 *      -> 质量过滤（连通块数量、行列不能全空）-> 行列约束传播求解
 * 传播能推满 = 逻辑唯一可解，玩家不用猜。
 *
 * 难度通过「形状参数」控制：
 *  - 简单：更密 + 平滑两次，图案成块、大块多，推起来快
 *  - 普通：中等密度 + 平滑一次
 *  - 困难：不平滑，碎块更多，需要更多轮推理
 */
object Generator {

    const val UNKNOWN = 0
    const val FILLED = 1
    const val EMPTY = 2

    const val EASY = 0
    const val NORMAL = 1
    const val HARD = 2

    private const val MAX_ATTEMPTS = 3000
    private const val TIME_BUDGET_MS = 5000L

    private class ShapeConfig(
        val density: Double,
        val smoothTimes: Int,
        val componentLimit: Int
    )

    private fun configFor(difficulty: Int): ShapeConfig = when (difficulty) {
        EASY -> ShapeConfig(density = 0.62, smoothTimes = 2, componentLimit = 2)
        HARD -> ShapeConfig(density = 0.52, smoothTimes = 0, componentLimit = 3)
        else -> ShapeConfig(density = 0.55, smoothTimes = 1, componentLimit = 2)
    }

    /** 生成一张 size×size 的答案位图；失败返回 null */
    fun generate(size: Int, difficulty: Int = NORMAL, random: Random = Random()): BooleanArray? {
        if (size < 3 || size > 30) return null
        val config = configFor(difficulty)
        val deadline = System.currentTimeMillis() + TIME_BUDGET_MS
        var attempts = 0

        while (attempts < MAX_ATTEMPTS) {
            attempts++
            if (attempts % 64 == 0 && System.currentTimeMillis() > deadline) break
            val solution = randomShape(size, random, config) ?: continue
            // 只用行列约束传播能推满全部格子 = 逻辑唯一解，玩家不用猜
            if (roundsOf(solution, size) > 0) return solution
        }
        return null
    }

    /** 难度档位名称，用于界面文案 */
    fun difficultyName(difficulty: Int): String = when (difficulty) {
        EASY -> "简单"
        HARD -> "困难"
        else -> "普通"
    }

    // ------------------------------------------------------------ 形状生成

    private fun randomShape(size: Int, random: Random, config: ShapeConfig): BooleanArray? {
        var grid = BooleanArray(size * size) { random.nextDouble() < config.density }

        // 随机镜像轴：竖 / 横 / 双轴，交给玩家一个「像画出来的」图案
        when (random.nextInt(3)) {
            0 -> mirrorVertical(grid, size)
            1 -> mirrorHorizontal(grid, size)
            else -> {
                mirrorVertical(grid, size)
                mirrorHorizontal(grid, size)
            }
        }

        repeat(config.smoothTimes) {
            grid = smooth(grid, size)
        }
        removeIsolated(grid, size)

        val filled = grid.count { it }
        val ratio = filled.toDouble() / (size * size)
        if (ratio < 0.22 || ratio > 0.85) return null
        if (filled == size * size) return null
        if (connectedComponents(grid, size) > config.componentLimit) return null
        if (hasEmptyLine(grid, size)) return null
        return grid
    }

    private fun mirrorVertical(grid: BooleanArray, size: Int) {
        for (r in 0 until size) {
            for (c in 0 until size / 2) {
                grid[r * size + (size - 1 - c)] = grid[r * size + c]
            }
        }
    }

    private fun mirrorHorizontal(grid: BooleanArray, size: Int) {
        for (r in 0 until size / 2) {
            for (c in 0 until size) {
                grid[(size - 1 - r) * size + c] = grid[r * size + c]
            }
        }
    }

    private fun smooth(grid: BooleanArray, size: Int): BooleanArray {
        val out = BooleanArray(grid.size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                out[r * size + c] = neighborCount(grid, size, r, c) >= 5
            }
        }
        return out
    }

    private fun removeIsolated(grid: BooleanArray, size: Int) {
        for (i in grid.indices) {
            if (!grid[i]) continue
            val r = i / size
            val c = i % size
            if (neighborCount(grid, size, r, c) == 0) grid[i] = false
        }
    }

    private fun neighborCount(grid: BooleanArray, size: Int, r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until size && nc in 0 until size && grid[nr * size + nc]) count++
            }
        }
        return count
    }

    private fun hasEmptyLine(grid: BooleanArray, size: Int): Boolean {
        for (r in 0 until size) {
            var any = false
            for (c in 0 until size) if (grid[r * size + c]) { any = true; break }
            if (!any) return true
        }
        for (c in 0 until size) {
            var any = false
            for (r in 0 until size) if (grid[r * size + c]) { any = true; break }
            if (!any) return true
        }
        return false
    }

    /** 涂色格子的连通块数量（4 邻接） */
    fun connectedComponents(grid: BooleanArray, size: Int): Int {
        val seen = BooleanArray(grid.size)
        val stack = IntArray(grid.size)
        var count = 0
        for (start in grid.indices) {
            if (!grid[start] || seen[start]) continue
            count++
            var top = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val index = stack[--top]
                val r = index / size
                val c = index % size
                if (r > 0) {
                    val q = index - size
                    if (grid[q] && !seen[q]) { seen[q] = true; stack[top++] = q }
                }
                if (r < size - 1) {
                    val q = index + size
                    if (grid[q] && !seen[q]) { seen[q] = true; stack[top++] = q }
                }
                if (c > 0) {
                    val q = index - 1
                    if (grid[q] && !seen[q]) { seen[q] = true; stack[top++] = q }
                }
                if (c < size - 1) {
                    val q = index + 1
                    if (grid[q] && !seen[q]) { seen[q] = true; stack[top++] = q }
                }
            }
        }
        return count
    }

    // ------------------------------------------------------------ 求解

    /** 由答案算行列提示 */
    fun cluesOf(solution: BooleanArray, size: Int): Pair<List<List<Int>>, List<List<Int>>> {
        val rowClues = ArrayList<List<Int>>(size)
        val colClues = ArrayList<List<Int>>(size)
        for (i in 0 until size) {
            rowClues.add(clueRuns(IntArray(size) { c -> if (solution[i * size + c]) 1 else 0 }))
            colClues.add(clueRuns(IntArray(size) { r -> if (solution[r * size + i]) 1 else 0 }))
        }
        return Pair(rowClues, colClues)
    }

    /** 用逻辑求解一道题需要的「轮数」；<=0 表示推不满（不是逻辑唯一解） */
    fun roundsOf(solution: BooleanArray, size: Int): Int {
        val (rowClues, colClues) = cluesOf(solution, size)
        return solveRounds(size, rowClues, colClues)
    }

    fun solveRounds(size: Int, rowClues: List<List<Int>>, colClues: List<List<Int>>): Int {
        val state = IntArray(size * size)
        val rounds = propagateInternal(size, rowClues, colClues, state)
        if (rounds < 0) return -1
        for (v in state) if (v == UNKNOWN) return -1
        return rounds
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
        val rounds = propagateInternal(size, rowClues, colClues, state)
        if (rounds < 0) return false
        for (v in state) if (v == UNKNOWN) return false
        return true
    }

    /** @return 用了多少轮；-1 表示与提示矛盾 */
    private fun propagateInternal(
        size: Int,
        rowClues: List<List<Int>>,
        colClues: List<List<Int>>,
        state: IntArray
    ): Int {
        val line = IntArray(size)
        val out = IntArray(size)
        var rounds = 0
        val maxRounds = 8 * size + 8
        while (rounds < maxRounds) {
            rounds++
            var changed = false

            for (r in 0 until size) {
                for (c in 0 until size) line[c] = state[r * size + c]
                if (!analyzeLine(line, rowClues[r], out)) return -1
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
                if (!analyzeLine(line, colClues[c], out)) return -1
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
        return rounds
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
     * dp[i][j]：前 i 个格子放好前 j 段、且第 i 个格子为空的摆法数；末尾补一个虚拟空格。
     */
    private fun countWays(cells: IntArray, clue: List<Int>): Long {
        val n = cells.size
        val m = clue.size

        if (m == 0 || (m == 1 && clue[0] <= 0)) {
            return if (cells.none { it == FILLED }) 1L else 0L
        }

        val extSize = n + 1
        val dp = Array(extSize + 1) { LongArray(m + 1) }
        dp[0][0] = 1L

        for (i in 1..extSize) {
            val cell = if (i <= n) cells[i - 1] else EMPTY
            if (cell == FILLED) continue
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
