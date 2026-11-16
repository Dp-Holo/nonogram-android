package com.dsha.nonogram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** 图片转关卡的预览：把 N×N 的黑白格子画出来 */
class GridPreviewView(context: Context) : View(context) {

    private var grid: BooleanArray = BooleanArray(0)
    private var size = 0

    private val backgroundPaint = Paint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setData(grid: BooleanArray, size: Int) {
        this.grid = grid
        this.size = size
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (size <= 0) return
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val cell = side / size
        val ox = (width - side) / 2f
        val oy = (height - side) / 2f

        backgroundPaint.color = 0xFF171C28.toInt()
        canvas.drawRect(ox, oy, ox + side, oy + side, backgroundPaint)

        fillPaint.color = 0xFF7C9CFF.toInt()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!grid[r * size + c]) continue
                val x = ox + c * cell
                val y = oy + r * cell
                canvas.drawRect(
                    x + cell * 0.08f,
                    y + cell * 0.08f,
                    x + cell * 0.92f,
                    y + cell * 0.92f,
                    fillPaint
                )
            }
        }
    }
}
