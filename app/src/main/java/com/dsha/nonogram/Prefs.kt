package com.dsha.nonogram

import android.content.Context

/**
 * 用 SharedPreferences 保存每一关的进度、通关状态与最佳用时。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("nonogram_state", Context.MODE_PRIVATE)

    fun loadCells(index: Int): IntArray? {
        val raw = sp.getString("cells_$index", null) ?: return null
        if (raw.isEmpty()) return null
        val parts = raw.split(",")
        val out = IntArray(parts.size)
        for (i in parts.indices) {
            out[i] = parts[i].toIntOrNull() ?: 0
        }
        return out
    }

    fun saveCells(index: Int, cells: IntArray) {
        sp.edit().putString("cells_$index", cells.joinToString(",")).apply()
    }

    fun isSolved(index: Int): Boolean = sp.getBoolean("solved_$index", false)

    fun markSolved(index: Int, ms: Long) {
        sp.edit().putBoolean("solved_$index", true).apply()
        val best = bestTime(index)
        if (best <= 0L || ms < best) {
            sp.edit().putLong("best_$index", ms).apply()
        }
    }

    fun bestTime(index: Int): Long = sp.getLong("best_$index", 0L)

    fun lastLevel(): Int = sp.getInt("last_level", 0)

    fun setLastLevel(index: Int) {
        sp.edit().putInt("last_level", index).apply()
    }
}
