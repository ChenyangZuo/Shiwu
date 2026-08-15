package com.zzzcy.shiwu.data

import android.content.Context
import com.zzzcy.shiwu.util.MergeEngine
import com.zzzcy.shiwu.util.WebDavClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebDAV 同步编排：锁 → 下载远端快照 → 本地合并 → 照片增量 → 上传合并结果 → 写回本地库。
 * 全程持锁；失败时释放锁并抛出可读错误。
 */
object WebDavSyncManager {
    const val ROOT = "shiwu"
    const val LOCK = "sync.lock"
    const val DATA = "data.json"
    const val DEVICES = "devices.json"
    const val PHOTOS = "photos"

    private const val LOCK_LEASE_MS = 5 * 60 * 1000L

    data class Config(
        val server: String,
        val username: String,
        val password: String
    )

    suspend fun sync(context: Context, config: Config): SyncReport = withContext(Dispatchers.IO) {
        val client = WebDavClient(config.server, config.username, config.password)
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        client.mkcol(ROOT)
        client.mkcol("$ROOT/$PHOTOS")
        acquireLock(client)
        try {
            val remoteJson = client.get("$ROOT/$DATA")
            val remote = if (remoteJson != null) {
                snapshotFromJson(String(remoteJson, Charsets.UTF_8), photosDir)
            } else {
                SyncData(emptyList(), emptyList(), emptyList(), emptyList())
            }
            val local = ShiwuRepository.loadSyncSnapshot()
            val defaultListId = ShiwuRepository.lists.value.firstOrNull()?.id ?: 0L
            val merged = MergeEngine.merge(
                local = local,
                remote = remote,
                defaultListId = defaultListId,
                newId = { ShiwuRepository.allocSyncId() },
                deviceId = ShiwuRepository.syncDeviceId
            )
            syncPhotos(client, merged.items, remote.items, local, photosDir)

            val snapshot = SyncData(merged.lists, merged.items, merged.wishes, merged.categories)
            client.put("$ROOT/$DATA", snapshot.toSnapshotJson().toByteArray(Charsets.UTF_8))
            client.put("$ROOT/$DEVICES", buildDevicesJson(client).toByteArray(Charsets.UTF_8))
            ShiwuRepository.applySyncData(snapshot)
            merged.report
        } finally {
            runCatching { client.delete("$ROOT/$LOCK") }
        }
    }

    /** 互斥锁：If-None-Match: * 原子创建；超租约可强制抢占 */
    private fun acquireLock(client: WebDavClient) {
        val now = System.currentTimeMillis()
        val body = """{"deviceId":"${ShiwuRepository.syncDeviceId}","time":$now}""".toByteArray(Charsets.UTF_8)
        val code = client.put("$ROOT/$LOCK", body, ifNoneMatch = true)
        if (code == 412) {
            val lockTime = client.get("$ROOT/$LOCK")
                ?.let { Regex("\"time\":(\\d+)").find(String(it))?.groupValues?.get(1)?.toLongOrNull() }
                ?: 0L
            if (now - lockTime > LOCK_LEASE_MS) {
                client.delete("$ROOT/$LOCK")
                val retry = client.put("$ROOT/$LOCK", body, ifNoneMatch = true)
                if (retry == 412) {
                    throw WebDavClient.WebDavException("同步锁被占用，请稍后重试", 412)
                }
            } else {
                throw WebDavClient.WebDavException("同步锁被占用，请稍后重试", 412)
            }
        }
    }

    private fun buildDevicesJson(client: WebDavClient): String {
        val existing = client.get("$ROOT/$DEVICES")
            ?.let { devicesFromJson(String(it, Charsets.UTF_8)) }
            ?: emptyList()
        val merged = LinkedHashMap<String, Triple<String, String, Long>>()
        existing.forEach { merged[it.first] = it }
        merged[ShiwuRepository.syncDeviceId] = Triple(
            ShiwuRepository.syncDeviceId,
            ShiwuRepository.userName.value,
            System.currentTimeMillis()
        )
        return devicesToJson(merged.values.toList())
    }

    /** 照片增量：远端有本地无 → 下载；本地有远端无 → 上传（按文件名） */
    private fun syncPhotos(
        client: WebDavClient,
        mergedItems: List<Item>,
        remoteItems: List<Item>,
        local: SyncData,
        photosDir: File
    ) {
        val localNames = local.items.flatMap { it.photos }.map { File(it).name }.toSet()
        val remoteNames = remoteItems.flatMap { it.photos }.map { File(it).name }.toSet()
        (remoteNames - localNames).forEach { name ->
            runCatching {
                val data = client.get("$ROOT/$PHOTOS/$name") ?: return@forEach
                File(photosDir, name).writeBytes(data)
            }
        }
        (localNames - remoteNames).forEach { name ->
            val f = File(photosDir, name)
            if (f.exists()) {
                runCatching { client.put("$ROOT/$PHOTOS/$name", f.readBytes()) }
            }
        }
    }
}
