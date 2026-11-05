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
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private CredentialManager credentialManager;

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
        webView.setWebChromeClient(new WebChromeClient());
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
            "window.googleSignIn = async function() {" +
            " try {" +
            "  if (typeof window.completeNativeGoogleSignIn !== 'function') throw new Error('بخش ورود گوگل بارگذاری نشده است.');" +
            "  const idToken = await new Promise((resolve, reject) => {" +
            "   window.onNativeGoogleSignInSuccess = resolve;" +
            "   window.onNativeGoogleSignInError = reject;" +
            "   window.AndroidGoogleAuth.signIn();" +
            "  });" +
            "  await window.completeNativeGoogleSignIn(idToken);" +
            " } catch(e) { alert('ورود با گوگل انجام نشد. ' + (e && e.message ? e.message : e)); }" +
            "};";
        webView.evaluateJavascript(script, null);
    }

    private class GoogleAuthBridge {
        @JavascriptInterface public void signIn() {
            runOnUiThread(MainActivity.this::beginGoogleSignIn);
        }
    }

    private void beginGoogleSignIn() {
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
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
