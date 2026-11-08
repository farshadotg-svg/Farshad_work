package ir.farshad.waterinvoice;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private CredentialManager credentialManager;
    private ValueCallback<Uri[]> pendingFileChooser;
    private static final int REQUEST_RESTORE_FILE = 4101;
    private static final int REQUEST_SAVE_FILE = 4102;
    private byte[] pendingSaveBytes;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        credentialManager = CredentialManager.create(this);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.addJavascriptInterface(new GoogleAuthBridge(), "AndroidGoogleAuth");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, REQUEST_RESTORE_FILE); }
                catch (Exception e) { pendingFileChooser = null; callback.onReceiveValue(null); }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installNativeGoogleSignInOverride();
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void installNativeGoogleSignInOverride() {
        String script =
            "window.completeNativeGoogleSignIn = async function(idToken) {" +
            " const waitForFirebase = async () => {" +
            "  const deadline = Date.now() + 15000;" +
            "  while ((!window.firebase || !window.firebase.auth || !window.firebase.firestore || typeof fbAuth === 'undefined' || !fbAuth || typeof fbDb === 'undefined' || !fbDb) && Date.now() < deadline) {" +
            "   await new Promise(resolve => setTimeout(resolve, 250));" +
            "  }" +
            "  if (window.firebase && window.firebase.auth && window.firebase.firestore && (typeof fbAuth === 'undefined' || !fbAuth || typeof fbDb === 'undefined' || !fbDb)) {" +
            "   if (typeof initFirebase === 'function' && typeof DEFAULT_FIREBASE_CONFIG !== 'undefined') initFirebase(DEFAULT_FIREBASE_CONFIG);" +
            "  }" +
            "  if (!window.firebase || !window.firebase.auth || !window.firebase.firestore) throw new Error('کتابخانه Firebase بارگذاری نشد؛ اینترنت گوشی را بررسی کنید.');" +
            "  if (typeof fbAuth === 'undefined' || !fbAuth || typeof fbDb === 'undefined' || !fbDb) throw new Error('راه‌اندازی Firebase کامل نشد. برنامه را ببندید و دوباره باز کنید.');" +
            " };" +
            " await waitForFirebase();" +
            " const credential = firebase.auth.GoogleAuthProvider.credential(idToken);" +
            " await fbAuth.signInWithCredential(credential);" +
            " const u = fbAuth.currentUser;" +
            " if (!u) throw new Error('ورود انجام نشد.');" +
            " const ref = fbDb.collection('users').doc(u.uid);" +
            " const snap = await ref.get();" +
            " if (snap.exists) {" +
            "  if (confirm('برای این حساب، پشتیبان ابری پیدا شد. اطلاعات ابری جایگزین اطلاعات فعلی این گوشی شود؟\\nتأیید = بازیابی از ابر\\nلغو = نگه‌داشتن اطلاعات گوشی و بارگذاری آن به ابر')) {" +
            "   const d = snap.data(); records = Array.isArray(d.records) ? d.records : [];" +
            "   localStorage.setItem(STORE, JSON.stringify(records));" +
            "   if (Array.isArray(d.customers)) localStorage.setItem('waterServiceCustomers_v1', JSON.stringify(d.customers));" +
            "   if (d.customerPhones && typeof d.customerPhones === 'object') { customerPhones = d.customerPhones; localStorage.setItem('waterServiceCustomerPhones_v1', JSON.stringify(customerPhones)); }" +
            "   render(); alert('اطلاعات ابری بازیابی شد.');" +
            "  } else { await syncCloud(true); }" +
            " } else { await syncCloud(true); alert('اتصال انجام شد و اطلاعات فعلی در حساب گوگل ذخیره شد.'); }" +
            "};" +
            "window.googleSignIn = async function() {" +
            " try {" +
            "  if (typeof window.AndroidGoogleAuth !== 'undefined' && window.AndroidGoogleAuth) {" +
            "   const idToken = await new Promise((resolve, reject) => {" +
            "    window.onNativeGoogleSignInSuccess = resolve;" +
            "    window.onNativeGoogleSignInError = reject;" +
            "    window.AndroidGoogleAuth.signIn();" +
            "   });" +
            "   await window.completeNativeGoogleSignIn(idToken);" +
            "  } else { throw new Error('ورود گوگل اندروید در دسترس نیست.'); }" +
            " } catch(e) { setCloudStatus('خطا: ' + (e && e.message ? e.message : e)); alert('ورود با گوگل انجام نشد. ' + (e && e.message ? e.message : e)); }" +
            "};";
        script +=
            "(function(){" +
            "const css = document.createElement('style');" +
            "css.textContent = '#appThemedNotice{position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.68);display:flex;align-items:center;justify-content:center;padding:22px;direction:rtl;font-family:inherit}#appThemedNotice .notice-card{width:min(92vw,390px);background:#171b25;color:#f4f5f8;border:1px solid rgba(255,255,255,.12);border-radius:18px;padding:22px;box-shadow:0 18px 55px rgba(0,0,0,.48);text-align:center}#appThemedNotice .notice-title{font-size:16px;font-weight:700;margin:0 0 12px;color:#f4f5f8}#appThemedNotice .notice-body{font-size:14px;line-height:1.9;white-space:pre-wrap;color:#d5d9e2;margin-bottom:18px;overflow-wrap:anywhere}#appThemedNotice button{border:0;border-radius:11px;padding:10px 25px;background:var(--accent,#64748b);color:#fff;font:inherit;font-weight:700;min-width:100px}';" +
            "document.head.appendChild(css);" +
            "window.alert = function(message){const old=document.getElementById('appThemedNotice');if(old)old.remove();const overlay=document.createElement('div');overlay.id='appThemedNotice';const card=document.createElement('div');card.className='notice-card';const title=document.createElement('div');title.className='notice-title';title.textContent='پیام برنامه';const body=document.createElement('div');body.className='notice-body';body.textContent=String(message==null?'':message);const btn=document.createElement('button');btn.textContent='متوجه شدم';btn.onclick=()=>overlay.remove();card.append(title,body,btn);overlay.appendChild(card);overlay.addEventListener('click',e=>{if(e.target===overlay)overlay.remove()});document.body.appendChild(overlay);};" +
            "function matchGoogleTab(el){const t=(el.innerText||el.textContent||'').replace(/\\s+/g,' ');return /گوگل|Google/i.test(t)&&/اتصال|حساب|ورود|متصل/i.test(t)}" +
            "function themeTabs(){document.querySelectorAll('button,[role=tab],.tab,.nav-link').forEach(el=>{if(!matchGoogleTab(el)||el.dataset.themeFixed==='1')return;let peer=el.parentElement&&Array.from(el.parentElement.children).find(x=>x!==el&&(x.matches('button,[role=tab],.tab,.nav-link')||x.querySelector('button,[role=tab],.tab,.nav-link')));if(peer&&!peer.matches('button,[role=tab],.tab,.nav-link'))peer=peer.querySelector('button,[role=tab],.tab,.nav-link');if(peer){const s=getComputedStyle(peer);el.style.setProperty('background',s.backgroundColor,'important');el.style.setProperty('background-color',s.backgroundColor,'important');el.style.setProperty('color',s.color,'important');el.style.setProperty('border-color',s.borderColor,'important');el.style.setProperty('box-shadow',s.boxShadow,'important');}else{el.style.setProperty('background','#252b38','important');el.style.setProperty('background-color','#252b38','important');el.style.setProperty('color','#e7eaf0','important');el.style.setProperty('border-color','rgba(255,255,255,.12)','important');}el.dataset.themeFixed='1';});}" +
            "themeTabs();new MutationObserver(themeTabs).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']});" +
            "})();";
        webView.evaluateJavascript(script, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESTORE_FILE) {
            if (pendingFileChooser != null) {
                Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            return;
        }
        if (requestCode == REQUEST_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("امکان ذخیره فایل وجود ندارد");
                    out.write(pendingSaveBytes);
                    android.widget.Toast.makeText(this, "فایل با موفقیت ذخیره شد.", android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "ذخیره فایل ناموفق بود: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveBytes = null;
        }
    }

    private class GoogleAuthBridge {
        @JavascriptInterface public void signIn() {
            runOnUiThread(MainActivity.this::beginGoogleSignIn);
        }
    }

    private void beginGoogleSignIn() {
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(
                getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        credentialManager.getCredentialAsync(
                this, request, new CancellationSignal(), getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(@NonNull GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }
                    @Override public void onError(@NonNull GetCredentialException e) {
                        sendAuthError(e.getMessage());
                    }
                });
    }

    private void handleGoogleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            sendAuthError("نوع اطلاعات ورود گوگل پشتیبانی نمی‌شود.");
            return;
        }
        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            webView.post(() -> webView.evaluateJavascript(
                    "window.onNativeGoogleSignInSuccess && window.onNativeGoogleSignInSuccess("
                            + JSONObject.quote(googleCredential.getIdToken()) + ");", null));
        } catch (Exception e) {
            sendAuthError(e.getMessage());
        }
    }

    private void sendAuthError(String message) {
        webView.post(() -> webView.evaluateJavascript(
                "window.onNativeGoogleSignInError && window.onNativeGoogleSignInError("
                        + JSONObject.quote(message == null ? "خطای ورود گوگل" : message) + ");", null));
    }

    private class AndroidBridge {
        @JavascriptInterface public void saveTextFile(String filename, String content, String mimeType) {
            runOnUiThread(() -> {
                try {
                    String safeName = (filename == null || filename.trim().isEmpty()) ? "backup.json" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                    pendingSaveBytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                    intent.putExtra(Intent.EXTRA_TITLE, safeName);
                    startActivityForResult(intent, REQUEST_SAVE_FILE);
                } catch (Exception e) {
                    pendingSaveBytes = null;
                    android.widget.Toast.makeText(MainActivity.this, "بازکردن محل ذخیره فایل ناموفق بود: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("گزارش روزانه", text == null ? "" : text));
            });
        }

        @JavascriptInterface public void shareText(String title, String text) {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, title == null ? "حساب سرویس‌های آب" : title);
                intent.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گزارش"));
            });
        }

        @JavascriptInterface public void shareImageBase64(String encoded, String filename) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "shared-images");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("ساخت پوشه تصویر ناموفق بود");
                    String safeName = (filename == null ? "factor.png" : filename).replaceAll("[^A-Za-z0-9._-]", "_");
                    if (!safeName.endsWith(".png")) safeName = "factor.png";
                    File file = new File(dir, safeName);
                    try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/png");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setClipData(ClipData.newUri(getContentResolver(), "فاکتور", uri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصویر فاکتور"));
                } catch (Exception e) {
                    android.widget.Toast.makeText(MainActivity.this, "اشتراک‌گذاری تصویر ناموفق بود: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void printHtml(String html) {
            runOnUiThread(() -> {
                WebView printView = new WebView(MainActivity.this);
                printView.getSettings().setJavaScriptEnabled(false);
                printView.setWebViewClient(new WebViewClient() {
                    private boolean started;
                    @Override public void onPageFinished(WebView view, String url) {
                        if (started) return;
                        started = true;
                        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("Factor-Water-Service");
                        PrintAttributes attributes = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution("pdf", "PDF", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build();
                        printManager.print("فاکتور سرویس آب", adapter, attributes);
                    }
                });
                String printable = html == null ? "" : html.replace("window.onload=()=>window.print()", "");
                printView.loadDataWithBaseURL(null, printable, "text/html", "UTF-8", null);
            });
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
