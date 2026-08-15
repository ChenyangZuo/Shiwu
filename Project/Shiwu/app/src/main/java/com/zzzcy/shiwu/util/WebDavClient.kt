package com.zzzcy.shiwu.util

import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocketFactory

/**
 * 极简 WebDAV 客户端：基于原生 Socket 实现（不依赖 HttpURLConnection，
 * 因为 Android 的 HttpURLConnection 不允许 MKCOL / PROPFIND 等 WebDAV 方法）。
 */
class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val root = baseUrl.trimEnd('/')

    class WebDavException(message: String, val statusCode: Int = -1) : IOException(message)

    private data class RawResponse(val statusCode: Int, val body: ByteArray)

    private fun request(
        path: String,
        method: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap()
    ): RawResponse {
        val url = URL(root.trimEnd('/') + "/" + path.trimStart('/'))
        val secure = url.protocol == "https"
        val port = if (url.port != -1) url.port else if (secure) 443 else 80
        val socket = if (secure) {
            SSLSocketFactory.getDefault().createSocket(url.host, port)
        } else {
            Socket(url.host, port)
        }
        try {
            socket.soTimeout = 20_000
            val out = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val requestPath = url.path + (if (url.query != null) "?${url.query}" else "")
            out.write("$method $requestPath HTTP/1.1\r\n")
            out.write("Host: ${url.host}${if (url.port != -1) ":${url.port}" else ""}\r\n")
            out.write(
                "Authorization: Basic " +
                    Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP) +
                    "\r\n"
            )
            out.write("User-Agent: Shiwu-WebDAV/1.0\r\n")
            headers.forEach { (k, v) -> out.write("$k: $v\r\n") }
            if (body != null) {
                out.write("Content-Length: ${body.size}\r\n")
            }
            out.write("Connection: close\r\n\r\n")
            out.flush()
            if (body != null) {
                socket.getOutputStream().write(body)
                socket.getOutputStream().flush()
            }

            val input = BufferedInputStream(socket.getInputStream())
            val statusLine = readLine(input)
            val statusMatch = Regex("^HTTP/\\d\\.\\d\\s+(\\d+)").find(statusLine)
            val code = statusMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
            var contentLength = -1
            var chunked = false
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val lower = line.lowercase()
                when {
                    lower.startsWith("content-length:") -> contentLength = line.substringAfter(':').trim().toIntOrNull() ?: -1
                    lower.startsWith("transfer-encoding:") && lower.contains("chunked") -> chunked = true
                }
            }
            val data = when {
                contentLength >= 0 -> readExact(input, contentLength)
                chunked -> readChunked(input)
                else -> input.readBytes()
            }
            return RawResponse(code, data)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (prev == '\r'.code && b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') sb.setLength(sb.length - 1)
                break
            }
            sb.append(b.toChar())
            prev = b
        }
        return sb.toString()
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n == -1) break
            read += n
        }
        return buf.copyOf(read)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input).trim()
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: 0
            if (size == 0) {
                // 结尾 CRLF + 可选 trailer
                readLine(input)
                while (true) {
                    val t = readLine(input)
                    if (t.isEmpty()) break
                }
                break
            }
            out.write(readExact(input, size))
            readLine(input) // chunk 结尾 CRLF
        }
        return out.toByteArray()
    }

    // ---------- 对外 API ----------

    /** GET 文件内容；不存在返回 null */
    fun get(path: String): ByteArray? {
        val r = request(path, "GET")
        return when (r.statusCode) {
            200 -> r.body
            404 -> null
            else -> throw WebDavException("读取失败", r.statusCode)
        }
    }

    /** PUT 文件；返回 HTTP 状态码（412 表示 If-None-Match 冲突） */
    fun put(path: String, data: ByteArray, ifNoneMatch: Boolean = false): Int {
        val headers = if (ifNoneMatch) mapOf("If-None-Match" to "*") else emptyMap()
        val r = request(path, "PUT", data, headers)
        if (r.statusCode !in 200..299 && r.statusCode != 412) {
            throw WebDavException("上传失败", r.statusCode)
        }
        return r.statusCode
    }

    /** DELETE 文件；404 视为成功（不存在） */
    fun delete(path: String) {
        val r = request(path, "DELETE")
        if (r.statusCode !in 200..299 && r.statusCode != 404) {
            throw WebDavException("删除失败", r.statusCode)
        }
    }

    /** MKCOL 建目录；已存在（301/405）视为成功 */
    fun mkcol(path: String) {
        val r = request(path, "MKCOL")
        if (r.statusCode !in 200..299 && r.statusCode !in listOf(301, 405)) {
            throw WebDavException("创建目录失败", r.statusCode)
        }
    }

    /** PROPFIND 列目录：返回子项相对名 */
    fun propfind(path: String): List<String> {
        val r = request(path, "PROPFIND", headers = mapOf("Depth" to "1", "Content-Type" to "application/xml"))
        if (r.statusCode !in 200..299) throw WebDavException("列目录失败", r.statusCode)
        return parseHrefs(String(r.body, Charsets.UTF_8), path)
    }

    /** 解析 PROPFIND multistatus 的 href，返回子项文件名（排除自身） */
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
