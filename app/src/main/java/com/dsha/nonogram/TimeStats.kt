package com.dsha.nonogram

/** 完成时间的统计计算（纯函数，方便单元测试） */
object TimeStats {

    data class Summary(
        val count: Int,
        val meanMs: Long,
        val medianMs: Long,
        val bestMs: Long
    )

    fun summarize(times: List<Long>): Summary {
        if (times.isEmpty()) return Summary(0, 0L, 0L, 0L)
        val sorted = times.sorted()
        val count = sorted.size
        val mean = sorted.sum() / count
        val median = if (count % 2 == 1) {
            sorted[count / 2]
        } else {
            (sorted[count / 2 - 1] + sorted[count / 2]) / 2
        }
        return Summary(count, mean, median, sorted.first())
    }
}
