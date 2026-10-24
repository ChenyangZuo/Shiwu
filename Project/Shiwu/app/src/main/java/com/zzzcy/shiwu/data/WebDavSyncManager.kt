package com.zzzcy.shiwu.data

import android.content.Context
import android.util.Log
import com.zzzcy.shiwu.util.MergeEngine
import com.zzzcy.shiwu.util.WebDavClient
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * WebDAV 同步编排：锁 → 下载远端快照 → 本地合并 → 照片增量 → 备份上一版 → 原子写入新快照
 * → devices.json → 写回本地库。全程持锁；失败时只释放自己持有的锁并抛出可读错误。
 */
object WebDavSyncManager {
    const val ROOT = "shiwu"
    const val LOCK = "sync.lock"
    const val DATA = "data.json"
    const val DEVICES = "devices.json"
    const val PHOTOS = "photos"

    private const val DATA_TMP = "data.json.tmp"
    private const val DATA_BAK = "data.json.bak"

    /** 锁租约：持有者超过该时间未续租视为失联，可被抢占 */
    private const val LOCK_LEASE_MS = 30 * 60 * 1000L

    /** 每处理 N 张照片续租一次，避免长同步中途锁过期 */
    private const val PHOTO_RENEW_INTERVAL = 50

    private const val TAG = "ShiwuSync"

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
        // 必须在 try 之前获取锁：获取失败时不能进入 finally 误删别人的锁。
        val lockToken = acquireLock(client)
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
                deviceId = ShiwuRepository.syncDeviceId,
                newWishId = { ShiwuRepository.allocSyncWishId() },
                newListId = { ShiwuRepository.allocSyncListId() }
            )

            renewLock(client, lockToken)
            val failedPhotos = syncPhotos(client, lockToken, photosDir)
            if (failedPhotos > 0) {
                // 必须在写 data.json / applySyncData 之前失败：syncedRev 不推进，下次同步重试。
                throw WebDavClient.WebDavException("照片同步失败 $failedPhotos 张，已中止；请重试")
            }
            renewLock(client, lockToken)

            val snapshot = SyncData(merged.lists, merged.items, merged.wishes, merged.categories)
            backupSnapshot(client)
            writeSnapshotAtomic(client, snapshot.toSnapshotJson().toByteArray(Charsets.UTF_8))
            client.put("$ROOT/$DEVICES", buildDevicesJson(client).toByteArray(Charsets.UTF_8))
            ShiwuRepository.applySyncData(snapshot, expected = local)
            merged.report
        } finally {
            releaseLock(client, lockToken)
        }
    }

    // ---------- 锁：JSON 锁体 + token 所有权 ----------

    private fun lockBody(token: String, time: Long): ByteArray = JSONObject().apply {
        put("deviceId", ShiwuRepository.syncDeviceId)
        put("token", token)
        put("time", time)
    }.toString().toByteArray(Charsets.UTF_8)

    private fun parseLock(payload: ByteArray?): Pair<String, Long>? {
        if (payload == null) return null
        return try {
            val o = JSONObject(String(payload, Charsets.UTF_8))
            val token = o.optString("token", "")
            if (token.isBlank()) null else token to o.optLong("time", 0L)
        } catch (e: Exception) {
            Log.w(TAG, "解析同步锁失败", e)
            null
        }
    }

    /**
     * 互斥锁：If-None-Match: * 原子创建；超租约可抢占。
     * 成功后回读锁并校验 token，防止服务器不支持条件 PUT 时误以为持锁。
     */
    private fun acquireLock(client: WebDavClient): String {
        val token = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val path = "$ROOT/$LOCK"
        val body = lockBody(token, now)

        var code = client.put(path, body, ifNoneMatch = true)
        if (code == 412) {
            val existing = parseLock(client.get(path))
                ?: throw WebDavClient.WebDavException("同步锁被占用，请稍后重试", 412)
            val (holderDevice, lockTime) = existing
            if (now - lockTime > LOCK_LEASE_MS) {
                Log.w(TAG, "同步锁租约已过期（device=$holderDevice），尝试抢占")
                client.delete(path)
                code = client.put(path, body, ifNoneMatch = true)
                if (code == 412) {
                    throw WebDavClient.WebDavException("同步锁被占用，请稍后重试", 412)
                }
            } else {
                throw WebDavClient.WebDavException("同步锁被占用，请稍后重试", 412)
            }
        }

        val holder = parseLock(client.get(path))
        if (holder?.first != token) {
            throw WebDavClient.WebDavException("同步锁获取失败：当前设备未持有锁，请重试")
        }
        return token
    }

    /**
     * 续租：先回读并校验 token 仍属于自己，再覆盖写回刷新 time。
     * 若锁已被其他设备按租约抢占，立即中止同步，绝不覆盖别人的锁。
     */
    private fun renewLock(client: WebDavClient, token: String) {
        val holder = parseLock(client.get("$ROOT/$LOCK"))
        if (holder?.first != token) {
            throw WebDavClient.WebDavException("同步锁已失效（可能被其他设备占用），请重试")
        }
        client.put("$ROOT/$LOCK", lockBody(token, System.currentTimeMillis()))
    }

    /** 释放：先回读锁，只有 token 仍属于自己时才删除，绝不无条件删别人的锁。 */
    private fun releaseLock(client: WebDavClient, token: String) {
        runCatching {
            val holder = parseLock(client.get("$ROOT/$LOCK")) ?: return@runCatching
            if (holder.first == token) {
                client.delete("$ROOT/$LOCK")
            } else {
                Log.w(TAG, "同步锁已不属于当前设备，跳过释放")
            }
        }.onFailure {
            // 网络异常时不盲目删除，锁会按租约自然过期。
            Log.w(TAG, "释放同步锁失败（锁将按租约过期）", it)
        }
    }

    // ---------- 快照：保留上一版 + 原子替换 ----------

    /** 备份当前远端快照到 data.json.bak；best-effort，失败不阻塞同步。 */
    private fun backupSnapshot(client: WebDavClient) {
        runCatching {
            val previous = client.get("$ROOT/$DATA") ?: return@runCatching
            client.put("$ROOT/$DATA_BAK", previous)
        }.onFailure {
            Log.w(TAG, "备份上一版快照失败（继续同步）", it)
        }
    }

    /**
     * 原子写：先 PUT 到 data.json.tmp，再 MOVE 覆盖 data.json。
     * 服务器不支持 MOVE（405/501）时回退为直接 PUT，并记录警告。
     */
    private fun writeSnapshotAtomic(client: WebDavClient, data: ByteArray) {
        val tmpPath = "$ROOT/$DATA_TMP"
        val targetPath = "$ROOT/$DATA"
        try {
            client.put(tmpPath, data)
            try {
                client.move(tmpPath, targetPath, overwrite = true)
                return
            } catch (e: WebDavClient.WebDavException) {
                if (e.statusCode != 405 && e.statusCode != 501) throw e
                Log.w(TAG, "服务器不支持 MOVE（HTTP ${e.statusCode}），回退为直接 PUT 快照")
            }
        } catch (e: Throwable) {
            runCatching { client.delete(tmpPath) }
            throw e
        }
        // 回退路径：先清理 tmp，再直接覆盖写（非原子，仅用于兼容老旧服务器）。
        runCatching { client.delete(tmpPath) }
            .onFailure { Log.w(TAG, "清理临时快照失败", it) }
        client.put(targetPath, data)
    }

    // ---------- 照片 ----------

    /**
     * 照片增量：以远端 PROPFIND 实际文件列表 + 本地文件是否存在为准。
     * 任一张失败都计数并在返回前不抛出；调用方必须在写快照前中止同步。
     */
    private fun syncPhotos(client: WebDavClient, lockToken: String, photosDir: File): Int {
        val remoteNames = client.propfind("$ROOT/$PHOTOS")
            .filter { it.isNotBlank() && !it.endsWith("/") && !it.endsWith(".tmp") }
            .toSet()
        val localNames = photosDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        // PROPFIND 说远端有、本地文件不存在 → 下载（而不是依赖 DB 里的文件名集合）
        val toDownload = remoteNames.filter { !File(photosDir, it).exists() }
        // 本地实际存在、远端列表没有 → 上传
        val toUpload = localNames.filter { it !in remoteNames }

        var failed = 0
        var processed = 0

        for (name in toDownload) {
            if (!downloadPhoto(client, name, photosDir)) failed++
            processed++
            if (processed % PHOTO_RENEW_INTERVAL == 0) renewLock(client, lockToken)
        }
        for (name in toUpload) {
            if (!uploadPhoto(client, name, photosDir)) failed++
            processed++
            if (processed % PHOTO_RENEW_INTERVAL == 0) renewLock(client, lockToken)
        }
        return failed
    }

    /** 下载单张照片：404 视为失败；写 .tmp 校验非空后 renameTo 原子落盘。 */
    private fun downloadPhoto(client: WebDavClient, name: String, photosDir: File): Boolean {
        val tmp = File(photosDir, "$name.tmp")
        return try {
            val data = client.get("$ROOT/$PHOTOS/$name")
            if (data == null) {
                Log.w(TAG, "照片下载失败（远端 404）：$name")
                false
            } else {
                tmp.writeBytes(data)
                if (tmp.length() <= 0L) {
                    Log.w(TAG, "照片下载失败（空文件）：$name")
                    tmp.delete()
                    false
                } else {
                    val target = File(photosDir, name)
                    if (tmp.renameTo(target)) {
                        true
                    } else {
                        Log.w(TAG, "照片下载失败（无法落盘）：$name")
                        tmp.delete()
                        false
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "照片下载失败：$name", e)
            tmp.delete()
            false
        }
    }

    /** 上传单张照片：文件必须真实存在；PUT 失败计入失败数。 */
    private fun uploadPhoto(client: WebDavClient, name: String, photosDir: File): Boolean {
        val file = File(photosDir, name)
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "照片上传跳过（本地不存在）：$name")
            return false
        }
        return try {
            client.put("$ROOT/$PHOTOS/$name", file.readBytes())
            true
        } catch (e: IOException) {
            Log.w(TAG, "照片上传失败：$name", e)
            false
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
}
