package com.dsha.nonogram

/** 完成时间的统计计算（纯函数，方便单元测试） */
object TimeStats {

    data class Summary(
        val count: Int,
        val meanMs: Long,
        val medianMs: Long,
        val bestMs: Long,
        val p25Ms: Long = 0L,
        val p75Ms: Long = 0L
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
        return Summary(count, mean, median, sorted.first(), percentile(sorted, 0.25), percentile(sorted, 0.75))
    }

    /** 分位数（线性插值），times 不必有序 */
    fun percentile(times: List<Long>, p: Double): Long {
        if (times.isEmpty()) return 0L
        val sorted = times.sorted()
        if (sorted.size == 1) return sorted[0]
        val pos = (sorted.size - 1) * p.coerceIn(0.0, 1.0)
        val lower = kotlin.math.floor(pos).toInt()
        val upper = kotlin.math.ceil(pos).toInt()
        if (lower == upper) return sorted[lower]
        val frac = pos - lower
        return (sorted[lower] * (1 - frac) + sorted[upper] * frac).toLong()
    }
}
