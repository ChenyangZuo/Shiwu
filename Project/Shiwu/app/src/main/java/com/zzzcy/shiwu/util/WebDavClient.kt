package com.zzzcy.shiwu.util

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Base64
import javax.net.ssl.SSLSocket
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
        val rawSocket = Socket()
        try {
            // 必须带连接超时，否则不可达的服务器会一直阻塞同步流程。
            rawSocket.connect(InetSocketAddress(url.host, port), CONNECT_TIMEOUT_MS)
            rawSocket.soTimeout = READ_TIMEOUT_MS
            val socket: Socket = if (secure) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(rawSocket, url.host, port, true) as SSLSocket
                // 自签证书会在这里因主机名校验/信任链失败而抛错，这是预期行为。
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS"
                ssl.sslParameters = params
                ssl.startHandshake()
                ssl
            } else {
                rawSocket
            }
            socket.soTimeout = READ_TIMEOUT_MS

            val output = socket.getOutputStream()
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            val requestPath = url.path + (if (url.query != null) "?${url.query}" else "")
            writer.write("$method $requestPath HTTP/1.1\r\n")
            writer.write("Host: ${url.host}${if (url.port != -1) ":${url.port}" else ""}\r\n")
            writer.write(
                "Authorization: Basic " +
                    Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8)) +
                    "\r\n"
            )
            writer.write("User-Agent: Shiwu-WebDAV/1.0\r\n")
            headers.forEach { (k, v) -> writer.write("$k: $v\r\n") }
            if (body != null) {
                writer.write("Content-Length: ${body.size}\r\n")
            } else {
                writer.write("Content-Length: 0\r\n")
            }
            writer.write("Connection: close\r\n\r\n")
            writer.flush()
            if (body != null) {
                output.write(body)
                output.flush()
            }

            val input = BufferedInputStream(socket.getInputStream())
            val statusLine = readLine(input)
            if (statusLine.isEmpty()) {
                throw IOException("$method $path：服务器未返回有效响应")
            }
            val statusMatch = STATUS_LINE.find(statusLine)
                ?: throw IOException("$method $path：无法解析响应状态行")
            val code = statusMatch.groupValues[1].toInt()
            var contentLength = -1
            var chunked = false
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                when {
                    name == "content-length" ->
                        contentLength = value.toIntOrNull()
                            ?: throw IOException("$method $path：非法的 Content-Length（$value）")
                    name == "transfer-encoding" && value.lowercase().contains("chunked") -> chunked = true
                }
            }
            val data = when {
                // Transfer-Encoding 优先于 Content-Length
                chunked -> readChunked(input)
                contentLength >= 0 -> readExact(input, contentLength)
                else -> input.readBytes()
            }
            return RawResponse(code, data)
        } finally {
            runCatching { rawSocket.close() }
        }
    }

    private fun readLine(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (b == '\n'.code) break
            out.write(b)
        }
        val bytes = out.toByteArray()
        val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        return String(bytes, 0, end, Charsets.UTF_8)
    }

    /** 读满 length 字节；EOF 提前出现说明响应被截断，必须报错而不能当成功。 */
    private fun readExact(input: InputStream, length: Int): ByteArray {
        if (length < 0) throw IOException("非法的响应长度：$length")
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n == -1) {
                throw EOFException("响应体被截断：期望 $length 字节，实际 $read 字节")
            }
            read += n
        }
        return buf
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input).trim()
            if (sizeLine.isEmpty()) throw IOException("分块编码非法：缺少 chunk 长度")
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16)
                ?: throw IOException("分块编码非法：$sizeLine")
            if (size < 0) throw IOException("分块编码非法：$sizeLine")
            if (size == 0) {
                // 结尾 CRLF + 可选 trailer
                while (true) {
                    if (readLine(input).isEmpty()) break
                }
                break
            }
            out.write(readExact(input, size))
            if (readLine(input).isNotEmpty()) {
                throw IOException("分块编码非法：chunk 未以 CRLF 结束")
            }
        }
        return out.toByteArray()
    }

    private fun failureMessage(method: String, path: String, code: Int): String =
        "$method $path 失败：HTTP $code"

    // ---------- 对外 API ----------

    /** GET 文件内容；不存在返回 null */
    fun get(path: String): ByteArray? {
        val r = request(path, "GET")
        return when (r.statusCode) {
            200 -> r.body
            404 -> null
            else -> throw WebDavException(failureMessage("GET", path, r.statusCode), r.statusCode)
        }
    }

    /**
     * PUT 文件；返回 HTTP 状态码。
     * 仅当 ifNoneMatch=true 时 412（目标已存在）才是合法结果，其余 412 均视为错误。
     */
    fun put(path: String, data: ByteArray, ifNoneMatch: Boolean = false): Int {
        val headers = if (ifNoneMatch) mapOf("If-None-Match" to "*") else emptyMap()
        val r = request(path, "PUT", data, headers)
        if (r.statusCode in 200..299) return r.statusCode
        if (ifNoneMatch && r.statusCode == 412) return r.statusCode
        throw WebDavException(failureMessage("PUT", path, r.statusCode), r.statusCode)
    }

    /** DELETE 文件；404 视为成功（不存在） */
    fun delete(path: String) {
        val r = request(path, "DELETE")
        if (r.statusCode !in 200..299 && r.statusCode != 404) {
            throw WebDavException(failureMessage("DELETE", path, r.statusCode), r.statusCode)
        }
    }

    /** MKCOL 建目录；已存在（301/405）视为成功 */
    fun mkcol(path: String) {
        val r = request(path, "MKCOL")
        if (r.statusCode !in 200..299 && r.statusCode !in listOf(301, 405)) {
            throw WebDavException(failureMessage("MKCOL", path, r.statusCode), r.statusCode)
        }
    }

    /**
     * MOVE 重命名/移动（WebDAV 原子替换的关键）。
     * Destination 必须是完整绝对 URL；overwrite=true 时发送 Overwrite: T。
     */
    fun move(src: String, dst: String, overwrite: Boolean = true): Int {
        val destination = root.trimEnd('/') + "/" + dst.trimStart('/')
        val headers = mapOf(
            "Destination" to destination,
            "Overwrite" to if (overwrite) "T" else "F"
        )
        val r = request(src, "MOVE", headers = headers)
        if (r.statusCode !in 200..299) {
            throw WebDavException(failureMessage("MOVE", src, r.statusCode), r.statusCode)
        }
        return r.statusCode
    }

    /** PROPFIND 列目录：返回子项相对名 */
    fun propfind(path: String): List<String> {
        val r = request(path, "PROPFIND", headers = mapOf("Depth" to "1", "Content-Type" to "application/xml"))
        if (r.statusCode !in 200..299) {
            throw WebDavException(failureMessage("PROPFIND", path, r.statusCode), r.statusCode)
        }
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

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private val STATUS_LINE = Regex("^HTTP/\\d\\.\\d\\s+(\\d{3})")
    }
}
