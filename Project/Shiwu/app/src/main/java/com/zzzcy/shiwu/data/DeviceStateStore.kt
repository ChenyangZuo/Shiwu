package com.zzzcy.shiwu.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 本机设备状态（设备号 + 各表序列分配器 + 全局 ID 迁移标记）。
 *
 * 存放在 [Context.noBackupFilesDir]：Android 自动备份与换机克隆都不会携带该目录，
 * 因此恢复备份/换机后不会有两台设备共用同一个 deviceId。
 * 注意：不要把这些字段再写回 SharedPreferences（旧版本曾用 prefs，备份会带上）。
 */
data class DeviceState(
    val deviceId: Long,
    val itemSeq: Long,
    val wishSeq: Long,
    val listSeq: Long,
    val gidMigrated: Boolean
)

/**
 * [DeviceState] 的 JSON 持久化：tmp 文件 + rename 的原子写入，所有读写串行化。
 * 文件不存在或损坏时返回 null，由调用方决定重建策略。
 */
class DeviceStateStore(context: Context) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val lock = Any()

    fun read(): DeviceState? = synchronized(lock) {
        if (!file.isFile) return null
        try {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            DeviceState(
                deviceId = o.optLong("deviceId", 0L),
                itemSeq = o.optLong("itemSeq", 0L).coerceAtLeast(0L),
                wishSeq = o.optLong("wishSeq", 0L).coerceAtLeast(0L),
                listSeq = o.optLong("listSeq", 0L).coerceAtLeast(0L),
                gidMigrated = o.optBoolean("gidMigrated", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun write(state: DeviceState) = synchronized(lock) {
        val json = JSONObject().apply {
            put("version", 1)
            put("deviceId", state.deviceId)
            put("itemSeq", state.itemSeq)
            put("wishSeq", state.wishSeq)
            put("listSeq", state.listSeq)
            put("gidMigrated", state.gidMigrated)
        }.toString()
        val dir = file.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            // rename 在同一目录内是原子的；极少数文件系统失败时才退回覆盖写
            if (!file.delete() || !tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "device_state.json"
    }
}
