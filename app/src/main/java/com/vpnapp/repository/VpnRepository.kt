package com.vpnapp.repository

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.vpnapp.model.SubscriptionConfig
import com.vpnapp.model.VpnServer
import com.vpnapp.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class VpnRepository(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchServers(url: String): Result<List<VpnServer>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val servers = parseSubscription(body)

            if (servers.isEmpty()) {
                return@withContext Result.failure(Exception("Не удалось распознать формат подписки"))
            }

            prefs.saveServers(servers)
            Result.success(servers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSubscription(body: String): List<VpnServer> {
        val trimmed = body.trim()

        // 1. Попробовать JSON формат (наш формат)
        try {
            val config = gson.fromJson(trimmed, SubscriptionConfig::class.java)
            if (config?.servers?.isNotEmpty() == true) return config.servers
        } catch (_: Exception) {}

        // 2. Попробовать JSON массив серверов
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<VpnServer>>() {}.type
            val list: List<VpnServer> = gson.fromJson(trimmed, type)
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {}

        // 3. Попробовать base64 (формат v2ray/quattro-cloud и подобных)
        val decoded = tryBase64Decode(trimmed)
        if (decoded != null) {
            return parseProxyLines(decoded)
        }

        // 4. Попробовать как обычный текст со строками прокси
        if (trimmed.contains("vless://") || trimmed.contains("vmess://") ||
            trimmed.contains("hysteria2://") || trimmed.contains("ss://") ||
            trimmed.contains("trojan://")
        ) {
            return parseProxyLines(trimmed)
        }

        return emptyList()
    }

    private fun tryBase64Decode(input: String): String? {
        return try {
            val cleaned = input.replace("\n", "").replace("\r", "").trim()
            val decoded = Base64.decode(cleaned, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProxyLines(text: String): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        lines.forEachIndexed { index, line ->
            val server = when {
                line.startsWith("vless://")     -> parseVless(line, index)
                line.startsWith("vmess://")     -> parseVmess(line, index)
                line.startsWith("hysteria2://") -> parseHysteria2(line, index)
                line.startsWith("hy2://")       -> parseHysteria2(line, index)
                line.startsWith("ss://")        -> parseShadowsocks(line, index)
                line.startsWith("trojan://")    -> parseTrojan(line, index)
                else -> null
            }
            server?.let { servers.add(it) }
        }

        return servers
    }

    // ── VLESS ──────────────────────────────────────────────────────────────────
    private fun parseVless(line: String, index: Int): VpnServer? {
        return try {
            // vless://uuid@host:port?params#name
            val withoutScheme = line.removePrefix("vless://")
            val hashIdx = withoutScheme.lastIndexOf('#')
            val rawName = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else "VLESS $index"
            val name = urlDecode(rawName).ifBlank { "VLESS $index" }
            val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme

            val qIdx = main.indexOf('?')
            val params = if (qIdx >= 0) parseQueryParams(main.substring(qIdx + 1)) else emptyMap()
            val hostPart = if (qIdx >= 0) main.substring(0, qIdx) else main

            val atIdx = hostPart.indexOf('@')
            val uuid = if (atIdx >= 0) hostPart.substring(0, atIdx) else ""
            val hostPort = if (atIdx >= 0) hostPart.substring(atIdx + 1) else hostPart

            val (host, port) = splitHostPort(hostPort)
            val country = extractCountry(name)
            val flag = countryToFlag(country)

            VpnServer(
                id = "vless_${index}_${host}",
                name = cleanName(name),
                country = country,
                flag = flag,
                host = host,
                port = port,
                protocol = "vless",
                config = line,
                load = 0
            )
        } catch (_: Exception) { null }
    }

    // ── VMESS ─────────────────────────────────────────────────────────────────
    private fun parseVmess(line: String, index: Int): VpnServer? {
        return try {
            val b64 = line.removePrefix("vmess://")
            val json = Base64.decode(b64, Base64.DEFAULT).toString(Charsets.UTF_8)
            val obj = gson.fromJson(json, Map::class.java)
            val host = obj["add"]?.toString() ?: return null
            val port = obj["port"]?.toString()?.toIntOrNull() ?: 443
            val name = obj["ps"]?.toString() ?: "VMess $index"
            val country = extractCountry(name)

            VpnServer(
                id = "vmess_${index}_${host}",
                name = cleanName(name),
                country = country,
                flag = countryToFlag(country),
                host = host,
                port = port,
                protocol = "vmess",
                config = line,
                load = 0
            )
        } catch (_: Exception) { null }
    }

    // ── HYSTERIA2 ─────────────────────────────────────────────────────────────
    private fun parseHysteria2(line: String, index: Int): VpnServer? {
        return try {
            val withoutScheme = line.removePrefix("hysteria2://").removePrefix("hy2://")
            val hashIdx = withoutScheme.lastIndexOf('#')
            val rawName = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else "Hysteria2 $index"
            val name = urlDecode(rawName).ifBlank { "Hysteria2 $index" }
            val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme

            val qIdx = main.indexOf('?')
            val hostPart = if (qIdx >= 0) main.substring(0, qIdx) else main

            val atIdx = hostPart.indexOf('@')
            val hostPort = if (atIdx >= 0) hostPart.substring(atIdx + 1) else hostPart
            val (host, port) = splitHostPort(hostPort)
            val country = extractCountry(name)

            VpnServer(
                id = "hy2_${index}_${host}",
                name = cleanName(name),
                country = country,
                flag = countryToFlag(country),
                host = host,
                port = port,
                protocol = "hysteria2",
                config = line,
                load = 0
            )
        } catch (_: Exception) { null }
    }

    // ── SHADOWSOCKS ───────────────────────────────────────────────────────────
    private fun parseShadowsocks(line: String, index: Int): VpnServer? {
        return try {
            val withoutScheme = line.removePrefix("ss://")
            val hashIdx = withoutScheme.lastIndexOf('#')
            val name = if (hashIdx >= 0) urlDecode(withoutScheme.substring(hashIdx + 1)) else "SS $index"
            val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme

            val atIdx = main.lastIndexOf('@')
            val hostPort = if (atIdx >= 0) main.substring(atIdx + 1) else main
            val (host, port) = splitHostPort(hostPort)
            val country = extractCountry(name)

            VpnServer(
                id = "ss_${index}_${host}",
                name = cleanName(name),
                country = country,
                flag = countryToFlag(country),
                host = host,
                port = port,
                protocol = "shadowsocks",
                config = line,
                load = 0
            )
        } catch (_: Exception) { null }
    }

    // ── TROJAN ────────────────────────────────────────────────────────────────
    private fun parseTrojan(line: String, index: Int): VpnServer? {
        return try {
            val withoutScheme = line.removePrefix("trojan://")
            val hashIdx = withoutScheme.lastIndexOf('#')
            val name = if (hashIdx >= 0) urlDecode(withoutScheme.substring(hashIdx + 1)) else "Trojan $index"
            val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme

            val qIdx = main.indexOf('?')
            val hostPart = if (qIdx >= 0) main.substring(0, qIdx) else main
            val atIdx = hostPart.indexOf('@')
            val hostPort = if (atIdx >= 0) hostPart.substring(atIdx + 1) else hostPart
            val (host, port) = splitHostPort(hostPort)
            val country = extractCountry(name)

            VpnServer(
                id = "trojan_${index}_${host}",
                name = cleanName(name),
                country = country,
                flag = countryToFlag(country),
                host = host,
                port = port,
                protocol = "trojan",
                config = line,
                load = 0
            )
        } catch (_: Exception) { null }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        // Handle IPv6 [::1]:port
        return if (hostPort.startsWith("[")) {
            val bracket = hostPort.indexOf(']')
            val host = hostPort.substring(1, bracket)
            val port = hostPort.substring(bracket + 2).toIntOrNull() ?: 443
            host to port
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon < 0) return hostPort to 443
            val host = hostPort.substring(0, colon)
            val port = hostPort.substring(colon + 1).toIntOrNull() ?: 443
            host to port
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&").mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null else it.substring(0, eq) to urlDecode(it.substring(eq + 1))
        }.toMap()
    }

    private fun urlDecode(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun cleanName(raw: String): String {
        // Remove emoji flags (regional indicator symbols U+1F1E6..U+1F1FF)
        return raw.replace(Regex("[\\uD83C\\uDDE6-\\uD83C\\uDDFF]{2}"), "").trim()
            .replace(Regex("^[\\s|–-]+"), "").trim()
    }

    private fun extractCountry(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("россия") || lower.contains("russia") || lower.contains("мск") ||
            lower.contains("спб") || lower.contains("екатеринб") || lower.contains("хабаровск") -> "Россия"
            lower.contains("нидерланд") || lower.contains("netherlands") || lower.contains("голланд") -> "Нидерланды"
            lower.contains("германи") || lower.contains("germany") || lower.contains("deutsch") -> "Германия"
            lower.contains("финлянд") || lower.contains("finland") -> "Финляндия"
            lower.contains("швеци") || lower.contains("sweden") -> "Швеция"
            lower.contains("сша") || lower.contains("usa") || lower.contains("united states") ||
            lower.contains("атланта") || lower.contains("денвер") || lower.contains("эшберн") -> "США"
            lower.contains("велик") || lower.contains("uk") || lower.contains("britain") -> "Великобритания"
            lower.contains("франц") || lower.contains("france") -> "Франция"
            lower.contains("польш") || lower.contains("poland") -> "Польша"
            lower.contains("латви") || lower.contains("latvia") -> "Латвия"
            lower.contains("эстони") || lower.contains("estonia") -> "Эстония"
            lower.contains("литв") || lower.contains("lithuania") -> "Литва"
            lower.contains("чехи") || lower.contains("czech") -> "Чехия"
            lower.contains("австри") || lower.contains("austria") -> "Австрия"
            lower.contains("швейцар") || lower.contains("switzerland") -> "Швейцария"
            lower.contains("норвег") || lower.contains("norway") -> "Норвегия"
            lower.contains("дания") || lower.contains("denmark") -> "Дания"
            lower.contains("италия") || lower.contains("italy") -> "Италия"
            lower.contains("испани") || lower.contains("spain") -> "Испания"
            lower.contains("белар") || lower.contains("belarus") -> "Беларусь"
            lower.contains("украин") || lower.contains("ukraine") -> "Украина"
            lower.contains("казахст") || lower.contains("kazakhstan") -> "Казахстан"
            lower.contains("япони") || lower.contains("japan") -> "Япония"
            lower.contains("канад") || lower.contains("canada") -> "Канада"
            lower.contains("австрали") || lower.contains("australia") -> "Австралия"
            lower.contains("сингапур") || lower.contains("singapore") -> "Сингапур"
            lower.contains("гонконг") || lower.contains("hong kong") -> "Гонконг"
            lower.contains("турци") || lower.contains("turkey") -> "Турция"
            lower.contains("израил") || lower.contains("israel") -> "Израиль"
            lower.contains("грузи") || lower.contains("georgia") -> "Грузия"
            lower.contains("молдов") || lower.contains("moldova") -> "Молдова"
            lower.contains("армени") || lower.contains("armenia") -> "Армения"
            else -> "Сервер ${name.take(20)}"
        }
    }

    private fun countryToFlag(country: String): String = when (country) {
        "Россия" -> "🇷🇺"
        "Нидерланды" -> "🇳🇱"
        "Германия" -> "🇩🇪"
        "Финляндия" -> "🇫🇮"
        "Швеция" -> "🇸🇪"
        "США" -> "🇺🇸"
        "Великобритания" -> "🇬🇧"
        "Франция" -> "🇫🇷"
        "Польша" -> "🇵🇱"
        "Латвия" -> "🇱🇻"
        "Эстония" -> "🇪🇪"
        "Литва" -> "🇱🇹"
        "Чехия" -> "🇨🇿"
        "Австрия" -> "🇦🇹"
        "Швейцария" -> "🇨🇭"
        "Норвегия" -> "🇳🇴"
        "Дания" -> "🇩🇰"
        "Италия" -> "🇮🇹"
        "Испания" -> "🇪🇸"
        "Беларусь" -> "🇧🇾"
        "Украина" -> "🇺🇦"
        "Казахстан" -> "🇰🇿"
        "Япония" -> "🇯🇵"
        "Канада" -> "🇨🇦"
        "Австралия" -> "🇦🇺"
        "Сингапур" -> "🇸🇬"
        "Гонконг" -> "🇭🇰"
        "Турция" -> "🇹🇷"
        "Израиль" -> "🇮🇱"
        "Грузия" -> "🇬🇪"
        "Молдова" -> "🇲🇩"
        "Армения" -> "🇦🇲"
        else -> "🌐"
    }

    fun getCachedServers(): List<VpnServer> = prefs.loadServers()

    fun needsUpdate(intervalHours: Int): Boolean {
        val last = prefs.getLastUpdateTime()
        if (last == 0L) return true
        return System.currentTimeMillis() - last > intervalHours * 3600 * 1000L
    }
}