package com.zzzcy.shiwu.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import android.net.Uri
import android.media.ExifInterface
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PhotoUtils {
    const val FILE_PROVIDER_AUTHORITY = "com.zzzcy.shiwu.fileprovider"

    /** 保存时压缩：长边上限（像素），足够清晰且显著减小文件体积与解码开销 */
    private const val MAX_EDGE = 2048

    /** 保存时压缩：JPEG 质量 */
    private const val JPEG_QUALITY = 88

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

    /**
     * 把相册选中的图片保存到应用私有目录。
     * 落盘前统一做降采样压缩：长边不超过 [MAX_EDGE]、JPEG 重编码、按 EXIF 摆正方向，
     * 不再保留原始高清图；压缩失败时兜底复制原图，保证不丢照片。
     */
    fun copyToInternal(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val tmp = File(dir, "tmp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                tmp.delete()
                null
            } else {
                stream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                val final = compressTo(dir, tmp) ?: fallbackCopy(dir, tmp)
                tmp.delete()
                final.absolutePath
            }
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    /** 为拍照创建目标文件与 content Uri */
    fun createCameraUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return file to uri
    }

    /**
     * 拍照后把缓存文件存入私有目录（同样做降采样压缩与 EXIF 摆正，不保留原图）。
     */
    fun moveCameraPhoto(context: Context, tempFile: File?): String? {
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) return null
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val final = compressTo(dir, tempFile) ?: fallbackCopy(dir, tempFile)
        tempFile.delete()
        return final.absolutePath
    }

    /**
     * 启动后后台执行：把「保存时压缩」上线前存的旧照片原地压缩（长边超过 [MAX_EDGE] 的）。
     * 压缩成功后原子替换同名文件，数据库中的路径引用保持不变；
     * 压缩后长边即等于 [MAX_EDGE]，下次启动自动跳过，天然幂等。
     */
    fun recompressExistingPhotos(context: Context) {
        val dir = File(context.filesDir, "photos")
        val files = dir.listFiles() ?: return
        files.forEach { f ->
            if (!f.isFile || !f.name.startsWith("photo_")) return@forEach
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (maxOf(bounds.outWidth, bounds.outHeight) <= MAX_EDGE) return@forEach

            val compressed = compressTo(dir, f) ?: return@forEach
            if (compressed.length() > 0L && compressed.length() < f.length()) {
                // 同目录 renameTo 即原子替换；失败则丢弃压缩产物，保留原图
                if (!compressed.renameTo(f)) compressed.delete()
            } else {
                compressed.delete()
            }
        }
    }

    /** 降采样 + EXIF 摆正 + JPEG 重编码；失败返回 null */
    private fun compressTo(dir: File, input: File): File? {
        var decoded: Bitmap? = null
        var bmp: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var inSampleSize = 1
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (longEdge / (inSampleSize * 2) >= MAX_EDGE) inSampleSize *= 2

            val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            decoded = BitmapFactory.decodeFile(input.absolutePath, opts) ?: return null

            val degrees = exifRotationDegrees(input)
            bmp = if (degrees == 0) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }

            // 幂等采样只能把长边压到 2048~4096，这里再等比缩放，确保精确不超过 MAX_EDGE
            bmp = scaleToMaxEdge(bmp)

            val out = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
            val ok = out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            return if (ok && out.length() > 0L) out else null
        } catch (_: Exception) {
            return null
        } finally {
            bmp?.recycle()
            if (decoded != null && decoded !== bmp) decoded.recycle()
        }
    }

    /** 等比缩放到长边不超过 [MAX_EDGE]；需要缩放时回收原图 */
    private fun scaleToMaxEdge(src: Bitmap): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= MAX_EDGE) return src
        val scale = MAX_EDGE.toFloat() / longEdge
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    /** 压缩失败时的兜底：原样复制到正式文件名，保证照片不丢 */
    private fun fallbackCopy(dir: File, input: File): File {
        val out = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        input.copyTo(out, overwrite = true)
        return out
    }

    /** 读取 JPEG EXIF 旋转角度（0 / 90 / 180 / 270），读取失败按 0 处理 */
    private fun exifRotationDegrees(file: File): Int = try {
        val iface = ExifInterface(file.absolutePath)
        when (iface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
}
