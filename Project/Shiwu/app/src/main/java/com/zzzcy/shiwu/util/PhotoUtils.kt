package com.zzzcy.shiwu.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PhotoUtils {
    const val FILE_PROVIDER_AUTHORITY = "com.zzzcy.shiwu.fileprovider"

    /**
     * 按目标尺寸采样解码，避免高清原图（如 1 亿像素）解码后占满内存。
     * 返回的位图分辨率不小于目标尺寸，但不会超过其 2 倍。
     */
    fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var inSampleSize = 1
            while (
                bounds.outWidth / (inSampleSize * 2) >= reqWidth &&
                bounds.outHeight / (inSampleSize * 2) >= reqHeight
            ) {
                inSampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        }
    }

    /** 把相册选中的图片复制到应用私有目录，返回本地路径 */
    fun copyToInternal(context: Context, uri: Uri): String? = try {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val out = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        out.absolutePath
    } catch (_: Exception) {
        null
    }

    /** 为拍照创建目标文件与 content Uri */
    fun createCameraUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return file to uri
    }

    /** 拍照后把缓存文件移入私有目录 */
    fun moveCameraPhoto(context: Context, tempFile: File?): String? {
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) return null
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val out = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        return try {
            tempFile.copyTo(out, overwrite = true)
            tempFile.delete()
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
