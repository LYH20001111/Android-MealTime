package com.skyanchor.mealtime.core.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把系统相册选中的图片复制到应用私有目录（filesDir/<directory>），
 * 返回 file:// URI；复制失败返回 null。菜谱封面与食材照片共用。
 */
suspend fun copyImageToPrivate(context: Context, uri: android.net.Uri, directory: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, directory).apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            "file://" + file.absolutePath
        }.getOrNull()
    }
