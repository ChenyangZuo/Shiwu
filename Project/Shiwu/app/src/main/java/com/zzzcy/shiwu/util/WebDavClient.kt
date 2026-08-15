package com.zzzcy.shiwu.util

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 极简 WebDAV 客户端（HttpURLConnection 实现，无第三方依赖） */
class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val root: String = baseUrl.trimEnd('/')

    class WebDavException(message: String, val statusCode: Int = -1) : IOException(message)

    private fun open(
        path: String,
        method: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000
    ): HttpURLConnection {
        val url = URL(root.trimEnd('/') + "/" + path.trimStart('/'))
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = timeoutMs
        conn.setRequestProperty(
            "Authorization",
            "Basic " + Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        )
        conn.setRequestProperty("User-Agent", "Shiwu-WebDAV/1.0")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun fail(conn: HttpURLConnection, detail: String): Nothing {
        val msg = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (_: Exception) {
            ""
        }
        throw WebDavException("$detail（HTTP ${conn.responseCode}）${if (msg.isNotBlank()) ": $msg" else ""}", conn.responseCode)
    }

    /** GET 文件内容；不存在返回 null */
    fun get(path: String): ByteArray? {
        val conn = open(path, "GET")
        return try {
            when (conn.responseCode) {
                200 -> conn.inputStream.use { it.readBytes() }
                404 -> null
                else -> fail(conn, "读取失败")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** PUT 文件；返回 HTTP 状态码（412 表示 If-None-Match 冲突） */
    fun put(path: String, data: ByteArray, ifNoneMatch: Boolean = false): Int {
        val headers = if (ifNoneMatch) mapOf("If-None-Match" to "*") else emptyMap()
        val conn = open(path, "PUT", data, headers)
        return try {
            val code = conn.responseCode
            if (code !in 200..299 && code != 412) fail(conn, "上传失败")
            code
        } finally {
            conn.disconnect()
        }
    }

    /** DELETE 文件；404 视为成功（不存在） */
    fun delete(path: String) {
        val conn = open(path, "DELETE")
        try {
            if (conn.responseCode !in 200..299 && conn.responseCode != 404) fail(conn, "删除失败")
        } finally {
            conn.disconnect()
        }
    }

    /** MKCOL 建目录；已存在（405/301）视为成功 */
    fun mkcol(path: String) {
        val conn = open(path, "MKCOL")
        try {
            if (conn.responseCode !in 200..299 && conn.responseCode !in listOf(301, 405)) fail(conn, "创建目录失败")
        } finally {
            conn.disconnect()
        }
    }

    /** PROPFIND 列目录：返回子项相对名 */
    fun propfind(path: String): List<String> {
        val conn = open(path, "PROPFIND", headers = mapOf("Depth" to "1", "Content-Type" to "application/xml"))
        return try {
            if (conn.responseCode !in 200..299) fail(conn, "列目录失败")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            parseHrefs(body, path)
        } finally {
            conn.disconnect()
        }
    }

    /** 解析 PROPFIND multistatus 的 href，返回子项文件名（排除自身与目录） */
    private fun parseHrefs(xml: String, basePath: String): List<String> {
        val base = basePath.trim('/')
        val names = mutableSetOf<String>()
        val hrefRegex = Regex("<[^:>]*:?href>([^<]+)</[^:>]*:?href>")
        hrefRegex.findAll(xml).forEach { m ->
            val raw = m.groupValues[1].trim()
            val path = raw
                .replace(Regex("^https?://[^/]+"), "")
                .trimStart('/')
                .removeSuffix("/")
            if (path != base && path.startsWith("$base/")) {
                names += path.removePrefix("$base/")
            }
        }
        return names.toList()
    }
}
