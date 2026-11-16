package com.dsha.nonogram

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var store: PuzzleStore

    private lateinit var board: BoardView
    private lateinit var nameView: TextView
    private lateinit var infoView: TextView
    private lateinit var undoButton: TextView
    private lateinit var markButton: TextView

    private lateinit var current: Puzzle
    private lateinit var state: GameState

    private var solved = false
    private var markMode = false
    private var startAt = 0L
    private var elapsedMs = 0L
    private var timerRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private var levelsDialog: AlertDialog? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            elapsedMs = System.currentTimeMillis() - startAt
            updateHeader()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = PuzzleStore(this)
        buildUi()
        loadPuzzleByKey(prefs.lastPuzzleKey())
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

        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        nameView = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(160f)
            gravity = Gravity.END
        }
        infoView = TextView(this).apply {
            setTextColor(TEXT_DIM)
            textSize = 12f
            gravity = Gravity.END
        }
        metaBox.addView(nameView)
        metaBox.addView(infoView)

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

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        undoButton = makeButton("撤销") { onUndo() }
        markButton = makeButton("标记") { toggleMark() }
        val resetButton = makeButton("重来") { onReset() }
        val levelButton = makeButton("关卡") { showLevels() }
        listOf(undoButton, markButton, resetButton, levelButton).forEachIndexed { index, button ->
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.alpha = if (v.isEnabled) 1f else 0.4f
            }
            false
        }
        return tv
    }

    private fun makeChip(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(TEXT)
        tv.textSize = 14f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.background = rounded(BTN_BG, BTN_STROKE, 10f)
        tv.isClickable = true
        tv.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        tv.setOnClickListener { onClick() }
        tv.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.alpha = 0.6f
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

    // ---------------------------------------------------------- 关卡状态

    private fun loadPuzzleByKey(key: String) {
        val puzzle = Builtins.find(key) ?: store.find(key) ?: Builtins.puzzles[0]
        loadPuzzle(puzzle)
    }

    private fun loadPuzzle(puzzle: Puzzle) {
        handler.removeCallbacks(ticker)
        timerRunning = false

        current = puzzle
        state = GameState(puzzle)

        val saved = prefs.loadCells(puzzle.key)
        if (saved != null && saved.size == state.cells.size) {
            System.arraycopy(saved, 0, state.cells, 0, saved.size)
        }

        board.state = state
        board.markMode = markMode

        solved = state.isSolved()
        elapsedMs = 0L
        if (solved) {
            elapsedMs = prefs.bestTime(puzzle.key)
        } else {
            startTimer()
        }

        prefs.setLastPuzzle(puzzle.key)
        updateHeader()
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

    private fun updateHeader() {
        nameView.text = current.name
        val timeText = if (solved) {
            if (elapsedMs > 0L) "✓ ${formatTime(elapsedMs)}" else "✓ 已通关"
        } else {
            formatTime(elapsedMs)
        }
        infoView.text = "${current.size}×${current.size} · $timeText"
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
        prefs.saveCells(current.key, state.cells)
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
        prefs.saveCells(current.key, state.cells)
        if (solved) {
            solved = false
            startTimer()
        }
        updateHeader()
        updateButtons()
        board.invalidate()
    }

    private fun onBoardChanged() {
        prefs.saveCells(current.key, state.cells)
        updateButtons()
        if (!solved && state.isSolved()) {
            solved = true
            timerRunning = false
            handler.removeCallbacks(ticker)
            elapsedMs = System.currentTimeMillis() - startAt
            prefs.markSolved(current.key, elapsedMs)
            updateHeader()
            showWin()
        }
    }

    // ------------------------------------------------------------ 弹窗

    private fun showWin() {
        val puzzle = current
        val message = TextView(this).apply {
            text = "${puzzle.name} · ${puzzle.size}×${puzzle.size}\n用时 ${formatTime(elapsedMs)}"
            setTextColor(TEXT)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        val next = Builtins.next(puzzle)
        val generated = puzzle.key.startsWith("gen_")
        val builder = AlertDialog.Builder(this)
            .setTitle("🎉 过关！")
            .setView(message)
            .setNegativeButton("关闭", null)
        when {
            // 内置关卡：进入下一关内置关
            next != null -> builder.setPositiveButton("下一关") { _, _ -> loadPuzzle(next) }
            // 随机关卡：同尺寸再出一道新的（图案不与已有题目重复）
            generated -> builder.setPositiveButton("下一关") { _, _ -> generateLevel(puzzle.size) }
            // 图片关卡：回到选关
            else -> builder.setPositiveButton("选关") { _, _ -> showLevels() }
        }
        builder.show()
    }

    private fun showLevels() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), dp(8f))
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10f), dp(4f), dp(10f), dp(10f))
        }
        val genButton = makeChip("🎲 随机出题") {
            levelsDialog?.dismiss()
            showGenerateDialog()
        }
        val imgButton = makeChip("🖼 图片导入") {
            levelsDialog?.dismiss()
            pickImage()
        }
        actions.addView(
            genButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8f)
            }
        )
        actions.addView(imgButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        root.addView(sectionLabel("内置关卡"))
        for (puzzle in Builtins.puzzles) {
            root.addView(makeLevelRow(puzzle, withDelete = false))
        }

        root.addView(sectionLabel("我的关卡"))
        val mine = store.all()
        if (mine.isEmpty()) {
            val hint = TextView(this).apply {
                text = "还没有自建关卡：用上面的「随机出题」或「图片导入」生成一关，会自动保存在这里。"
                setTextColor(TEXT_DIM)
                textSize = 13f
                setPadding(dp(16f), dp(8f), dp(16f), dp(14f))
            }
            root.addView(hint)
        } else {
            for (puzzle in mine) {
                root.addView(makeLevelRow(puzzle, withDelete = true))
            }
        }

        val scroll = ScrollView(this).apply { addView(root) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择关卡")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .create()
        levelsDialog = dialog
        dialog.show()
    }

    private fun makeLevelRow(puzzle: Puzzle, withDelete: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(8f), dp(12f))
            isClickable = true
        }

        val isSolvedLevel = prefs.isSolved(puzzle.key)
        val best = prefs.bestTime(puzzle.key)
        val label = TextView(this).apply {
            text = buildString {
                append(puzzle.name)
                append("    ")
                append(puzzle.size).append("×").append(puzzle.size)
                if (isSolvedLevel) append("    ✓")
                if (best > 0L) append("    ").append(formatTime(best))
            }
            setTextColor(
                when {
                    puzzle.key == current.key -> ACCENT
                    isSolvedLevel -> TEXT_DIM
                    else -> TEXT
                }
            )
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            levelsDialog?.dismiss()
            loadPuzzle(puzzle)
        }

        if (withDelete) {
            val delete = TextView(this).apply {
                text = "✕"
                setTextColor(TEXT_DIM)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                isClickable = true
            }
            delete.setOnClickListener {
                store.remove(puzzle.key)
                prefs.forget(puzzle.key)
                toast("已删除：${puzzle.name}")
                levelsDialog?.dismiss()
                if (current.key == puzzle.key) loadPuzzle(Builtins.puzzles[0])
            }
            row.addView(delete)
        }
        return row
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(ACCENT)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(16f), dp(14f), dp(16f), dp(6f))
    }

    // -------------------------------------------------------- 随机生成

    private fun showGenerateDialog() {
        var size = 10

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(8f))
        }

        val sizeText = TextView(this).apply {
            text = "$size × $size"
            setTextColor(TEXT)
            textSize = 34f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        column.addView(sizeText)

        val hint = TextView(this).apply {
            text = "拖动滑杆选择边长（$MIN_SIZE ~ $MAX_SIZE 格）"
            setTextColor(TEXT_DIM)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        column.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        )

        val bar = SeekBar(this).apply {
            max = MAX_SIZE - MIN_SIZE
            progress = size - MIN_SIZE
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                size = progress + MIN_SIZE
                sizeText.text = "$size × $size"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        column.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }
        )

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (preset in intArrayOf(5, 10, 15)) {
            val chip = makeChip("$preset×$preset") {
                size = preset
                bar.progress = preset - MIN_SIZE
                sizeText.text = "$size × $size"
            }
            presets.addView(
                chip,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (preset == 15) 0 else dp(8f)
                }
            )
        }
        column.addView(
            presets,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) }
        )

        AlertDialog.Builder(this)
            .setTitle("随机出题")
            .setView(column)
            .setPositiveButton("开始生成") { _, _ -> generateLevel(size) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun generateLevel(size: Int) {
        val loading = TextView(this).apply {
            text = "正在生成 ${size}×${size} 关卡…\n（保证逻辑可解，不需要猜）"
            setTextColor(TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(22f), dp(24f), dp(22f))
        }
        val dialog = AlertDialog.Builder(this)
            .setView(loading)
            .setCancelable(false)
            .create()
        dialog.show()

        // 同尺寸已出现过的图案全部收集起来，保证新题不重复（在 UI 线程算好再交给后台线程）
        val existing = existingPatterns(size)
        Thread {
            var solution: BooleanArray? = null
            var tries = 0
            while (tries < MAX_GENERATE_TRIES) {
                tries++
                val candidate = Generator.generate(size) ?: break
                if (!existing.contains(Puzzle.rowsOf(candidate, size))) {
                    solution = candidate
                    break
                }
            }
            val generated = solution
            runOnUiThread {
                dialog.dismiss()
                if (generated == null) {
                    toast("生成失败，请再试一次")
                    return@runOnUiThread
                }
                val name = "随机 ${size}×${size} #${store.count() + 1}"
                val puzzle = Puzzle.fromSolution(
                    "gen_${System.currentTimeMillis()}",
                    name,
                    size,
                    generated
                )
                store.add(puzzle)
                loadPuzzle(puzzle)
            }
        }.start()
    }

    /** 同尺寸已经出现过的图案（内置 + 我的关卡），用于连续出题时去重 */
    private fun existingPatterns(size: Int): HashSet<String> {
        val patterns = HashSet<String>()
        for (puzzle in Builtins.puzzles) {
            if (puzzle.size == size) patterns.add(puzzle.toRowsString())
        }
        for (puzzle in store.all()) {
            if (puzzle.size == size) patterns.add(puzzle.toRowsString())
        }
        // 当前正在玩的题目也要排除（即使它刚刚被从列表里删掉）
        if (this::current.isInitialized && current.size == size) {
            patterns.add(current.toRowsString())
        }
        return patterns
    }

    // ---------------------------------------------------------- 图片导入

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQ_PICK_IMAGE)
        } catch (e: Exception) {
            toast("没有可用的图片选择器")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val bitmap = ImageImport.loadBitmap(this, uri)
        if (bitmap == null) {
            toast("图片读取失败，换一张试试")
            return
        }
        showImageDialog(bitmap)
    }

    private fun showImageDialog(bitmap: Bitmap) {
        var size = 10
        var invert = false
        var luminance = ImageImport.cellLuminance(bitmap, size)
        var threshold = ImageImport.otsu(luminance)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(12f), dp(18f), dp(6f))
        }

        val preview = GridPreviewView(this)
        column.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(210f)
            )
        )

        fun refreshPreview() {
            val grid = ImageImport.gridFrom(luminance, threshold, invert)
            preview.setData(grid, size)
        }

        val sizeLabel = TextView(this).apply {
            text = "尺寸  $size × $size"
            setTextColor(TEXT)
            textSize = 13f
            setPadding(0, dp(10f), 0, 0)
        }
        column.addView(sizeLabel)

        val sizeBar = SeekBar(this).apply {
            max = MAX_SIZE - MIN_SIZE
            progress = size - MIN_SIZE
        }
        column.addView(sizeBar)

        val thresholdLabel = TextView(this).apply {
            text = "阈值  $threshold"
            setTextColor(TEXT)
            textSize = 13f
            setPadding(0, dp(6f), 0, 0)
        }
        column.addView(thresholdLabel)

        val thresholdBar = SeekBar(this).apply {
            max = 255
            progress = threshold
        }
        column.addView(thresholdBar)

        lateinit var invertButton: TextView
        invertButton = makeChip("反色：关") {
            invert = !invert
            invertButton.text = if (invert) "反色：开" else "反色：关"
            refreshPreview()
        }
        column.addView(
            invertButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) }
        )

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                size = progress + MIN_SIZE
                sizeLabel.text = "尺寸  $size × $size"
                luminance = ImageImport.cellLuminance(bitmap, size)
                threshold = ImageImport.otsu(luminance)
                thresholdBar.progress = threshold
                thresholdLabel.text = "阈值  $threshold"
                refreshPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        thresholdBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                threshold = progress
                thresholdLabel.text = "阈值  $threshold"
                refreshPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        refreshPreview()

        AlertDialog.Builder(this)
            .setTitle("图片转关卡")
            .setView(column)
            .setPositiveButton("开始玩") { _, _ ->
                val grid = ImageImport.gridFrom(luminance, threshold, invert)
                val filled = grid.count { it }
                if (filled == 0 || filled == grid.size) {
                    toast("这张图转换不出有效关卡，调一下阈值或换张图")
                } else {
                    val puzzle = Puzzle.fromSolution(
                        "img_${System.currentTimeMillis()}",
                        "图片 ${size}×${size}",
                        size,
                        grid
                    )
                    store.add(puzzle)
                    loadPuzzle(puzzle)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ------------------------------------------------------------ 工具

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    // -------------------------------------------------------- 生命周期

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
            prefs.saveCells(current.key, state.cells)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val REQ_PICK_IMAGE = 1001
        private const val MIN_SIZE = 5
        private const val MAX_SIZE = 20
        private const val MAX_GENERATE_TRIES = 20

        private val BG = 0xFF12141A.toInt()
        private val TEXT = 0xFFE7ECF7.toInt()
        private val TEXT_DIM = 0xFF8A93A8.toInt()
        private val ACCENT = 0xFF7C9CFF.toInt()
        private val BTN_BG = 0xFF1B2130.toInt()
        private val BTN_ACTIVE = 0xFF24305A.toInt()
        private val BTN_STROKE = 0xFF2B3242.toInt()
    }
}
