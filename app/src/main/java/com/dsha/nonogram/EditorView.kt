package com.dsha.nonogram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 自编关卡画板：N×N 的格子，点按/拖动涂色，支持橡皮、左右镜像、撤销。
 * 不显示提示数字（提示数字由 EditorActivity 在完成时自动生成）。
 */
class EditorView(context: Context) : View(context) {

    var size = 10
        private set

    var cells = BooleanArray(0)
        private set

    /** 左右镜像辅助：涂一格同时涂对称位置 */
    var mirror = false

    /** 橡皮模式：点按/拖动都是擦除 */
    var eraseMode = false

    var onChange: (() -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    private var cellSize = 0f
    private var gridLeft = 0f
    private var gridTop = 0f

    private var downIndex = -1
    private var pressedIndex = -1
    private var paintValue = true
    private var stroking = false
    private var strokeDirty = false

    private val undoStack = ArrayList<BooleanArray>(32)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun load(size: Int, grid: BooleanArray?) {
        this.size = size.coerceIn(5, 20)
        cells = BooleanArray(this.size * this.size)
        if (grid != null && grid.size == cells.size) {
            System.arraycopy(grid, 0, cells, 0, cells.size)
        }
        undoStack.clear()
        pressedIndex = -1
        downIndex = -1
        stroking = false
        invalidate()
        onChange?.invoke()
    }

    fun filledCount(): Int = cells.count { it }

    fun toGrid(): BooleanArray = cells.copyOf()

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        cells = undoStack.removeAt(undoStack.size - 1)
        invalidate()
        onChange?.invoke()
    }

    fun clearAll() {
        pushUndo()
        cells.fill(false)
        invalidate()
        onChange?.invoke()
    }

    private fun pushUndo() {
        undoStack.add(cells.copyOf())
        if (undoStack.size > 60) undoStack.removeAt(0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        if (size <= 0) return
        val pad = dp(6f)
        val side = min(width - pad * 2f, height - pad * 2f)
        if (side <= 0f) return
        cellSize = side / size
        gridLeft = (width - side) / 2f
        gridTop = (height - side) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        if (cells.isEmpty() || size <= 0) return
        if (cellSize <= 0f) computeGeometry()
        if (cellSize <= 0f) return

        val left = gridLeft
        val top = gridTop
        val right = left + size * cellSize
        val bottom = top + size * cellSize

        fillPaint.color = COLOR_BOARD
        tmp.set(left - dp(2f), top - dp(2f), right + dp(2f), bottom + dp(2f))
        val radius = max(dp(6f), cellSize * 0.10f)
        canvas.drawRoundRect(tmp, radius, radius, fillPaint)

        val inset = max(dp(0.6f), cellSize * 0.045f)
        val cellRadius = max(dp(1.5f), cellSize * 0.14f)
        for (r in 0 until size) {
            for (c in 0 until size) {
                val filled = cells[r * size + c]
                val x = left + c * cellSize
                val y = top + r * cellSize
                fillPaint.color = when {
                    filled -> COLOR_FILLED
                    r * size + c == pressedIndex -> COLOR_PRESS
                    else -> COLOR_EMPTY
                }
                tmp.set(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset)
                canvas.drawRoundRect(tmp, cellRadius, cellRadius, fillPaint)
            }
        }

        linePaint.style = Paint.Style.STROKE
        for (i in 0..size) {
            val strong = (size > 5 && i % 5 == 0) || i == 0 || i == size
            linePaint.color = if (strong) COLOR_LINE_STRONG else COLOR_LINE
            linePaint.strokeWidth =
                if (strong) max(dp(1.1f), cellSize * 0.035f) else max(dp(0.5f), cellSize * 0.02f)
            canvas.drawLine(left + i * cellSize, top, left + i * cellSize, bottom, linePaint)
            canvas.drawLine(left, top + i * cellSize, right, top + i * cellSize, linePaint)
        }
    }

    private fun hitTest(x: Float, y: Float): Int {
        if (cellSize <= 0f) return -1
        if (x < gridLeft || y < gridTop) return -1
        val c = ((x - gridLeft) / cellSize).toInt()
        val r = ((y - gridTop) / cellSize).toInt()
        if (c < 0 || r < 0 || c >= size || r >= size) return -1
        return r * size + c
    }

    private fun paintCell(index: Int) {
        if (index < 0 || index >= cells.size) return
        if (cells[index] == paintValue) return
        cells[index] = paintValue
        if (mirror) {
            val r = index / size
            val c = index % size
            val mirrorIndex = r * size + (size - 1 - c)
            if (mirrorIndex != index) cells[mirrorIndex] = paintValue
        }
        strokeDirty = true
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = hitTest(event.x, event.y)
                if (idx < 0) return false
                downIndex = idx
                pressedIndex = idx
                paintValue = if (eraseMode) false else !cells[idx]
                stroking = false
                strokeDirty = false
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
                    stroking = true
                    pushUndo()
                    paintCell(downIndex)
                    paintCell(idx)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else if (stroking) {
                    paintCell(idx)
                }
                if (idx != pressedIndex) {
                    pressedIndex = idx
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!stroking) {
                    if (downIndex >= 0) {
                        pushUndo()
                        paintCell(downIndex)
                    }
                }
                if (strokeDirty) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onChange?.invoke()
                }
                stroking = false
                strokeDirty = false
                pressedIndex = -1
                downIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (strokeDirty) onChange?.invoke()
                stroking = false
                strokeDirty = false
                pressedIndex = -1
                downIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private val COLOR_BOARD = 0xFF171C28.toInt()
        private val COLOR_EMPTY = 0xFF1F2737.toInt()
        private val COLOR_PRESS = 0xFF2C3A5C.toInt()
        private val COLOR_FILLED = 0xFF7C9CFF.toInt()
        private val COLOR_LINE = 0xFF12161F.toInt()
        private val COLOR_LINE_STRONG = 0xFF39445A.toInt()
    }
}
