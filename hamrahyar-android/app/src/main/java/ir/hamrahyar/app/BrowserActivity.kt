package ir.hamrahyar.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
    private lateinit var fillButton: Button
    private val prefs by lazy { getSharedPreferences("hamrahyar_local", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(9, 14, 27)) }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
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
                    progress.visibility = View.GONE
                    inspectPage()
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        fillButton = Button(this).apply {
            text = "پرکردن فیلدهای قابل تشخیص با اطلاعات پروفایل"
            isEnabled = false
            setOnClickListener { fillRecognizedFields() }
        }
        root.addView(fillButton, LinearLayout.LayoutParams(-1, dp(52)))
        report = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; setPadding(18, 14, 18, 18)
            text = "در حال بازکردن سایت و بررسی ساختار فرم…"
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(17, 26, 45)); addView(report) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(155)))
        setContentView(root)
        web.loadUrl(url)
    }

    private fun inspectPage() {
        val script = """(function(){
          const fields=[...document.querySelectorAll('input,select,textarea')].map((e)=>({
            tag:e.tagName.toLowerCase(), type:(e.type||'').toLowerCase(), name:e.name||'', id:e.id||'',
            placeholder:e.placeholder||'',
            label:(e.labels&&e.labels.length?[...e.labels].map(x=>x.innerText).join(' '):'') || e.getAttribute('aria-label') || e.placeholder || '',
            required:!!e.required, disabled:!!e.disabled, readOnly:!!e.readOnly
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
                    val lines = mutableListOf("تحلیل صفحه انجام شد", "عنوان: ${data.optString("title")}", "فیلدهای شناسایی‌شده: ${fields.length()}")
                    for (i in 0 until fields.length()) {
                        val f = fields.getJSONObject(i)
                        val label = f.optString("label").ifBlank { f.optString("name").ifBlank { f.optString("id").ifBlank { "بدون برچسب" } } }
                        lines += "• $label  (${f.optString("tag")} / ${f.optString("type")})" + if (f.optBoolean("required")) " · الزامی" else ""
                    }
                    lines += "\nدکمه‌های صفحه:"
                    for (i in 0 until buttons.length()) lines += "• ${buttons.optString(i)}"
                    lines += "\nبرای تکمیل فیلدهای قابل تشخیص، دکمه بالا را خودت بزن. رمز، کد پیامکی و کپچا پر نمی‌شوند و فرم هم خودکار ارسال نخواهد شد."
                    report.text = lines.joinToString("\n")
                    fillButton.isEnabled = fields.length() > 0 && hasProfile()
                } catch (_: Exception) {
                    report.text = "صفحه باز شد، اما نتوانستم اطلاعات فرم را بخوانم. ممکن است سایت بارگذاری پویا داشته باشد."
                    fillButton.isEnabled = false
                }
            }
        }
    }

    private fun hasProfile(): Boolean {
        val p = runCatching { JSONObject(prefs.getString("profile", "{}") ?: "{}") }.getOrNull() ?: return false
        return listOf("fullName", "phone", "nationalId", "email", "address").any { p.optString(it).isNotBlank() }
    }

    private fun fillRecognizedFields() {
        val profile = runCatching { JSONObject(prefs.getString("profile", "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        if (profile.length() == 0) {
            report.text = "ابتدا اطلاعاتت را در بخش پروفایل صفحه اصلی ذخیره کن."
            return
        }
        val profileLiteral = JSONObject.quote(profile.toString())
        val script = """(function(){
          const p=JSON.parse($profileLiteral);
          const rules=[
            {key:'fullName',re:/(full.?name|name|نام.?و.?نام|نام.?خانوادگی|نام مشتری)/i},
            {key:'phone',re:/(mobile|phone|tel|موبایل|تلفن|شماره.?تماس)/i},
            {key:'nationalId',re:/(national.?id|national.?code|کد.?ملی)/i},
            {key:'email',re:/(email|ایمیل)/i},
            {key:'address',re:/(address|نشانی|آدرس)/i}
          ];
          let count=0; const skipped=[];
          for(const e of document.querySelectorAll('input,textarea')){
            const type=(e.type||'').toLowerCase();
            if(e.disabled||e.readOnly||['password','hidden','submit','button','checkbox','radio','file'].includes(type))continue;
            const hint=[e.name,e.id,e.placeholder,e.getAttribute('aria-label'),e.labels?[...e.labels].map(x=>x.innerText).join(' '):''].join(' ');
            if(/captcha|کپچا|otp|one.?time|verification|رمز|password|کد.?تایید/i.test(hint))continue;
            if(e.value && e.value.trim())continue;
            const rule=rules.find(r=>r.re.test(hint));
            if(!rule||!p[rule.key])continue;
            const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value')?.set;
            if(setter)setter.call(e,p[rule.key]);else e.value=p[rule.key];
            e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));count++;
          }
          return JSON.stringify({count:count});
        })()"""
        web.evaluateJavascript(script) { encoded ->
            runOnUiThread {
                val count = runCatching {
                    val raw = JSONTokener(encoded).nextValue() as String
                    JSONObject(raw).optInt("count")
                }.getOrDefault(0)
                report.text = if (count > 0)
                    "$count فیلد با اطلاعات پروفایل پر شد. لطفاً همه موارد را بررسی کن؛ هیچ دکمه ارسال یا ثبت فشرده نشده است."
                else
                    "فیلد خالی و قابل تشخیصی مطابق اطلاعات پروفایل پیدا نشد. ممکن است نام‌گذاری فیلدهای این سایت متفاوت باشد."
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}