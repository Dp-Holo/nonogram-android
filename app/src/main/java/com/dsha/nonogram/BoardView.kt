package com.dsha.nonogram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 数织棋盘：自己绘制格子、提示数字，并处理交互。
 *
 * 操作：
 *  - 轻点：切换涂色 / 空白
 *  - 长按（不移动）：切换打叉（标记为确定不涂）
 *  - 按住拖动：从按下的格子开始连续涂色；如果按在已涂色格子上则连续擦除
 *  - 「标记」模式打开时，点按与拖动都是打叉
 *  - 一次拖动 = 一步撤销
 */
class BoardView(context: Context) : View(context) {

    var state: GameState? = null
        set(value) {
            field = value
            dirty = true
            pressedIndex = -1
            downIndex = -1
            stroking = false
            invalidate()
        }

    var markMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onChange: (() -> Unit)? = null

    /** 每涂/擦/打叉一格回调一次（index、新状态、同一笔的编号），用于记录操作 */
    var onCellAction: ((index: Int, value: Int, strokeId: Long) -> Unit)? = null

    private var dirty = true
    private var cellSize = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var clueUnitW = 0f
    private var clueUnitH = 0f
    private var clueCols = 1
    private var clueRows = 1

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tmp = RectF()

    private var strokeCounter = 0L
    private var currentStroke = 0L
    private var pressedIndex = -1
    private var downIndex = -1
    private var downAt = 0L
    private var stroking = false
    private var strokeDirty = false
    private var paintValue = GameState.FILLED

    private fun dp(v: Float): Float = v * density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dirty = true
    }

    private fun computeGeometry() {
        val s = state ?: return
        val n = s.n
        val p = s.puzzle
        clueCols = p.clueWidth
        clueRows = p.clueHeight

        val pad = dp(4f)
        val availW = width - pad * 2f
        val availH = height - pad * 2f
        if (availW <= 0f || availH <= 0f) return

        val ratio = 0.68f
        val byW = availW / (n + clueCols * ratio)
        val byH = availH / (n + clueRows * ratio)
        cellSize = min(byW, byH)
        clueUnitW = cellSize * ratio
        clueUnitH = cellSize * ratio

        val totalW = n * cellSize + clueCols * clueUnitW
        val totalH = n * cellSize + clueRows * clueUnitH
        gridLeft = pad + (availW - totalW) / 2f + clueCols * clueUnitW
        gridTop = pad + (availH - totalH) / 2f + clueRows * clueUnitH
        dirty = false
    }

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        if (dirty) computeGeometry()
        if (cellSize <= 0f) return

        val n = s.n
        val p = s.puzzle
        val left = gridLeft
        val top = gridTop
        val right = left + n * cellSize
        val bottom = top + n * cellSize

        fillPaint.color = COLOR_BOARD_BG
        tmp.set(left - dp(2f), top - dp(2f), right + dp(2f), bottom + dp(2f))
        val boardRadius = max(dp(6f), cellSize * 0.12f)
        canvas.drawRoundRect(tmp, boardRadius, boardRadius, fillPaint)

        val inset = max(dp(0.7f), cellSize * 0.05f)
        val radius = max(dp(1.5f), cellSize * 0.16f)
        for (r in 0 until n) {
            for (c in 0 until n) {
                val idx = r * n + c
                val x = left + c * cellSize
                val y = top + r * cellSize
                when (s.cells[idx]) {
                    GameState.EMPTY -> {
                        fillPaint.color = if (idx == pressedIndex) COLOR_CELL_PRESS else COLOR_CELL
                        tmp.set(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset)
                        canvas.drawRoundRect(tmp, radius, radius, fillPaint)
                    }

                    GameState.FILLED -> {
                        fillPaint.color = COLOR_FILLED
                        tmp.set(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset)
                        canvas.drawRoundRect(tmp, radius, radius, fillPaint)
                    }

                    else -> {
                        fillPaint.color = COLOR_CELL
                        tmp.set(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset)
                        canvas.drawRoundRect(tmp, radius, radius, fillPaint)
                        markPaint.color = COLOR_MARK
                        markPaint.strokeWidth = max(dp(1.2f), cellSize * 0.08f)
                        val p0 = cellSize * 0.30f
                        val p1 = cellSize * 0.70f
                        canvas.drawLine(x + p0, y + p0, x + p1, y + p1, markPaint)
                        canvas.drawLine(x + p1, y + p0, x + p0, y + p1, markPaint)
                    }
                }
            }
        }

        linePaint.style = Paint.Style.STROKE
        for (i in 0..n) {
            // 只有 5 的倍数尺寸（10/15/20）才画 5 格分隔线；8×8 这类不画中间的线
            val strong = i == 0 || i == n || (n > 5 && n % 5 == 0 && i % 5 == 0)
            linePaint.color = if (strong) COLOR_LINE_STRONG else COLOR_LINE
            linePaint.strokeWidth =
                if (strong) max(dp(1.2f), cellSize * 0.04f) else max(dp(0.6f), cellSize * 0.02f)
            canvas.drawLine(left + i * cellSize, top, left + i * cellSize, bottom, linePaint)
            canvas.drawLine(left, top + i * cellSize, right, top + i * cellSize, linePaint)
        }

        val rowRuns = Array(n) { r -> runsOf(IntArray(n) { c -> s.cells[r * n + c] }) }
        val colRuns = Array(n) { c -> runsOf(IntArray(n) { r -> s.cells[r * n + c] }) }
        val baseText = min(cellSize * 0.56f, clueUnitW * 0.78f)

        for (r in 0 until n) {
            val clue = p.rowClues[r]
            val cy = top + r * cellSize + cellSize / 2f
            for (k in clue.indices.reversed()) {
                val num = clue[k]
                cluePaint.textSize = if (num >= 10) baseText * 0.70f else baseText
                cluePaint.color = clueColor(rowRuns[r], clue, k)
                val cx = left - (clue.size - k) * clueUnitW + clueUnitW / 2f
                val baseline = cy - (cluePaint.ascent() + cluePaint.descent()) / 2f
                canvas.drawText(num.toString(), cx, baseline, cluePaint)
            }
        }

        for (c in 0 until n) {
            val clue = p.colClues[c]
            val cx = left + c * cellSize + cellSize / 2f
            for (k in clue.indices.reversed()) {
                val num = clue[k]
                cluePaint.textSize = if (num >= 10) baseText * 0.70f else baseText
                cluePaint.color = clueColor(colRuns[c], clue, k)
                val cy = top - (clue.size - k) * clueUnitH + clueUnitH / 2f
                val baseline = cy - (cluePaint.ascent() + cluePaint.descent()) / 2f
                canvas.drawText(num.toString(), cx, baseline, cluePaint)
            }
        }
    }

    private fun runsOf(values: IntArray): List<Int> {
        val out = ArrayList<Int>(4)
        var count = 0
        for (v in values) {
            if (v == GameState.FILLED) {
                count++
            } else if (count > 0) {
                out.add(count)
                count = 0
            }
        }
        if (count > 0) out.add(count)
        return out
    }

    private fun clueColor(actual: List<Int>, clue: List<Int>, index: Int): Int {
        if (clue[index] == 0) return COLOR_CLUE_DONE
        return if (actual.getOrNull(index) == clue[index]) COLOR_CLUE_DONE else COLOR_CLUE
    }

    private fun hitTest(x: Float, y: Float): Int {
        val s = state ?: return -1
        if (cellSize <= 0f) return -1
        val n = s.n
        if (x < gridLeft || y < gridTop) return -1
        val c = ((x - gridLeft) / cellSize).toInt()
        val r = ((y - gridTop) / cellSize).toInt()
        if (c < 0 || r < 0 || c >= n || r >= n) return -1
        return r * n + c
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = state ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = hitTest(event.x, event.y)
                if (idx < 0) return false
                strokeCounter++
                currentStroke = strokeCounter
                downIndex = idx
                pressedIndex = idx
                downAt = System.currentTimeMillis()
                stroking = false
                strokeDirty = false
                paintValue = when {
                    markMode ->
                        if (s.cells[idx] == GameState.MARKED) GameState.EMPTY
                        else GameState.MARKED

                    s.cells[idx] == GameState.FILLED -> GameState.EMPTY
                    else -> GameState.FILLED
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = hitTest(event.x, event.y)
                if (idx < 0) {
                    if (pressedIndex != -1) {
                        pressedIndex = -1
                        invalidate()
                    }
                    return true
                }
                if (!stroking && idx != downIndex) {
                    // 第一次移动到别的格子：开始一笔连续涂色
                    stroking = true
                    s.beginStroke()
                    if (s.set(downIndex, paintValue)) {
                        strokeDirty = true
                        onCellAction?.invoke(downIndex, paintValue, currentStroke)
                    }
                    if (s.set(idx, paintValue)) {
                        strokeDirty = true
                        onCellAction?.invoke(idx, paintValue, currentStroke)
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    invalidate()
                } else if (stroking) {
                    if (s.set(idx, paintValue)) {
                        strokeDirty = true
                        onCellAction?.invoke(idx, paintValue, currentStroke)
                        invalidate()
                    }
                }
                if (idx != pressedIndex) {
                    pressedIndex = idx
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (stroking) {
                    s.endStroke()
                    if (strokeDirty) onChange?.invoke()
                } else {
                    val elapsed = System.currentTimeMillis() - downAt
                    val longPress = elapsed >= LONG_PRESS_MS
                    val value = when {
                        longPress && !markMode ->
                            if (s.cells[downIndex] == GameState.MARKED) GameState.EMPTY
                            else GameState.MARKED

                        else -> paintValue
                    }
                    s.beginStroke()
                    val changed = s.set(downIndex, value)
                    s.endStroke()
                    if (changed) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCellAction?.invoke(downIndex, value, currentStroke)
                        invalidate()
                        onChange?.invoke()
                    }
                }
                pressedIndex = -1
                downIndex = -1
                stroking = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (stroking) {
                    s.endStroke()
                    if (strokeDirty) onChange?.invoke()
                }
                stroking = false
                pressedIndex = -1
                downIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val LONG_PRESS_MS = 340L

        private val COLOR_BOARD_BG = 0xFF171C28.toInt()
        private val COLOR_CELL = 0xFF1F2737.toInt()
        private val COLOR_CELL_PRESS = 0xFF2C3A5C.toInt()
        private val COLOR_FILLED = 0xFF7C9CFF.toInt()
        private val COLOR_MARK = 0xFF6B7690.toInt()
        private val COLOR_LINE = 0xFF12161F.toInt()
        private val COLOR_LINE_STRONG = 0xFF39445A.toInt()
        private val COLOR_CLUE = 0xFFD6DDEE.toInt()
        private val COLOR_CLUE_DONE = 0xFF4A5470.toInt()
    }
}
