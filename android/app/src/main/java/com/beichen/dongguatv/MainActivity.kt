package com.beichen.dongguatv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.beichen.dongguatv.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 阶段1 骨架入口页：展示深色主题 + 暖橙红配色，提供一个"测试后端连通"按钮，
 * 点按后用原生 HttpURLConnection 探测 tv.bcpan.icu 是否可达，结果回显到屏幕。
 * 阶段2 会替换为底部三 Tab 的首页/搜索/我的。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener { probeBackend() }
        // 首次进入自动探测一次
        probeBackend()
    }

    private fun probeBackend() {
        binding.tvResult.text = "测试中…\n目标 https://tv.bcpan.icu/api/hot"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                probe("https://tv.bcpan.icu/api/hot")
            }
            binding.tvResult.text = result
        }
    }

    private fun probe(target: String): String {
        return try {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DongguatvProbe/1.0")
                instanceFollowRedirects = true
            }
            conn.connect()
            val code = conn.responseCode
            val len = conn.contentLength
            val ctype = conn.contentType
            conn.disconnect()
            buildString {
                appendLine("✓ 后端可达")
                appendLine("HTTP $code")
                appendLine("Content-Type: $ctype")
                appendLine("Content-Length: ${if (len < 0) "未知" else "$len 字节"}")
                appendLine("目标: $target")
                appendLine()
                appendLine("骨架就绪，等待阶段2：")
                appendLine("• Retrofit ApiClient")
                appendLine("• 登录/首页/搜索/详情")
                appendLine("• ExoPlayer + 弹幕叠加")
            }
        } catch (e: Exception) {
            "✗ 连接失败\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
