package com.zzzcy.shiwu.data

import android.content.Context
import android.util.Log
import com.zzzcy.shiwu.util.Crypto

/** WebDAV 同步配置存取：地址 / 账号明文，密码 Keystore 加密；记录上次同步时间 */
object SyncConfigStore {
    private const val TAG = "ShiwuSync"
    private const val KEY_SERVER = "webdav_server"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASS = "webdav_pass"
    private const val KEY_LAST_SYNC = "webdav_last_sync"

    /**
     * 读取结果：config 为 null 表示尚未配置。
     * decryptFailed=true 表示存过密码但解密失败（config.password 为空），UI 可提示重新输入。
     */
    data class LoadResult(
        val config: WebDavSyncManager.Config?,
        val decryptFailed: Boolean = false
    )

    @Volatile
    private var loadFailed = false

    /** 最近一次 load(context) 是否因解密失败返回了空密码。 */
    fun lastLoadFailed(): Boolean = loadFailed

    /** 保持原有调用方式；解密失败时返回密码为空的配置。 */
    fun load(context: Context): WebDavSyncManager.Config? = loadResult(context).config

    fun loadResult(context: Context): LoadResult {
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        val server = p.getString(KEY_SERVER, null)
        if (server == null) {
            loadFailed = false
            return LoadResult(null, false)
        }
        val user = p.getString(KEY_USER, "") ?: ""
        val stored = p.getString(KEY_PASS, null)
        val decrypted = stored?.let { Crypto.decrypt(context, it) }
        val decryptFailed = stored != null && decrypted == null
        if (decryptFailed) {
            Log.w(TAG, "WebDAV 密码解密失败，请重新输入密码")
        }
        loadFailed = decryptFailed
        return LoadResult(
            config = WebDavSyncManager.Config(server, user, decrypted ?: ""),
            decryptFailed = decryptFailed
        )
    }

    /**
     * 保存配置。加密失败时不写任何字段（尤其不能用空串覆盖旧密码）；
     * 只有加密成功且 prefs 落盘成功才返回 true。
     */
    fun save(context: Context, config: WebDavSyncManager.Config): Boolean {
        val encrypted = Crypto.encrypt(context, config.password) ?: return false
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        return p.edit()
            .putString(KEY_SERVER, config.server.trim())
            .putString(KEY_USER, config.username.trim())
            .putString(KEY_PASS, encrypted)
            .commit()
    }

    fun clear(context: Context) {
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        p.edit()
            .remove(KEY_SERVER)
            .remove(KEY_USER)
            .remove(KEY_PASS)
            .remove(KEY_LAST_SYNC)
            .apply()
        loadFailed = false
    }

    fun lastSyncAt(context: Context): Long =
        context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun markSynced(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, at).apply()
    }
}
