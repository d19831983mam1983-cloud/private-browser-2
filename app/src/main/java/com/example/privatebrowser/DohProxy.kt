package com.example.privatebrowser

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// Локальный прокси: WebView отправляет все соединения сюда,
// а имена сайтов превращаются в IP через зашифрованный DNS (DoH).
class DohProxy {

    // 0 = AdGuard, 1 = Comss.one
    @Volatile
    var provider: Int = 0

    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private class CacheEntry(val addrs: List<InetAddress>, val expires: Long)

    private val http = OkHttpClient()

    private fun ips(vararg list: String): List<InetAddress> {
        val result = ArrayList<InetAddress>()
        for (s in list) result.add(InetAddress.getByName(s))
        return result
    }

    private val adguard: DnsOverHttps = DnsOverHttps.Builder()
        .client(http)
        .url("https://dns.adguard-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(ips("94.140.14.14", "94.140.15.15"))
        .includeIPv6(false)
        .build()

    private val comss: DnsOverHttps = DnsOverHttps.Builder()
        .client(http)
        .url("https://dns.comss.one/dns-query".toHttpUrl())
        .bootstrapDnsHosts(ips("83.220.169.155", "212.109.195.93", "195.133.25.16"))
        .includeIPv6(false)
        .build()

    fun start(): Int {
        val existing = server
        if (existing != null) return existing.localPort
        val ss = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        server = ss
        pool.execute {
            while (!ss.isClosed) {
                try {
                    val c = ss.accept()
                    pool.execute { handle(c) }
                } catch (e: Exception) {
                    break
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
        pool.shutdownNow()
    }

    fun clearCache() {
        cache.clear()
    }

    private fun isIp(h: String): Boolean {
        if (h.isEmpty()) return false
        if (h.contains(":")) return true
        for (ch in h) if (!(ch.isDigit() || ch == '.')) return false
        return true
    }

    private fun resolve(host: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val key = provider.toString() + ":" + host
        val c = cache[key]
        if (c != null && c.expires > now) return c.addrs

        val order = if (provider == 0) listOf(adguard, comss) else listOf(comss, adguard)
        var failure: Exception? = null
        for (d in order) {
            try {
                val r = d.lookup(host)
                if (r.isNotEmpty()) {
                    cache[key] = CacheEntry(r, now + 120000L)
                    return r
                }
            } catch (e: UnknownHostException) {
                val m1 = e.message ?: ""
                val m2 = e.cause?.message ?: ""
                // Домен не существует или заблокирован DNS-фильтром: второй сервер не спрашиваем
                if (e.cause is UnknownHostException || m1.contains("NXDOMAIN") || m2.contains("NXDOMAIN")) {
                    throw e
                }
                failure = e
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: UnknownHostException(host)
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            val n = sb.length
            if (n >= 4 && sb[n - 4] == '\r' && sb[n - 3] == '\n' &&
                sb[n - 2] == '\r' && sb[n - 1] == '\n'
            ) {
                return sb.toString()
            }
        }
        return null
    }

    private fun reply(out: OutputStream, line: String) {
        out.write((line + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
        out.flush()
    }

    private fun closeQuiet(s: Socket?) {
        try {
            s?.close()
        } catch (e: Exception) {
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16384)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (e: Exception) {
        }
    }

    private fun handle(client: Socket) {
        var remote: Socket? = null
        try {
            client.tcpNoDelay = true
            val head = readHead(client.getInputStream())
            if (head == null) return
            val out = client.getOutputStream()
            val first = head.substringBefore("\r\n")
            val parts = first.split(" ")
            if (parts.size < 2 || parts[0] != "CONNECT") {
                reply(out, "HTTP/1.1 405 Method Not Allowed")
                return
            }
            val target = parts[1]
            val idx = target.lastIndexOf(':')
            if (idx < 0) {
                reply(out, "HTTP/1.1 400 Bad Request")
                return
            }
            val host = target.substring(0, idx).trim('[', ']')
            val port = target.substring(idx + 1).toInt()

            val addrs: List<InetAddress>
            try {
                if (isIp(host)) addrs = listOf(InetAddress.getByName(host)) else addrs = resolve(host)
            } catch (e: Exception) {
                reply(out, "HTTP/1.1 502 Bad Gateway")
                return
            }

            val good = ArrayList<InetAddress>()
            for (a in addrs) if (!a.isAnyLocalAddress && !a.isLoopbackAddress) good.add(a)
            if (good.isEmpty()) {
                // DNS-фильтр вернул 0.0.0.0: сайт или трекер заблокирован
                reply(out, "HTTP/1.1 403 Forbidden")
                return
            }

            for (a in good) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(a, port), 10000)
                    remote = s
                    break
                } catch (e: Exception) {
                }
            }
            val r = remote
            if (r == null) {
                reply(out, "HTTP/1.1 502 Bad Gateway")
                return
            }
            r.tcpNoDelay = true

            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            out.flush()

            pool.execute {
                copy(r.getInputStream(), client.getOutputStream())
                closeQuiet(r)
                closeQuiet(client)
            }
            copy(client.getInputStream(), r.getOutputStream())
            closeQuiet(r)
        } catch (e: Exception) {
        } finally {
            closeQuiet(client)
            closeQuiet(remote)
        }
    }
}
