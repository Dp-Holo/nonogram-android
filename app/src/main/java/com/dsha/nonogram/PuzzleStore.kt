package com.dsha.nonogram

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「我的关卡」存储：随机生成的、图片导入的关卡都存在这里（SharedPreferences + JSON）。
 */
class PuzzleStore private constructor(context: Context) {

    private val sp = context.getSharedPreferences("nonogram_custom_puzzles", Context.MODE_PRIVATE)
    private val items = ArrayList<Puzzle>()
    private var loaded = false

    fun all(): List<Puzzle> {
        ensureLoaded()
        return items.toList()
    }

    fun find(key: String): Puzzle? {
        ensureLoaded()
        return items.firstOrNull { it.key == key }
    }

    fun add(puzzle: Puzzle): Boolean {
        ensureLoaded()
        if (items.any { it.key == puzzle.key }) return false
        items.add(0, puzzle)
        while (items.size > MAX_ITEMS) {
            items.removeAt(items.size - 1)
        }
        persist()
        return true
    }

    fun remove(key: String): Boolean {
        ensureLoaded()
        val removed = items.removeAll { it.key == key }
        if (removed) persist()
        return removed
    }

    fun count(): Int {
        ensureLoaded()
        return items.size
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        load()
    }

    private fun load() {
        val raw = sp.getString(KEY_LIST, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key", "")
                if (key.isEmpty()) continue
                val name = obj.optString("name", "关卡")
                val rowsText = obj.optString("rows", "")
                if (rowsText.isEmpty()) continue
                val puzzle = Puzzle.fromRows(key, name, rowsText.split("\n"))
                if (puzzle != null) items.add(puzzle)
            }
        } catch (e: Exception) {
            // 数据损坏时不阻塞启动
        }
    }

    private fun persist() {
        try {
            val array = JSONArray()
            for (puzzle in items) {
                val obj = JSONObject()
                obj.put("key", puzzle.key)
                obj.put("name", puzzle.name)
                obj.put("rows", puzzle.toRowsString())
                array.put(obj)
            }
            sp.edit().putString(KEY_LIST, array.toString()).apply()
        } catch (e: Exception) {
            // 忽略写入异常
        }
    }

    companion object {
        @Volatile
        private var shared: PuzzleStore? = null

        /** 全进程共用一个实例，避免两个 Activity 各自缓存导致看不到对方新增的关卡 */
        fun get(context: Context): PuzzleStore {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                val created = PuzzleStore(context.applicationContext)
                shared = created
                return created
            }
        }

        private const val KEY_LIST = "list"
        private const val MAX_ITEMS = 100
    }
}
