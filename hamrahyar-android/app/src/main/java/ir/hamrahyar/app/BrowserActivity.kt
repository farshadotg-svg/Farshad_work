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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class BrowserActivity : Activity() {
    companion object {
        const val EXTRA_URL = "site_url"
        const val EXTRA_GOAL = "agent_goal"
        const val EXTRA_BACKEND = "agent_backend"
        const val EXTRA_TOKEN = "agent_token"
        const val EXTRA_SHARE_PROFILE = "share_profile"
    }
    private lateinit var web: WebView
    private lateinit var report: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resumeButton: android.widget.Button
    private val prefs by lazy { getSharedPreferences("hamrahyar_local", MODE_PRIVATE) }
    private val network = Executors.newSingleThreadExecutor()
    private var assistantRunning = false
    private var stopped = false
    private var stepCount = 0
    private var filledCount = 0
    private var lastUrl = ""
    private var previous = JSONArray()
    private val goal by lazy { intent.getStringExtra(EXTRA_GOAL).orEmpty().take(1200) }
    private val backend by lazy { intent.getStringExtra(EXTRA_BACKEND).orEmpty().trim().trimEnd('/') }
    private val token by lazy { intent.getStringExtra(EXTRA_TOKEN).orEmpty().trim() }
    private val shareProfile by lazy { intent.getBooleanExtra(EXTRA_SHARE_PROFILE, false) }

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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme !in listOf("http", "https")
                override fun onPageStarted(view: WebView, startedUrl: String, favicon: android.graphics.Bitmap?) {
                    progress.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    progress.visibility = View.GONE
                    if (finishedUrl != lastUrl) { lastUrl = finishedUrl; previous = JSONArray() }
                    if (!assistantRunning && !stopped) startAgentLoop()
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        report = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; setPadding(18, 14, 18, 18)
            text = "صفحه در حال بارگذاری است…"
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(17, 26, 45)); addView(report) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(135)))
        resumeButton = android.widget.Button(this).apply {
            text = "ادامه پس از انجام مرحله دستی"
            visibility = View.GONE
            setOnClickListener {
                if (!assistantRunning) {
                    stopped = false
                    visibility = View.GONE
                    assistantRunning = true
                    report.text = "صفحه دوباره بررسی می‌شود…"
                    observePage()
                }
            }
        }
        root.addView(resumeButton, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(root)
        web.loadUrl(url)
        if (backend.isBlank() || token.isBlank() || goal.isBlank()) {
            report.text = "مرورگر آماده است. برای فعال شدن هوش مصنوعی، هدف کار و نشانی سرور HTTPS را در صفحه اصلی تنظیم کن. این نسخه بدون اتصال سرور فقط سایت را نمایش می‌دهد."
        } else if (!backend.startsWith("https://")) {
            report.text = "نشانی سرور باید با HTTPS شروع شود؛ اتصال ناامن پذیرفته نمی‌شود."
            stopped = true
        } else report.text = "در انتظار بارگذاری سایت و آماده‌سازی دستیار هوشمند…"
    }

    private fun startAgentLoop() {
        if (backend.isBlank() || token.isBlank() || goal.isBlank() || assistantRunning || stopped) return
        assistantRunning = true; stepCount = 0; filledCount = 0; previous = JSONArray()
        report.text = "هوش مصنوعی در حال بررسی ساختار صفحه است…"
        observePage()
    }

    private val snapshotScript = """
        (function(){
          const norm=s=>(s||'').toString().replace(/[ي]/g,'ی').replace(/[ك]/g,'ک').replace(/\s+/g,' ').trim();
          const sensitive=/(captcha|کپچا|otp|one.?time|verification.?code|رمز|password|پسورد|کد.?تأیید|کد.?تایید|national.?id|کد.?ملی|identity|احراز.?هویت|queue|صف.?انتظار)/i;
          const elements=[]; let id=1;
          const label=e=>{
            const labels=e.labels?[...e.labels].map(x=>x.innerText).join(' '):'';
            const parent=e.closest('label');
            return norm([labels,parent?.innerText,e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.name,e.id,e.getAttribute('autocomplete')].filter(Boolean).join(' ')).slice(0,180);
          };
          for(const e of document.querySelectorAll('input,textarea,select,button,a,[role=button]')){
            const tag=e.tagName.toLowerCase(), type=(e.type||'').toLowerCase();
            const text=norm(e.innerText||e.value||e.getAttribute('aria-label')).slice(0,160), hint=label(e);
            if(e.disabled||e.readOnly||e.getAttribute('aria-disabled')==='true'||e.closest('[aria-hidden=true]'))continue;
            if(sensitive.test(hint+' '+text)||['password','hidden','file','checkbox','radio','submit','reset','image'].includes(type))continue;
            let kind='other';
            if(['input','textarea','select'].includes(tag)&&['text','email','tel','number','search','url','','textarea','select-one','select-multiple'].includes(type))kind='field';
            if(['button','a'].includes(tag)||e.getAttribute('role')==='button')kind='click';
            if(kind==='other')continue;
            const itemId=id++; e.setAttribute('data-hy-agent-id',String(itemId));
            elements.push({id:itemId,kind:kind,tag:tag,type:type,label:hint,text:text,placeholder:(e.getAttribute('placeholder')||'').slice(0,100)});
            if(elements.length>=70)break;
          }
          const body=norm(document.body?.innerText||'').slice(0,6500);
          return JSON.stringify({url:location.href,title:document.title,body:body,elements:elements});
        })()
    """.trimIndent()

    private fun observePage() {
        if (stopped || stepCount >= 8) {
            finishAssistant("برای جلوگیری از اجرای بی‌پایان، این مرحله متوقف شد. صفحه را بررسی کن و در صورت نیاز دوباره شروع کن."); return
        }
        web.evaluateJavascript(snapshotScript) { raw ->
            val snapshot = runCatching { JSONObject(decodeJsString(raw)) }.getOrNull()
            if (snapshot == null) {
                finishAssistant("خواندن ساختار صفحه ممکن نشد؛ ممکن است سایت محدودیت یا ساختار غیرمعمول داشته باشد."); return@evaluateJavascript
            }
            if (shareProfile) {
                val profile = runCatching { JSONObject(prefs.getString("profile", "{}") ?: "{}") }.getOrNull() ?: JSONObject()
                val safeProfile = JSONObject()
                listOf("fullName", "phone", "email", "address").forEach { key ->
                    val value = profile.optString(key).take(180)
                    if (value.isNotBlank()) safeProfile.put(key, value)
                }
                snapshot.put("profile", safeProfile)
            } else snapshot.put("profile", JSONObject())
            stepCount++
            requestPlan(snapshot)
        }
    }

    private fun requestPlan(snapshot: JSONObject) {
        network.execute {
            var result: JSONObject? = null
            var errorText: String? = null
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$backend/v1/agent/step").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 12000; readTimeout = 30000; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val payload = JSONObject().put("goal", goal).put("page", snapshot).put("previous", previous)
                connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("پاسخ سرور: HTTP $code")
                result = JSONObject(body)
            } catch (e: Exception) {
                errorText = e.message ?: "اتصال به سرور ناموفق بود."
            } finally { connection?.disconnect() }
            runOnUiThread {
                if (isFinishing || stopped) return@runOnUiThread
                if (errorText != null || result == null) finishAssistant("اتصال هوش مصنوعی برقرار نشد: ${errorText ?: "خطای نامشخص"}")
                else handlePlan(result!!)
            }
        }
    }

    private fun handlePlan(plan: JSONObject) {
        val status = plan.optString("status")
        val message = plan.optString("message").take(400)
        val actions = plan.optJSONArray("actions") ?: JSONArray()
        if (status == "needs_user" || status == "blocked" || status == "done") {
            finishAssistant(message.ifBlank {
                when (status) {
                    "done" -> "هدف اعلام‌شده تکمیل شد؛ نتیجه را بررسی کن."
                    "blocked" -> "این مرحله به دلیل محدودیت امنیتی متوقف شد."
                    else -> "برای ادامه، اقدام لازم را خودت انجام بده."
                }
            }, allowResume = status == "needs_user"); return
        }
        if (actions.length() == 0) {
            finishAssistant(message.ifBlank { "برای ادامه اقدام امنی پیدا نشد؛ صفحه را بررسی کن." }); return
        }
        report.text = "هوش مصنوعی: $message\nمرحله $stepCount از ۸"
        executeActionAt(actions, 0)
    }

    private fun executeActionAt(actions: JSONArray, index: Int) {
        if (index >= actions.length()) { web.postDelayed({ observePage() }, 1100); return }
        val action = actions.optJSONObject(index) ?: run { executeActionAt(actions, index + 1); return }
        val type = action.optString("type")
        val id = action.optInt("element_id", -1)
        val value = action.optString("value").take(500)
        val reason = action.optString("reason").take(160)
        when (type) {
            "fill" -> {
                val script = """
                    (function(){
                      const e=document.querySelector('[data-hy-agent-id="$id"]');
                      if(!e||e.disabled||e.readOnly)return 'not_found';
                      const t=(e.type||'').toLowerCase();
                      const hint=[e.name,e.id,e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.autocomplete].join(' ');
                      if(/password|captcha|otp|کپچا|رمز|کد.?تأیید|کد.?ملی|national.?id|identity|احراز.?هویت/i.test(hint)||['password','hidden','file','checkbox','radio','submit','reset'].includes(t))return 'blocked';
                      if(!['INPUT','TEXTAREA','SELECT'].includes(e.tagName))return 'not_field';
                      const v=${JSONObject.quote(value)};
                      if(e.tagName==='SELECT'){
                        const o=[...e.options].find(x=>x.value===v||x.text.trim()===v);
                        if(!o)return 'option_missing';e.value=o.value;
                      }else{
                        const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
                        const s=Object.getOwnPropertyDescriptor(p,'value')?.set;
                        if(s)s.call(e,v);else e.value=v;
                      }
                      e.dispatchEvent(new Event('input',{bubbles:true}));
                      e.dispatchEvent(new Event('change',{bubbles:true}));
                      return 'filled';
                    })()
                """.trimIndent()
                web.evaluateJavascript(script) { raw ->
                    val result = decodeJsString(raw)
                    if (result == "filled") filledCount++
                    previous.put(JSONObject().put("type", type).put("element_id", id).put("result", result).put("reason", reason))
                    executeActionAt(actions, index + 1)
                }
            }
            "click" -> {
                val script = """
                    (function(){
                      const e=document.querySelector('[data-hy-agent-id="$id"]');
                      if(!e||e.disabled||e.getAttribute('aria-disabled')==='true')return 'not_found';
                      const t=(e.innerText||e.value||e.getAttribute('aria-label')||'').trim();
                      if(!/^(ادامه|مرحله بعد|مرحله‌ی بعد|بعدی|گام بعد|next|continue|proceed)$/i.test(t))return 'not_safe_navigation';
                      if(/ثبت نهایی|پرداخت|خرید|ثبت سفارش|ارسال نهایی|submit|pay|purchase|confirm/i.test(t))return 'blocked';
                      e.click();return 'clicked';
                    })()
                """.trimIndent()
                web.evaluateJavascript(script) { raw ->
                    val result = decodeJsString(raw)
                    previous.put(JSONObject().put("type", type).put("element_id", id).put("result", result).put("reason", reason))
                    if (result == "clicked") web.postDelayed({ observePage() }, 1200)
                    else executeActionAt(actions, index + 1)
                }
            }
            "wait" -> {
                previous.put(JSONObject().put("type", type).put("result", "waited"))
                web.postDelayed({ executeActionAt(actions, index + 1) }, 1200)
            }
            "ask_user" -> finishAssistant(reason.ifBlank { "برای ادامه به بررسی تو نیاز است." }, allowResume = true)
            "stop" -> finishAssistant(reason.ifBlank { "این مرحله متوقف شد." })
            else -> executeActionAt(actions, index + 1)
        }
    }

    private fun finishAssistant(message: String, allowResume: Boolean = false) {
        assistantRunning = false; stopped = true
        if (::resumeButton.isInitialized) resumeButton.visibility = if (allowResume) View.VISIBLE else View.GONE
        report.text = "گزارش همراه‌یار\n$message\n\nفیلدهای تکمیل‌شده: $filledCount\nتعداد بررسی‌های هوشمند: $stepCount\n\nرمز، کد پیامکی، کپچا، احراز هویت و صف انتظار خودکار انجام نمی‌شوند. ثبت نهایی، خرید و پرداخت نیز همیشه با خودت است."
    }
    private fun decodeJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(raw)
    }
    override fun onDestroy() {
        stopped = true; network.shutdownNow()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}