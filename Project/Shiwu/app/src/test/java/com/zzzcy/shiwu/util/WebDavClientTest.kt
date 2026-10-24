package com.zzzcy.shiwu.util

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDavClient 的纯 JVM 测试（不依赖 Android 类）。
 * 用 ServerSocket 起一个只用一次的假 WebDAV 服务器，逐项校验请求/响应行为。
 */
class WebDavClientTest {

    private data class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private data class Response(
        val status: Int,
        val body: ByteArray = ByteArray(0),
        val headers: Map<String, String> = emptyMap(),
        /** 声明一个比实际 body 更大的 Content-Length，用于模拟截断响应 */
        val declaredLength: Int? = null
    )

    private inner class FakeServer(private val handler: (Request) -> Response) : Closeable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val failure = AtomicReference<Throwable?>()
        private val future = executor.submit {
            try {
                while (!server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (e: SocketException) {
                        break
                    }
                    socket.use {
                        val request = readRequest(it.getInputStream())
                        val response = handler(request)
                        writeResponse(it.getOutputStream(), response)
                    }
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }

        val port: Int get() = server.localPort
        val baseUrl: String get() = "http://127.0.0.1:$port/dav"

        override fun close() {
            server.close()
            future.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            failure.get()?.let { throw AssertionError("假服务器处理失败", it) }
        }
    }

    @Test
    fun `GET 200 返回 body 并携带 Basic Auth`() {
        val body = "hello-snapshot".toByteArray(Charsets.UTF_8)
        FakeServer { req ->
            assertEquals("GET", req.method)
            assertEquals("/dav/shiwu/data.json", req.path)
            val expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".toByteArray(Charsets.UTF_8))
            assertEquals(expected, req.headers["authorization"])
            Response(200, body)
        }.use { server ->
            val client = WebDavClient(server.baseUrl, "user", "pass")
            assertArrayEquals(body, client.get("shiwu/data.json"))
        }
    }

    @Test
    fun `GET 404 返回 null`() {
        FakeServer { Response(404) }.use { server ->
            assertNull(WebDavClient(server.baseUrl, "u", "p").get("shiwu/data.json"))
        }
    }

    @Test
    fun `GET 5xx 异常包含方法路径和状态码`() {
        FakeServer { Response(503) }.use { server ->
            val e = assertThrows(WebDavClient.WebDavException::class.java) {
                WebDavClient(server.baseUrl, "u", "p").get("shiwu/data.json")
            }
            assertEquals(503, e.statusCode)
            assertTrue(e.message!!.contains("GET"))
            assertTrue(e.message!!.contains("shiwu/data.json"))
            assertTrue(e.message!!.contains("503"))
        }
    }

    @Test
    fun `PUT 条件创建返回 201 且发送 If-None-Match`() {
        val payload = "lock-body".toByteArray(Charsets.UTF_8)
        FakeServer { req ->
            assertEquals("PUT", req.method)
            assertEquals("/dav/shiwu/sync.lock", req.path)
            assertEquals("*", req.headers["if-none-match"])
            assertArrayEquals(payload, req.body)
            Response(201)
        }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertEquals(201, client.put("shiwu/sync.lock", payload, ifNoneMatch = true))
        }
    }

    @Test
    fun `PUT 条件创建收到 412 时作为合法冲突返回`() {
        FakeServer { Response(412) }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertEquals(412, client.put("shiwu/sync.lock", ByteArray(0), ifNoneMatch = true))
        }
    }

    @Test
    fun `PUT 非条件请求收到 412 时抛异常`() {
        FakeServer { Response(412) }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            val e = assertThrows(WebDavClient.WebDavException::class.java) {
                client.put("shiwu/sync.lock", ByteArray(0))
            }
            assertEquals(412, e.statusCode)
            assertTrue(e.message!!.contains("PUT"))
            assertTrue(e.message!!.contains("shiwu/sync.lock"))
            assertTrue(e.message!!.contains("412"))
        }
    }

    @Test
    fun `MOVE 使用绝对 Destination 和 Overwrite T`() {
        lateinit var server: FakeServer
        server = FakeServer { req ->
            assertEquals("MOVE", req.method)
            assertEquals("/dav/shiwu/data.json.tmp", req.path)
            assertEquals(
                "http://127.0.0.1:${server.port}/dav/shiwu/data.json",
                req.headers["destination"]
            )
            assertEquals("T", req.headers["overwrite"])
            Response(201)
        }
        server.use {
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertEquals(201, client.move("shiwu/data.json.tmp", "shiwu/data.json"))
        }
    }

    @Test
    fun `MOVE 失败抛出带状态码的异常`() {
        FakeServer { Response(405) }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            val e = assertThrows(WebDavClient.WebDavException::class.java) {
                client.move("shiwu/data.json.tmp", "shiwu/data.json")
            }
            assertEquals(405, e.statusCode)
            assertTrue(e.message!!.contains("MOVE"))
            assertTrue(e.message!!.contains("data.json.tmp"))
        }
    }

    @Test
    fun `响应体短读抛 EOFException`() {
        FakeServer {
            Response(200, "short".toByteArray(Charsets.UTF_8), declaredLength = 100)
        }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertThrows(EOFException::class.java) { client.get("shiwu/data.json") }
        }
    }

    @Test
    fun `chunked 响应正确拼接`() {
        val encoded = "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n".toByteArray(Charsets.US_ASCII)
        FakeServer {
            Response(200, encoded, headers = mapOf("Transfer-Encoding" to "chunked"))
        }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertEquals("hello world", String(client.get("shiwu/data.json")!!, Charsets.UTF_8))
        }
    }

    @Test
    fun `非法 chunk 长度抛 IOException`() {
        val encoded = "zz\r\nhello\r\n0\r\n\r\n".toByteArray(Charsets.US_ASCII)
        FakeServer {
            Response(200, encoded, headers = mapOf("Transfer-Encoding" to "chunked"))
        }.use { server ->
            val client = WebDavClient(server.baseUrl, "u", "p")
            assertThrows(java.io.IOException::class.java) { client.get("shiwu/data.json") }
        }
    }

    // ---------- 假服务器读写 ----------

    private fun readRequest(input: InputStream): Request {
        val requestLine = readLine(input) ?: throw IllegalStateException("连接未发送请求")
        val parts = requestLine.split(" ")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        return Request(
            method = parts.getOrElse(0) { "" },
            path = parts.getOrElse(1) { "/" },
            headers = headers,
            body = readFully(input, length)
        )
    }

    private fun writeResponse(output: OutputStream, response: Response) {
        val head = buildString {
            append("HTTP/1.1 ${response.status} TEST\r\n")
            append("Content-Length: ${response.declaredLength ?: response.body.size}\r\n")
            response.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(response.body)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var any = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (any) String(out.toByteArray(), Charsets.UTF_8) else null
            }
            any = true
            if (b == '\n'.code) break
            out.write(b)
        }
        val bytes = out.toByteArray()
        val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        return String(bytes, 0, end, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n < 0) break
            read += n
        }
        return buf.copyOf(read)
    }
}
