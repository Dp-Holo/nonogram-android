package com.dsha.nonogram

/**
 * 关卡的静态定义。
 * 每一关用一组字符串描述答案：# 表示要涂的格子，. 表示空白。
 */
/** 把一行（#/.) 切成连续涂色块的长度列表，空行返回 [0]。 */
private fun clueRuns(line: String): List<Int> {
    val out = ArrayList<Int>(4)
    var count = 0
    for (ch in line) {
        if (ch == '#') {
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

object Puzzles {

    data class Puzzle(val name: String, val rows: List<String>) {

        val size: Int = rows.size

        /** 一维展开的答案，true = 需要涂色 */
        val solution: BooleanArray by lazy {
            val n = rows.size
            val a = BooleanArray(n * n)
            for (r in 0 until n) {
                val line = rows[r]
                for (c in 0 until n) {
                    if (line[c] == '#') a[r * n + c] = true
                }
            }
            a
        }

        val rowClues: List<List<Int>> by lazy { rows.map { clueRuns(it) } }

        val colClues: List<List<Int>> by lazy {
            val n = rows.size
            (0 until n).map { c ->
                val sb = StringBuilder(n)
                for (r in 0 until n) sb.append(rows[r][c])
                clueRuns(sb.toString())
            }
        }

        /** 行提示最多几个数字 */
        val clueWidth: Int get() = rowClues.maxOfOrNull { it.size } ?: 1

        /** 列提示最多几个数字 */
        val clueHeight: Int get() = colClues.maxOfOrNull { it.size } ?: 1
    }

    val all: List<Puzzle> = listOf(
        Puzzle(
            "爱心", listOf(
                ".#.#.",
                "#####",
                "#####",
                ".###.",
                "..#..",
            )
        ),
        Puzzle(
            "小树", listOf(
                "..#..",
                ".###.",
                "#####",
                "..#..",
                "..#..",
            )
        ),
        Puzzle(
            "十字", listOf(
                "..#..",
                "..#..",
                "#####",
                "..#..",
                "..#..",
            )
        ),
        Puzzle(
            "沙漏", listOf(
                "#####",
                ".###.",
                "..#..",
                ".###.",
                "#####",
            )
        ),
        Puzzle(
            "心形", listOf(
                "..##..##..",
                ".####.####",
                "##########",
                "##########",
                "##########",
                "##########",
                ".########.",
                "..######..",
                "...####...",
                "....##....",
            )
        ),
        Puzzle(
            "星星", listOf(
                "....##....",
                "....##....",
                "...####...",
                "##########",
                ".########.",
                "..######..",
                "..######..",
                ".##....##.",
                "###....###",
                "##......##",
            )
        ),
        Puzzle(
            "火箭", listOf(
                "....##....",
                "...####...",
                "...####...",
                "..######..",
                "..######..",
                ".########.",
                ".########.",
                "..######..",
                "..##..##..",
                ".##....##.",
            )
        ),
        Puzzle(
            "猫咪", listOf(
                "#........#",
                "##......##",
                "#.#....#.#",
                "#..####..#",
                "#.#.##.#.#",
                "#..####..#",
                "##..##..##",
                ".########.",
                "..#....#..",
                "..######..",
            )
        ),
        Puzzle(
            "蘑菇", listOf(
                "...####...",
                "..######..",
                ".########.",
                "##########",
                "##.####.##",
                "...####...",
                "...####...",
                "...####...",
                "..######..",
                "##########",
            )
        ),
        Puzzle(
            "大爱心", listOf(
                "...##.....##...",
                "..####...####..",
                ".######.######.",
                "###############",
                "###############",
                "###############",
                "###############",
                "###############",
                ".#############.",
                "..###########..",
                "...#########...",
                "....#######....",
                ".....#####.....",
                "......###......",
                ".......#.......",
            )
        ),
    )
}
