package com.dsha.nonogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/**
 * 图片 -> 数织关卡。
 * 把图片缩到 N×N 区块，取每块平均亮度，再按阈值二值化。
 */
object ImageImport {

    /** 读取并等比缩小图片，避免大图 OOM */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 640): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 每个格子的平均亮度（0 黑 ~ 255 白）；透明像素按白色处理 */
    fun cellLuminance(bitmap: Bitmap, size: Int): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = IntArray(size * size)
        for (r in 0 until size) {
            val y0 = r * h / size
            val y1 = max(y0 + 1, (r + 1) * h / size)
            for (c in 0 until size) {
                val x0 = c * w / size
                val x1 = max(x0 + 1, (c + 1) * w / size)
                var sum = 0L
                var count = 0
                var y = y0
                while (y < y1) {
                    val offset = y * w
                    var x = x0
                    while (x < x1) {
                        val pixel = pixels[offset + x]
                        val alpha = (pixel ushr 24) and 0xFF
                        if (alpha < 48) {
                            sum += 255L
                        } else {
                            val red = (pixel shr 16) and 0xFF
                            val green = (pixel shr 8) and 0xFF
                            val blue = pixel and 0xFF
                            sum += (299L * red + 587L * green + 114L * blue) / 1000L
                        }
                        count++
                        x++
                    }
                    y++
                }
                out[r * size + c] = if (count == 0) 255 else (sum / count).toInt()
            }
        }
        return out
    }

    fun gridFrom(luminance: IntArray, threshold: Int, invert: Boolean): BooleanArray {
        val out = BooleanArray(luminance.size)
        for (i in luminance.indices) {
            val v = luminance[i]
            out[i] = if (invert) v >= threshold else v < threshold
        }
        return out
    }

    /** Otsu 自动阈值，返回 1..254 */
    fun otsu(luminance: IntArray): Int {
        val hist = IntArray(256)
        for (v in luminance) hist[v.coerceIn(0, 255)]++
        val total = luminance.size
        if (total == 0) return 128

        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]

        var sumB = 0.0
        var weightB = 0
        var best = -1.0
        var threshold = 128
        for (i in 0..255) {
            weightB += hist[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i.toDouble() * hist[i]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = i + 1
            }
        }
        return threshold.coerceIn(1, 254)
    }
}
