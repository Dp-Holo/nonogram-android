package com.dsha.nonogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 用 JVM 单元测试保证「自动生成的题目一定逻辑可解、且提示与答案一致」。
 * 这些测试跑的是和 App 完全相同的代码（Puzzle / Generator）。
 */
class GeneratorTest {

    /** 独立实现一遍提示计算，避免和被测代码共用同一份逻辑 */
    private fun expectedClues(values: BooleanArray): List<Int> {
        val out = ArrayList<Int>()
        var run = 0
        for (v in values) {
            if (v) {
                run++
            } else if (run > 0) {
                out.add(run)
                run = 0
            }
        }
        if (run > 0) out.add(run)
        return if (out.isEmpty()) listOf(0) else out
    }

    @Test
    fun generatedPuzzlesAreLogicallySolvable() {
        val random = Random(20261116L)
        for (size in intArrayOf(5, 8, 10, 15, 20)) {
            repeat(5) { round ->
                val solution = Generator.generate(size, Generator.NORMAL, random)
                assertNotNull("size=$size 第 $round 次生成失败", solution)
                val puzzle = Puzzle.fromSolution("t", "t", size, solution!!)

                val state = IntArray(size * size)
                val solved = Generator.propagate(size, puzzle.rowClues, puzzle.colClues, state)
                assertTrue("size=$size 第 $round 题无法用逻辑唯一推出", solved)

                // 推断出来的结果必须和答案完全一致
                for (i in solution.indices) {
                    val expected = if (solution[i]) Generator.FILLED else Generator.EMPTY
                    assertEquals("size=$size 第 $round 题第 $i 格推断错误", expected, state[i])
                }
            }
        }
    }

    @Test
    fun cluesMatchSolution() {
        val random = Random(7L)
        for (size in intArrayOf(5, 8, 10, 15)) {
            val solution = Generator.generate(size, Generator.NORMAL, random)!!
            val puzzle = Puzzle.fromSolution("t", "t", size, solution)

            for (r in 0 until size) {
                val row = BooleanArray(size) { c -> solution[r * size + c] }
                assertEquals("size=$size 第 $r 行提示不一致", expectedClues(row), puzzle.rowClues[r])
            }
            for (c in 0 until size) {
                val col = BooleanArray(size) { r -> solution[r * size + c] }
                assertEquals("size=$size 第 $c 列提示不一致", expectedClues(col), puzzle.colClues[c])
            }
        }
    }

    @Test
    fun builtinPuzzlesAreSolvable() {
        for (puzzle in Builtins.puzzles) {
            val state = IntArray(puzzle.size * puzzle.size)
            assertTrue(
                "内置关「${puzzle.name}」无法用逻辑唯一推出",
                Generator.propagate(puzzle.size, puzzle.rowClues, puzzle.colClues, state)
            )
        }
    }

    @Test
    fun generatedPatternsDoNotRepeat() {
        val random = Random(42L)
        val seen = HashSet<String>()
        repeat(20) {
            val solution = Generator.generate(8, Generator.NORMAL, random, seen)
            assertNotNull("第 ${'$'}it 次生成失败", solution)
            val key = Puzzle.rowsOf(solution!!, 8)
            assertTrue("生成了重复的 8×8 图案", seen.add(key))
        }
    }

    @Test
    fun rowsRoundTrip() {
        val random = Random(3L)
        val solution = Generator.generate(10, Generator.NORMAL, random)!!
        val puzzle = Puzzle.fromSolution("x", "x", 10, solution)
        val restored = Puzzle.fromRows("x", "x", puzzle.toRowsString().split("\n"))
        assertNotNull(restored)
        assertEquals(puzzle.size, restored!!.size)
        for (i in solution.indices) {
            assertEquals(solution[i], restored.solution[i])
        }
    }

    @Test
    fun generatedPuzzlesHaveGoodShape() {
        val random = Random(1234L)
        for (size in intArrayOf(5, 8, 10, 15, 20)) {
            val solution = Generator.generate(size, Generator.NORMAL, random)
            assertNotNull("size=$size 生成失败", solution)
            val s = solution!!

            val components = Generator.connectedComponents(s, size)
            assertTrue("size=$size 涂色块太散（$components 块）", components in 1..3)

            val puzzle = Puzzle.fromSolution("t", "t", size, s)
            assertTrue("size=$size 出现整行为空", puzzle.rowClues.none { it == listOf(0) })
            assertTrue("size=$size 出现整列为空", puzzle.colClues.none { it == listOf(0) })

            val state = IntArray(size * size)
            assertTrue("size=$size 无法逻辑唯一推出", Generator.propagate(size, puzzle.rowClues, puzzle.colClues, state))
        }
    }

    @Test
    fun difficultyChangesClueDensity() {
        val random = Random(77L)

        fun clueTotal(solution: BooleanArray, size: Int): Int {
            val (rows, cols) = Generator.cluesOf(solution, size)
            return rows.sumOf { it.size } + cols.sumOf { it.size }
        }

        val easy = (0 until 3).map {
            val s = Generator.generate(10, Generator.EASY, random)
            assertNotNull("简单难度生成失败", s)
            clueTotal(s!!, 10)
        }.average()
        val hard = (0 until 3).map {
            val s = Generator.generate(10, Generator.HARD, random)
            assertNotNull("困难难度生成失败", s)
            clueTotal(s!!, 10)
        }.average()

        assertTrue("困难题的提示数应明显多于简单题: easy=$easy hard=$hard", hard > easy + 6)
    }

    @Test
    fun generatedPuzzlesAreAlwaysUniqueSolvable() {
        val random = Random(2026L)
        for (difficulty in intArrayOf(Generator.EASY, Generator.NORMAL, Generator.HARD)) {
            for (size in intArrayOf(5, 10, 15)) {
                repeat(2) {
                    val s = Generator.generate(size, difficulty, random)
                    assertNotNull("difficulty=$difficulty size=$size 生成失败", s)
                    assertTrue(
                        "difficulty=$difficulty size=$size 不是逻辑唯一解",
                        Generator.roundsOf(s!!, size) > 0
                    )
                }
            }
        }
    }

    private fun isMirrorSymmetric(grid: BooleanArray, size: Int): Boolean {
        var vertical = true
        for (r in 0 until size) {
            for (c in 0 until size / 2) {
                if (grid[r * size + c] != grid[r * size + size - 1 - c]) vertical = false
            }
        }
        if (vertical) return true
        var horizontal = true
        for (r in 0 until size / 2) {
            for (c in 0 until size) {
                if (grid[r * size + c] != grid[(size - 1 - r) * size + c]) horizontal = false
            }
        }
        return horizontal
    }

    @Test
    fun generatedPuzzlesAreNotAllSymmetric() {
        val random = Random(99L)
        var asymmetric = 0
        repeat(10) {
            val solution = Generator.generate(8, Generator.NORMAL, random)
            assertNotNull("第 ${'$'}it 次生成失败", solution)
            if (!isMirrorSymmetric(solution!!, 8)) asymmetric++
        }
        assertTrue("10 道题里应有多道不是对称图案（实际非对称 ${'$'}asymmetric 道）", asymmetric >= 2)
    }
}
