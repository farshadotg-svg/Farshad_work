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
import org.json.JSONObject

class BrowserActivity : Activity() {
    companion object { const val EXTRA_URL = "site_url" }
    private lateinit var web: WebView
    private lateinit var report: TextView
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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme !in listOf("http", "https")
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    progress.visibility = View.GONE
                    startSmartAssistant()
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        report = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; setPadding(18, 14, 18, 18)
            text = "دستیار هوشمند در حال بررسی صفحه است…"
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(17, 26, 45)); addView(report) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(165)))
        setContentView(root)
        web.loadUrl(url)
    }

    private fun startSmartAssistant() {
        val profile = runCatching { JSONObject(prefs.getString("profile", "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        val p = JSONObject.quote(profile.toString())
        val script = """
        (function(){
          if(window.__hyAssistantStarted)return 'already-running';
          window.__hyAssistantStarted=true;
          const profile=JSON.parse($p);
          const safeNext=/^(ادامه|مرحله بعد|مرحله‌ی بعد|بعدی|گام بعد|next|continue|proceed)$/i;
          const finalWords=/(ثبت نهایی|ثبت سفارش|پرداخت|خرید|تأیید نهایی|ارسال نهایی|submit|place order|pay now|purchase|confirm order)/i;
          const sensitive=/(captcha|کپچا|otp|one.?time|verification.?code|رمز|password|پسورد|کد.?تأیید|کد.?تایید)/i;
          const rules=[
            {key:'fullName',re:/(full.?name|نام.?و.?نام|نام.?خانوادگی|نام مشتری|نام و نام خانوادگی)/i},
            {key:'phone',re:/(mobile|phone|tel|موبایل|تلفن|شماره.?تماس|شماره.?همراه)/i},
            {key:'nationalId',re:/(national.?id|national.?code|کد.?ملی)/i},
            {key:'email',re:/(email|ایمیل)/i},
            {key:'address',re:/(address|نشانی|آدرس)/i}
          ];
          const normalize=s=>(s||'').toString().replace(/[ي]/g,'ی').replace(/[ك]/g,'ک').replace(/\s+/g,' ').trim();
          const labelFor=e=>{
            const labels=e.labels?[...e.labels].map(x=>x.innerText).join(' '):'';
            const parent=e.closest('label');
            return normalize([labels,parent?.innerText,e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.name,e.id,e.getAttribute('autocomplete')].filter(Boolean).join(' '));
          };
          function fill(){
            let count=0;
            for(const e of document.querySelectorAll('input,textarea,select')){
              const type=(e.type||'').toLowerCase(), hint=labelFor(e);
              if(e.disabled||e.readOnly||sensitive.test(hint)||['password','hidden','submit','button','checkbox','radio','file'].includes(type))continue;
              if(e.value&&String(e.value).trim())continue;
              const rule=rules.find(r=>r.re.test(hint));
              if(!rule||!profile[rule.key])continue;
              if(e.tagName==='SELECT'){
                const option=[...e.options].find(o=>normalize(o.text).includes(normalize(profile[rule.key]))||normalize(o.value)===normalize(profile[rule.key]));
                if(!option)continue;e.value=option.value;
              }else{
                const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
                const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;
                if(setter)setter.call(e,profile[rule.key]);else e.value=profile[rule.key];
              }
              e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));count++;
            }
            return count;
          }
          function advance(){
            const count=Number(sessionStorage.getItem('__hyAdvanceCount')||'0');
            if(count>=5)return false;
            const candidates=[...document.querySelectorAll('button,a,[role=button],input[type=button],input[type=submit]')];
            const b=candidates.find(el=>{
              const t=normalize(el.innerText||el.value||el.getAttribute('aria-label'));
              if(!safeNext.test(t)||finalWords.test(t))return false;
              if(el.disabled||el.getAttribute('aria-disabled')==='true')return false;
              if(el.closest('[aria-hidden=true]'))return false;
              return true;
            });
            if(!b)return false;
            sessionStorage.setItem('__hyAdvanceCount',String(count+1));
            setTimeout(()=>b.click(),650);
            return true;
          }
          let total=fill();
          const summary=()=>JSON.stringify({filled:total,fields:document.querySelectorAll('input,textarea,select').length,title:document.title,url:location.href});
          window.__hySummary=summary;
          const observer=new MutationObserver(()=>{
            const n=fill();if(n){total+=n;window.__hyLastFill=total;}
          });
          observer.observe(document.documentElement,{childList:true,subtree:true});
          setTimeout(()=>{if(advance())return;},1200);
          return summary();
        })()
        """.trimIndent()
        web.evaluateJavascript(script) { result ->
            runOnUiThread {
                val summary = runCatching {
                    val raw = result?.let { encoded ->
                        runCatching { org.json.JSONArray("[$encoded]").getString(0) }.getOrElse { encoded }
                    } ?: "{}"
                    JSONObject(raw)
                }.getOrNull()
                if (summary == null) {
                    report.text = "صفحه باز شد؛ اما تحلیل خودکار کامل نشد. ممکن است سایت ساختار غیرمعمول یا محدودیت ضدربات داشته باشد."
                } else {
                    val fields = summary.optInt("fields")
                    val filled = summary.optInt("filled")
                    report.text = "دستیار همراه‌یار فعال شد\nفیلدهای صفحه: $fields\nفیلدهای تکمیل‌شده از پروفایل: $filled\n\nهمراه‌یار فیلدهای خالی و قابل تشخیص را خودکار تکمیل می‌کند و تغییرات پویا را زیر نظر می‌گیرد. فقط دکمه‌های صریح «ادامه/مرحله بعد» را در حد محدود دنبال می‌کند. ثبت نهایی، خرید، پرداخت، رمز، کد یک‌بارمصرف و کپچا خودکار انجام نمی‌شوند."
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}