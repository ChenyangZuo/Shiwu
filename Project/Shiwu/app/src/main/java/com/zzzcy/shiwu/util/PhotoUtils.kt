package com.zzzcy.shiwu.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PhotoUtils {
    const val FILE_PROVIDER_AUTHORITY = "com.zzzcy.shiwu.fileprovider"

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
