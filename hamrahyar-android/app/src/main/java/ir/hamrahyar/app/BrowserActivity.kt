package ir.hamrahyar.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class BrowserActivity : Activity() {
    companion object { const val EXTRA_URL = "site_url" }
    private lateinit var web: WebView
    private lateinit var report: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(9, 14, 27)) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        root.addView(progress, LinearLayout.LayoutParams(-1, 4))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.url.scheme !in listOf("http", "https")
                }
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    progress.setVisibility(View.GONE)
                    inspectPage()
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        report = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; setPadding(18, 14, 18, 18)
            text = "در حال بازکردن سایت و بررسی ساختار فرم…"
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(17, 26, 45)); addView(report) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(180)))
        setContentView(root)
        web.loadUrl(url)
    }

    private fun inspectPage() {
        val script = """(function(){
          const fields=[...document.querySelectorAll('input,select,textarea')].map((e)=>({
            tag:e.tagName.toLowerCase(), type:(e.type||'').toLowerCase(), name:e.name||'', id:e.id||'',
            label:(e.labels&&e.labels.length?[...e.labels].map(x=>x.innerText).join(' '):'') || e.getAttribute('aria-label') || e.placeholder || '',
            required:!!e.required, disabled:!!e.disabled
          }));
          const buttons=[...document.querySelectorAll('button,input[type=submit],input[type=button],[role=button]')].map(e=>(e.innerText||e.value||e.getAttribute('aria-label')||'').trim()).filter(Boolean).slice(0,30);
          return JSON.stringify({title:document.title,url:location.href,fields:fields,buttons:buttons});
        })()"""
        web.evaluateJavascript(script) { encoded ->
            runOnUiThread {
                try {
                    val raw = JSONTokener(encoded).nextValue() as? String ?: return@runOnUiThread
                    val data = JSONObject(raw)
                    val fields = data.optJSONArray("fields") ?: JSONArray()
                    val buttons = data.optJSONArray("buttons") ?: JSONArray()
                    val lines = mutableListOf("تحلیل اولیه صفحه انجام شد", "عنوان: ${data.optString("title")}", "فیلدهای شناسایی‌شده: ${fields.length()}")
                    for (i in 0 until fields.length()) {
                        val f = fields.getJSONObject(i)
                        val label = f.optString("label").ifBlank { f.optString("name").ifBlank { f.optString("id").ifBlank { "بدون برچسب" } } }
                        lines += "• $label  (${f.optString("tag")} / ${f.optString("type")})" + if (f.optBoolean("required")) " · الزامی" else ""
                    }
                    lines += "\nدکمه‌های قابل مشاهده:"
                    for (i in 0 until buttons.length()) lines += "• ${buttons.optString(i)}"
                    lines += "\nاین فقط شناسایی ساختار صفحه است. ورود اطلاعات حساس، کپچا، رمز یک‌بارمصرف و ارسال نهایی باید با اقدام و تأیید خودت انجام شود."
                    report.text = lines.joinToString("\n")
                } catch (_: Exception) { report.text = "صفحه باز شد، اما نتوانستم اطلاعات فرم را بخوانم. ممکن است سایت بارگذاری پویا داشته باشد." }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}