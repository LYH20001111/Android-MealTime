package com.skyanchor.mealtime.core.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 备份导出前的图片压缩（规格文档 §12）：
 * 最长边约 1600px、WebP、质量 82。
 * 解码/编码失败时返回 null，由调用方回退为原样复制。
 */
object ImageCompressor {

    const val MAX_DIMENSION = 1600
    const val WEBP_QUALITY = 82

    /** @return 压缩后的 WebP 字节；无法解码或编码失败返回 null */
    fun compressToWebpBytes(source: File): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return null
        val rotated = applyExifRotation(source, decoded)
        val scaled = scaleDown(rotated, MAX_DIMENSION)

        val out = ByteArrayOutputStream()
        val ok = scaled.compress(webpFormat(), WEBP_QUALITY, out)
        if (ok) out.toByteArray() else null
    }.getOrNull()

    /** 计算 2 的幂次采样率，使解码后尺寸仍不小于目标（避免过度缩小） */
    private fun calcInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    /** 精确缩放到最长边不超过 maxDimension（仅缩小，不放大） */
    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** 按相册照片的 EXIF 方向旋转，避免重编码后方向丢失 */
    private fun applyExifRotation(source: File, bitmap: Bitmap): Bitmap = runCatching {
        val orientation = readExifOrientation(source)
        val degrees = when (orientation) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)

    @Suppress("DEPRECATION") // android.media.ExifInterface 读取 JPEG 方向足够可靠，避免新增依赖
    private fun readExifOrientation(source: File): Int =
        runCatching { android.media.ExifInterface(source.absolutePath).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        ) }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
}
