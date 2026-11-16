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
                val solution = Generator.generate(size, random)
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
            val solution = Generator.generate(size, random)!!
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
            val solution = Generator.generate(8, random)!!
            assertTrue("生成了重复的 8×8 图案", seen.add(Puzzle.rowsOf(solution, 8)))
        }
    }

    @Test
    fun rowsRoundTrip() {
        val random = Random(3L)
        val solution = Generator.generate(10, random)!!
        val puzzle = Puzzle.fromSolution("x", "x", 10, solution)
        val restored = Puzzle.fromRows("x", "x", puzzle.toRowsString().split("\n"))
        assertNotNull(restored)
        assertEquals(puzzle.size, restored!!.size)
        for (i in solution.indices) {
            assertEquals(solution[i], restored.solution[i])
        }
    }
}
