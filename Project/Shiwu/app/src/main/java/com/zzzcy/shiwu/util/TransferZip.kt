package com.zzzcy.shiwu.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** CSV + 照片 打包/解包工具 */
object TransferZip {
    private const val CSV_ENTRY = "items.csv"

    /** 生成 ZIP：items.csv + photos/ 目录下的照片文件 */
    fun buildExportZip(csv: String, photos: List<File>, dest: File): Boolean = try {
        ZipOutputStream(dest.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(CSV_ENTRY))
            zos.write(csv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            photos.forEach { p ->
                if (p.exists() && p.isFile) {
                    zos.putNextEntry(ZipEntry("photos/${p.name}"))
                    p.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 解压导入包：返回 CSV 文本 + 照片文件名 → 应用私有目录绝对路径 的映射。
     * 照片会被复制进 files/photos/ 供导入后直接引用。
     */
    fun importZip(context: Context, uri: Uri): Pair<String, Map<String, String>>? = try {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        var csv: String? = null
        val photoMap = LinkedHashMap<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == CSV_ENTRY -> csv = zis.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("photos/") && entry.name.length > "photos/".length -> {
                            val fileName = entry.name.substringAfterLast('/')
                            if (fileName.isNotBlank()) {
                                val out = File(dir, fileName)
                                out.outputStream().use { zis.copyTo(it) }
                                photoMap[fileName] = out.absolutePath
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        csv?.let { it to photoMap }
    } catch (_: Exception) {
        null
    }
}
