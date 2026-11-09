package ir.hamrahyar.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.net.URI

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("hamrahyar_local", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(9, 14, 27))
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(LocalUiBridge(), "HamrahYarAndroid")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class LocalUiBridge {
        @JavascriptInterface
        fun openSite(rawUrl: String, rawGoal: String, rawBackend: String, rawToken: String, shareProfile: Boolean) {
            runOnUiThread {
                val uri = runCatching { URI(rawUrl) }.getOrNull()
                if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "آدرس سایت معتبر نیست", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val goal = rawGoal.trim().take(1200)
                if (goal.isBlank()) {
                    Toast.makeText(this@MainActivity, "هدف انجام کار را بنویس", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val backend = rawBackend.trim().trimEnd('/')
                val token = rawToken.trim()
                if (backend.isNotBlank() && (!backend.startsWith("https://") || token.length < 32)) {
                    Toast.makeText(this@MainActivity, "آدرس سرور باید HTTPS و توکن حداقل ۳۲ نویسه باشد", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                prefs.edit().putString("agent_backend", backend).putString("agent_token", token).apply()
                val intent = Intent(this@MainActivity, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, uri.toString())
                    .putExtra(BrowserActivity.EXTRA_GOAL, goal)
                    .putExtra(BrowserActivity.EXTRA_BACKEND, backend)
                    .putExtra(BrowserActivity.EXTRA_TOKEN, token)
                    .putExtra(BrowserActivity.EXTRA_SHARE_PROFILE, shareProfile)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun saveProfile(rawJson: String) {
            runCatching {
                val source = JSONObject(rawJson)
                val safe = JSONObject()
                listOf("fullName", "phone", "nationalId", "email", "address").forEach { key ->
                    safe.put(key, source.optString(key).take(250))
                }
                prefs.edit().putString("profile", safe.toString()).apply()
            }
        }

        @JavascriptInterface
        fun getProfile(): String = prefs.getString("profile", "{}") ?: "{}"

        @JavascriptInterface
        fun getAgentConfig(): String {
            val config = JSONObject()
                .put("backend", prefs.getString("agent_backend", "") ?: "")
                .put("token", prefs.getString("agent_token", "") ?: "")
            return config.toString()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}