package com.dsha.nonogram

import android.content.Context

/**
 * 完成记录：每通关一局就记一条「尺寸 + 用时」，用于统计各尺寸的平均/中位完成时间。
 * 存在 SharedPreferences 里，最多保留最近 800 条。
 */
class StatsStore private constructor(context: Context) {

    private val sp = context.getSharedPreferences("nonogram_stats", Context.MODE_PRIVATE)

    fun record(size: Int, ms: Long, difficulty: Int = -1) {
        if (size <= 0 || ms <= 0L) return
        val raw = sp.getString(KEY, "") ?: ""
        val line = "$size,$ms,$difficulty"
        val lines = if (raw.isEmpty()) arrayListOf(line) else {
            val list = ArrayList(raw.split(";"))
            list.add(line)
            list
        }
        val trimmed = if (lines.size > MAX_ENTRIES) lines.subList(lines.size - MAX_ENTRIES, lines.size) else lines
        sp.edit().putString(KEY, trimmed.joinToString(";")).apply()
    }

    /** 所有完成记录（尺寸 + 用时），按时间先后排列；旧数据只有两个字段 */
    fun entries(): List<Pair<Int, Long>> = records().map { it.first to it.second }

    /** 尺寸 + 用时 + 难度，难度 -1 表示未知 */
    fun records(): List<Triple<Int, Long, Int>> {
        val raw = sp.getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        val result = ArrayList<Triple<Int, Long, Int>>()
        for (line in raw.split(";")) {
            if (line.isEmpty()) continue
            val parts = line.split(",")
            if (parts.size < 2) continue
            val size = parts[0].toIntOrNull() ?: continue
            val ms = parts[1].toLongOrNull() ?: continue
            val difficulty = if (parts.size >= 3) parts[2].toIntOrNull() ?: -1 else -1
            if (size > 0 && ms > 0L) result.add(Triple(size, ms, difficulty))
        }
        return result
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 800

        @Volatile
        private var shared: StatsStore? = null

        fun get(context: Context): StatsStore {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                val created = StatsStore(context.applicationContext)
                shared = created
                return created
            }
        }
    }
}
