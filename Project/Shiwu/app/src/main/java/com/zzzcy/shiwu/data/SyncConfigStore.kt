package com.zzzcy.shiwu.data

import android.content.Context
import com.zzzcy.shiwu.util.Crypto

/** WebDAV 同步配置存取：地址 / 账号明文，密码 Keystore 加密；记录上次同步时间 */
object SyncConfigStore {
    private const val KEY_SERVER = "webdav_server"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASS = "webdav_pass"
    private const val KEY_LAST_SYNC = "webdav_last_sync"

    fun load(context: Context): WebDavSyncManager.Config? {
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        val server = p.getString(KEY_SERVER, null) ?: return null
        val user = p.getString(KEY_USER, "") ?: ""
        val pass = p.getString(KEY_PASS, null)?.let { Crypto.decrypt(context, it) } ?: ""
        return WebDavSyncManager.Config(server, user, pass)
    }

    fun save(context: Context, config: WebDavSyncManager.Config) {
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        p.edit()
            .putString(KEY_SERVER, config.server.trim())
            .putString(KEY_USER, config.username.trim())
            .putString(KEY_PASS, Crypto.encrypt(context, config.password) ?: "")
            .apply()
    }

    fun clear(context: Context) {
        val p = context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
        p.edit()
            .remove(KEY_SERVER)
            .remove(KEY_USER)
            .remove(KEY_PASS)
            .apply()
    }

    fun lastSyncAt(context: Context): Long =
        context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun markSynced(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("shiwu_prefs", Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, at).apply()
    }
}
