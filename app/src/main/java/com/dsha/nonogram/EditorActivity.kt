package com.dsha.nonogram

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 关卡编辑器：画一个图案（或用图片转换出来的图案做像素修正），
 * 完成后自动生成行列提示数字，保存为「我的关卡」并直接开玩。
 */
class EditorActivity : Activity() {

    private lateinit var view: EditorView
    private lateinit var store: PuzzleStore
    private lateinit var sizeLabel: TextView
    private lateinit var countLabel: TextView
    private lateinit var brushButton: TextView
    private lateinit var mirrorButton: TextView

    private var size = 10
    private var namePrefix = "自编"
    private var eraseMode = false
    private var mirror = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PuzzleStore(this)
        size = intent.getIntExtra(EXTRA_SIZE, 10).coerceIn(5, 20)
        namePrefix = intent.getStringExtra(EXTRA_PREFIX) ?: "自编"
        buildUi()
        val initial = intent.getStringExtra(EXTRA_ROWS)?.let { parseRows(it, size) }
        view.load(size, initial)
        updateLabels()
        if (initial == null) {
            toast("点按或拖动涂色，完成后自动生成提示数字")
        }
    }

    private fun parseRows(rows: String, size: Int): BooleanArray? {
        val lines = rows.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size != size) return null
        val out = BooleanArray(size * size)
        for (r in 0 until size) {
            if (lines[r].length != size) return null
            for (c in 0 until size) {
                out[r * size + c] = lines[r][c] == '#'
            }
        }
        return out
    }

    // ------------------------------------------------------------ 界面

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "自编关卡"
            setTextColor(TEXT)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = if (namePrefix == "图片") "把图片转出来的图案修一修" else "画出图案，提示数字自动生成"
            setTextColor(TEXT_DIM)
            textSize = 11f
        }
        titles.addView(title)
        titles.addView(subtitle)

        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        sizeLabel = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        countLabel = TextView(this).apply {
            setTextColor(TEXT_DIM)
            textSize = 12f
            gravity = Gravity.END
        }
        metaBox.addView(sizeLabel)
        metaBox.addView(countLabel)

        header.addView(
            titles,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(metaBox)
        column.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        view = EditorView(this)
        view.onChange = { updateLabels() }
        column.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(12f) }
        )

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        brushButton = makeButton("画笔") {
            eraseMode = !eraseMode
            view.eraseMode = eraseMode
            updateButtons()
        }
        mirrorButton = makeButton("对称") { toggleMirror() }
        val undoButton = makeButton("撤销") { view.undo() }
        val clearButton = makeButton("清空") { view.clearAll() }
        val doneButton = makeButton("完成") { onDone() }
        val buttons = listOf(brushButton, mirrorButton, undoButton, clearButton, doneButton)
        buttons.forEachIndexed { index, button ->
            footer.addView(
                button,
                LinearLayout.LayoutParams(0, dp(46f), 1f).apply {
                    if (index > 0) marginStart = dp(6f)
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
        tv.textSize = 12.5f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.background = rounded(BTN_BG, BTN_STROKE, 12f)
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener { onClick() }
        tv.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.alpha = 0.55f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
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

    private fun toggleMirror() {
        mirror = !mirror
        view.mirror = mirror
        updateButtons()
    }

    private fun updateButtons() {
        brushButton.text = if (eraseMode) "橡皮" else "画笔"
        brushButton.setTextColor(if (eraseMode) ACCENT else TEXT_DIM)
        brushButton.background = rounded(
            if (eraseMode) BTN_ACTIVE else BTN_BG,
            if (eraseMode) ACCENT else BTN_STROKE,
            12f
        )

        mirrorButton.text = if (mirror) "对称·开" else "对称"
        mirrorButton.setTextColor(if (mirror) ACCENT else TEXT_DIM)
        mirrorButton.background = rounded(
            if (mirror) BTN_ACTIVE else BTN_BG,
            if (mirror) ACCENT else BTN_STROKE,
            12f
        )
    }

    private fun updateLabels() {
        sizeLabel.text = "${size}×${size}"
        countLabel.text = "已涂 ${view.filledCount()} 格"
    }

    // ------------------------------------------------------------ 完成

    private fun onDone() {
        val filled = view.filledCount()
        if (filled == 0) {
            toast("先画点东西吧")
            return
        }
        if (filled == size * size) {
            toast("全部涂满就没有提示数字了，留点空白")
            return
        }
        val grid = view.toGrid()
        val puzzle = Puzzle.fromSolution("tmp", "tmp", size, grid)
        val probe = IntArray(size * size)
        val unique = Generator.propagate(size, puzzle.rowClues, puzzle.colClues, probe)

        val message = if (unique) {
            "共 $filled 格，这道题逻辑上唯一可解。"
        } else {
            "共 $filled 格。注意：这道题可能有多个解，玩家即使填出另一种合法图案也不会判过关。"
        }

        AlertDialog.Builder(this)
            .setTitle("保存这一关？")
            .setMessage(message)
            .setPositiveButton("保存并开始玩") { _, _ -> saveAndFinish(grid) }
            .setNegativeButton("继续画", null)
            .show()
    }

    private fun saveAndFinish(grid: BooleanArray) {
        val puzzle = Puzzle.fromSolution(
            "edit_${System.currentTimeMillis()}",
            "$namePrefix ${size}×${size} #${store.count() + 1}",
            size,
            grid
        )
        store.add(puzzle)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_KEY, puzzle.key))
        finish()
    }

    // ------------------------------------------------------------ 工具

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_SIZE = "size"
        const val EXTRA_ROWS = "rows"
        const val EXTRA_PREFIX = "prefix"
        const val EXTRA_KEY = "key"

        private val BG = 0xFF12141A.toInt()
        private val TEXT = 0xFFE7ECF7.toInt()
        private val TEXT_DIM = 0xFF8A93A8.toInt()
        private val ACCENT = 0xFF7C9CFF.toInt()
        private val BTN_BG = 0xFF1B2130.toInt()
        private val BTN_ACTIVE = 0xFF24305A.toInt()
        private val BTN_STROKE = 0xFF2B3242.toInt()
    }
}
