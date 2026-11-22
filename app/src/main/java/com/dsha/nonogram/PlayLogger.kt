package com.dsha.nonogram

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 对局记录器：把每一局的操作逐条写成 JSONL（每行一个 JSON 事件），
 * 用于分析解题过程 / 校准难度。
 *
 * 记录的事件：
 *  - session_start：关卡信息（名称、尺寸、难度、行列提示）
 *  - cell：格子变化（r、c、新状态 1=涂 2=叉 0=空、stroke=同一笔的编号）
 *  - hint / undo / reset / pause / resume / solve / session_end
 */
class PlayLogger private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val io = Executors.newSingleThreadExecutor()

    private var active = false
    private var sid = 0L
    private var startedAt = 0L
    private var moveCount = 0
    private var hintCount = 0
    private var undoCount = 0
    private var pausedMs = 0L
    private var pauseStartedAt = 0L

    val moves: Int get() = moveCount
    val hints: Int get() = hintCount
    val undos: Int get() = undoCount

    fun beginSession(puzzle: Puzzle, difficulty: Int) {
        endSession(solved = false)
        active = true
        sid = System.currentTimeMillis()
        startedAt = System.currentTimeMillis()
        moveCount = 0
        hintCount = 0
        undoCount = 0
        pausedMs = 0L
        pauseStartedAt = 0L

        val json = JSONObject()
            .put("ev", "session_start")
            .put("sid", sid)
            .put("key", puzzle.key)
            .put("name", puzzle.name)
            .put("size", puzzle.size)
            .put("difficulty", difficulty)
            .put("filled", puzzle.filledCount)
            .put("startedAt", startedAt)
            .put("time", isoTime(startedAt))
            .put("rows", cluesJson(puzzle.rowClues))
            .put("cols", cluesJson(puzzle.colClues))
        append(json.toString())
    }

    fun cellChanged(index: Int, value: Int, strokeId: Long, size: Int) {
        if (!active) return
        moveCount++
        val json = JSONObject()
            .put("ev", "cell")
            .put("sid", sid)
            .put("t", elapsed())
            .put("r", index / size)
            .put("c", index % size)
            .put("v", value)
            .put("stroke", strokeId)
            .put("moves", moveCount)
        append(json.toString())
    }

    fun hint(index: Int, size: Int) {
        if (!active) return
        hintCount++
        val json = JSONObject()
            .put("ev", "hint")
            .put("sid", sid)
            .put("t", elapsed())
            .put("r", index / size)
            .put("c", index % size)
            .put("moves", moveCount)
        append(json.toString())
    }

    fun undo() {
        if (!active) return
        undoCount++
        append(event("undo").put("undos", undoCount).toString())
    }

    fun reset() {
        if (!active) return
        append(event("reset").put("moves", moveCount).toString())
    }

    fun pause() {
        if (!active) return
        pauseStartedAt = System.currentTimeMillis()
        append(event("pause").toString())
    }

    fun resume() {
        if (!active) return
        if (pauseStartedAt > 0L) {
            pausedMs += System.currentTimeMillis() - pauseStartedAt
            pauseStartedAt = 0L
        }
        append(event("resume").put("pausedMs", pausedMs).toString())
    }

    fun solve(elapsedMs: Long) {
        if (!active) return
        append(
            event("solve")
                .put("elapsed", elapsedMs)
                .put("moves", moveCount)
                .put("hints", hintCount)
                .put("undos", undoCount)
                .put("pausedMs", pausedMs)
                .toString()
        )
    }

    fun endSession(solved: Boolean) {
        if (!active) return
        append(
            event("session_end")
                .put("solved", solved)
                .put("elapsed", elapsed())
                .put("moves", moveCount)
                .put("hints", hintCount)
                .put("undos", undoCount)
                .put("pausedMs", pausedMs)
                .toString()
        )
        active = false
    }

    /** 把记录导出到手机 Download/DSHA/ 下，返回给用户看的位置；失败返回 null */
    fun export(context: Context): String? {
        val data = try {
            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) {
            return null
        }
        if (data.isEmpty()) return null

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "play-records-$stamp.jsonl"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/DSHA"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/DSHA/$name"
            } else {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "records")
                dir.mkdirs()
                val out = File(dir, name)
                out.writeText(data)
                out.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 记录文件里有多少行（大致反映记录量） */
    fun lineCount(): Int = try {
        if (!file.exists()) 0 else file.readLines().size
    } catch (e: Exception) {
        0
    }

    // ---------------------------------------------------------- 内部

    private fun event(name: String): JSONObject = JSONObject()
        .put("ev", name)
        .put("sid", sid)
        .put("t", elapsed())

    /** 相对本局开始的毫秒数（不含暂停时长更便于分析；这里给的是毛时间） */
    private fun elapsed(): Long = System.currentTimeMillis() - startedAt

    private fun append(line: String) {
        io.execute {
            try {
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    val old = File(file.parentFile, FILE_NAME + ".old")
                    if (old.exists()) old.delete()
                    file.renameTo(old)
                }
                file.appendText(line + "\n")
            } catch (e: Exception) {
                // 记录失败不影响游戏
            }
        }
    }

    private fun cluesJson(clues: List<List<Int>>): JSONArray {
        val array = JSONArray()
        for (line in clues) {
            val inner = JSONArray()
            for (value in line) inner.put(value)
            array.put(inner)
        }
        return array
    }

    private fun isoTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(ms))

    companion object {
        private const val FILE_NAME = "play-records.jsonl"
        private const val MAX_FILE_BYTES = 3L * 1024 * 1024

        @Volatile
        private var shared: PlayLogger? = null

        fun get(context: Context): PlayLogger {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                val created = PlayLogger(context.applicationContext)
                shared = created
                return created
            }
        }
    }
}
