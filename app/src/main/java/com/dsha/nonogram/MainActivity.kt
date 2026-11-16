package com.dsha.nonogram

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var board: BoardView
    private lateinit var metaView: TextView
    private lateinit var undoButton: TextView
    private lateinit var markButton: TextView

    private lateinit var state: GameState
    private var levelIndex = 0
    private var solved = false
    private var markMode = false
    private var startAt = 0L
    private var elapsedMs = 0L
    private var timerRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private var levelDialog: AlertDialog? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            elapsedMs = System.currentTimeMillis() - startAt
            updateMeta()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        loadLevel(prefs.lastLevel())
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }

        // 顶部标题栏
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "数织"
            setTextColor(TEXT)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "NONOGRAM · 用数字画出图案"
            setTextColor(TEXT_DIM)
            textSize = 11f
        }
        titles.addView(title)
        titles.addView(subtitle)

        metaView = TextView(this).apply {
            setTextColor(TEXT_DIM)
            textSize = 13f
            gravity = Gravity.END
            includeFontPadding = false
        }
        header.addView(
            titles,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(metaView)
        column.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 棋盘
        board = BoardView(this)
        board.onChange = { onBoardChanged() }
        column.addView(
            board,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(12f) }
        )

        // 底部操作栏
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        undoButton = makeButton("撤销") { onUndo() }
        markButton = makeButton("标记") { toggleMark() }
        val resetButton = makeButton("重来") { onReset() }
        val levelButton = makeButton("关卡") { showLevels() }
        val buttons = listOf(undoButton, markButton, resetButton, levelButton)
        buttons.forEachIndexed { index, button ->
            footer.addView(
                button,
                LinearLayout.LayoutParams(0, dp(46f), 1f).apply {
                    if (index > 0) marginStart = dp(8f)
                }
            )
        }
        column.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
        )

        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
    }

    private fun makeButton(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(TEXT_DIM)
        tv.textSize = 13.5f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.background = rounded(BTN_BG, BTN_STROKE, 12f)
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener { onClick() }
        tv.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.alpha = 0.55f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha =
                    if (v.isEnabled) 1f else 0.4f
            }
            false
        }
        return tv
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(maxOf(1, dp(1f)), stroke)
        }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()

    // ------------------------------------------------------------- 关卡

    private fun loadLevel(index: Int) {
        handler.removeCallbacks(ticker)
        timerRunning = false

        levelIndex = index.coerceIn(0, Puzzles.all.lastIndex)
        val puzzle = Puzzles.all[levelIndex]
        state = GameState(puzzle)

        val saved = prefs.loadCells(levelIndex)
        if (saved != null && saved.size == state.cells.size) {
            System.arraycopy(saved, 0, state.cells, 0, saved.size)
        }

        board.state = state
        board.markMode = markMode

        solved = state.isSolved()
        elapsedMs = 0L
        if (solved) {
            elapsedMs = prefs.bestTime(levelIndex)
        } else {
            startTimer()
        }

        prefs.setLastLevel(levelIndex)
        updateMeta()
        updateButtons()
        board.invalidate()
    }

    private fun startTimer() {
        elapsedMs = 0L
        startAt = System.currentTimeMillis()
        timerRunning = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun updateMeta() {
        val puzzle = Puzzles.all[levelIndex]
        val progress = "第 ${levelIndex + 1}/${Puzzles.all.size} 关 · ${puzzle.size}×${puzzle.size}"
        val time = if (solved) {
            if (elapsedMs > 0L) "✓ ${formatTime(elapsedMs)}" else "✓ 已通关"
        } else {
            formatTime(elapsedMs)
        }
        metaView.text = "$progress\n$time"
    }

    private fun updateButtons() {
        undoButton.isEnabled = state.undoAvailable()
        undoButton.alpha = if (undoButton.isEnabled) 1f else 0.4f

        markButton.text = if (markMode) "标记·开" else "标记"
        markButton.setTextColor(if (markMode) ACCENT else TEXT_DIM)
        markButton.background = rounded(
            if (markMode) BTN_ACTIVE else BTN_BG,
            if (markMode) ACCENT else BTN_STROKE,
            12f
        )
    }

    private fun onUndo() {
        if (!state.undo()) return
        prefs.saveCells(levelIndex, state.cells)
        updateButtons()
        board.invalidate()
    }

    private fun toggleMark() {
        markMode = !markMode
        board.markMode = markMode
        updateButtons()
    }

    private fun onReset() {
        state.reset()
        prefs.saveCells(levelIndex, state.cells)
        if (solved) {
            solved = false
            startTimer()
        }
        updateMeta()
        updateButtons()
        board.invalidate()
    }

    private fun onBoardChanged() {
        prefs.saveCells(levelIndex, state.cells)
        updateButtons()
        if (!solved && state.isSolved()) {
            solved = true
            timerRunning = false
            handler.removeCallbacks(ticker)
            elapsedMs = System.currentTimeMillis() - startAt
            prefs.markSolved(levelIndex, elapsedMs)
            updateMeta()
            showWin()
        }
    }

    // ------------------------------------------------------------- 弹窗

    private fun showWin() {
        val index = levelIndex
        val puzzle = Puzzles.all[index]
        val message = TextView(this).apply {
            text = "${puzzle.name} · ${puzzle.size}×${puzzle.size}\n用时 ${formatTime(elapsedMs)}"
            setTextColor(TEXT)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("🎉 过关！")
            .setView(message)
            .setNegativeButton("关闭", null)
        if (index + 1 < Puzzles.all.size) {
            builder.setPositiveButton("下一关") { _, _ -> loadLevel(index + 1) }
        } else {
            builder.setPositiveButton("选关") { _, _ -> showLevels() }
        }
        builder.show()
    }

    private fun showLevels() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        Puzzles.all.forEachIndexed { index, puzzle ->
            val done = prefs.isSolved(index)
            val row = TextView(this).apply {
                text = "${index + 1}. ${puzzle.name}    ${puzzle.size}×${puzzle.size}" +
                        if (done) "   ✓" else ""
                setTextColor(if (index == levelIndex) ACCENT else if (done) TEXT_DIM else TEXT)
                textSize = 16f
                setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
                isClickable = true
            }
            row.setOnClickListener {
                levelDialog?.dismiss()
                loadLevel(index)
            }
            container.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择关卡")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .create()
        levelDialog = dialog
        dialog.show()
    }

    // ------------------------------------------------------------- 生命周期

    override fun onResume() {
        super.onResume()
        if (!solved && this::state.isInitialized) {
            startAt = System.currentTimeMillis() - elapsedMs
            timerRunning = true
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
    }

    override fun onPause() {
        super.onPause()
        if (timerRunning) {
            elapsedMs = System.currentTimeMillis() - startAt
            timerRunning = false
        }
        handler.removeCallbacks(ticker)
        if (this::state.isInitialized) {
            prefs.saveCells(levelIndex, state.cells)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------- 工具

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    companion object {
        private val BG = 0xFF12141A.toInt()
        private val TEXT = 0xFFE7ECF7.toInt()
        private val TEXT_DIM = 0xFF8A93A8.toInt()
        private val ACCENT = 0xFF7C9CFF.toInt()
        private val BTN_BG = 0xFF1B2130.toInt()
        private val BTN_ACTIVE = 0xFF24305A.toInt()
        private val BTN_STROKE = 0xFF2B3242.toInt()
    }
}
