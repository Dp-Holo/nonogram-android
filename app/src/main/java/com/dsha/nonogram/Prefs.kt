package com.dsha.nonogram

import android.content.Context

/**
 * 用 SharedPreferences 保存每一关的进度、通关状态与最佳用时。
 * 关卡用字符串 key 标识（内置关 builtin_0…，自建关 gen_/img_ 前缀）。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("nonogram_state", Context.MODE_PRIVATE)

    init {
        migrateLegacyKeys()
    }

    /** 早期版本用数字下标做 key，这里迁移到新 key，避免丢掉已有进度 */
    private fun migrateLegacyKeys() {
        if (sp.getBoolean(KEY_MIGRATED, false)) return
        val editor = sp.edit()
        for (i in 0 until 10) {
            sp.getString("cells_$i", null)?.let { editor.putString("cells_builtin_$i", it) }
            if (sp.getBoolean("solved_$i", false)) editor.putBoolean("solved_builtin_$i", true)
            val best = sp.getLong("best_$i", 0L)
            if (best > 0L) editor.putLong("best_builtin_$i", best)
        }
        val last = sp.getInt("last_level", 0)
        editor.putString("last_puzzle_key", "builtin_$last")
        editor.putBoolean(KEY_MIGRATED, true)
        editor.apply()
    }

    fun loadCells(key: String): IntArray? {
        val raw = sp.getString("cells_$key", null) ?: return null
        if (raw.isEmpty()) return null
        val parts = raw.split(",")
        val out = IntArray(parts.size)
        for (i in parts.indices) {
            out[i] = parts[i].toIntOrNull() ?: 0
        }
        return out
    }

    fun saveCells(key: String, cells: IntArray) {
        sp.edit().putString("cells_$key", cells.joinToString(",")).apply()
    }

    fun isSolved(key: String): Boolean = sp.getBoolean("solved_$key", false)

    fun markSolved(key: String, ms: Long) {
        sp.edit().putBoolean("solved_$key", true).apply()
        val best = bestTime(key)
        if (best <= 0L || ms < best) {
            sp.edit().putLong("best_$key", ms).apply()
        }
    }

    fun bestTime(key: String): Long = sp.getLong("best_$key", 0L)

    fun lastPuzzleKey(): String = sp.getString("last_puzzle_key", "builtin_0") ?: "builtin_0"

    fun setLastPuzzle(key: String) {
        sp.edit().putString("last_puzzle_key", key).apply()
    }

    /** 删除关卡时清掉它的存档 */
    fun forget(key: String) {
        sp.edit()
            .remove("cells_$key")
            .remove("solved_$key")
            .remove("best_$key")
            .apply()
    }

    companion object {
        private const val KEY_MIGRATED = "migrated_keys_v2"
    }
}
