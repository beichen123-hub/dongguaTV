package com.beichen.dongguatv.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 播放地址解析器。
 *
 * 实测 7 个采集源里只有 bfzy / db 给的是直链 m3u8：
 *   bfzy  https://c1.rrcdnbf5.com/video/xxx/第01集/index.m3u8
 *   db    https://b3.bdzybf22.com/videos/.../index.m3u8
 * 其余给的是「分享页」或「播放页」，ExoPlayer 直接喂会 UnrecognizedInputFormat：
 *   lz       https://vip.lz-cdn3.com/share/xxx      页面里有 const url = "/xxx/index.m3u8?sign=..."
 *   ffzy     https://vod.feifei-online.com/share/xxx 同上
 *   subo/xl  https://play.xluuss.com/play/xxx        页面里有 https://play.xluuss.com/play/xxx/index.m3u8
 *   hongniu  https://hn.bfvvs.com/play/xxx           （域名常连不上，解析成功率低）
 *
 * 所以：直链直接用；page 型先 HTTP 拉 HTML 正则抠 m3u8；抠不到返回 null，
 * 由播放器退化成 WebView 打开原页面（页面自带 hls.js，能播，代价是没有弹幕）。
 */
object SourceResolver {

    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 页内 m3u8 地址：单双引号包起来的、含 .m3u8 的字符串 */
    private val M3U8_PATTERN = Regex("""["']([^"']*?\.m3u8[^"']*)["']""")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 已经是能直接喂播放器的地址 */
    fun isDirect(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)

    /** 内存缓存：同一集重播别重复拉解析页 */
    private val cache = HashMap<String, String?>()

    /**
     * 入参可能是直链，也可能是分享页。
     * @return 可播放的直链；解析不出来返回 null
     */
    suspend fun resolve(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        cache[raw]?.let { return it }
        val result = if (isDirect(raw)) encodeNonAscii(raw) else parsePage(raw)
        if (cache.size > 60) cache.clear()
        cache[raw] = result
        return result
    }

    private suspend fun parsePage(raw: String): String? = withContext(Dispatchers.IO) {
        try {
            val safeUrl = encodeNonAscii(raw)
            val req = Request.Builder()
                .url(safeUrl)
                // OkHttp 的 header 值只允许 ASCII，Referer 直接用 raw 会在带中文的地址上抛异常
                .header("User-Agent", UA)
                .header("Referer", safeUrl)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string().orEmpty()
                if (html.isEmpty()) return@withContext null
                val hit = M3U8_PATTERN.find(html)?.groupValues?.get(1)?.trim().orEmpty()
                if (hit.isEmpty()) return@withContext null
                absolutize(raw, hit)?.let { encodeNonAscii(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 采集站的路径里有中文（如 bfzy 的 `/video/xxx/第01集/index.m3u8`），
     * HttpURLConnection / OkHttp 遇到非 ASCII 会报 unexpected url，必须百分号编码。
     */
    private fun encodeNonAscii(s: String): String = buildString(s.length + 16) {
        for (ch in s) {
            if (ch.code in 0x21..0x7E) {
                append(ch)
            } else {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                    append('%')
                    append(String.format("%02X", b))
                }
            }
        }
    }

    /** 页面里给的多半是相对路径，补上原页面的协议+域名 */
    private fun absolutize(pageUrl: String, hit: String): String? = when {
        hit.startsWith("http://", true) || hit.startsWith("https://", true) -> hit
        hit.startsWith("//") -> "https:$hit"
        hit.startsWith("/") -> {
            val u = Uri.parse(pageUrl)
            val port = if (u.port > 0) ":${u.port}" else ""
            "${u.scheme}://${u.host}$port$hit"
        }
        else -> null
    }
}
