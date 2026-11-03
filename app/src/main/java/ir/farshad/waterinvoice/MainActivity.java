package ir.farshad.waterinvoice;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private CredentialManager credentialManager;
    private FirebaseAuth firebaseAuth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        credentialManager = CredentialManager.create(this);
        firebaseAuth = FirebaseAuth.getInstance();
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

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
            "  if (!window.fbAuth || !window.fbDb) throw new Error('Firebase هنوز آماده نیست.');" +
            "  const idToken = await new Promise((resolve, reject) => {" +
            "   window.onNativeGoogleSignInSuccess = resolve;" +
            "   window.onNativeGoogleSignInError = reject;" +
            "   window.AndroidGoogleAuth.signIn();" +
            "  });" +
            "  const credential = firebase.auth.GoogleAuthProvider.credential(idToken);" +
            "  await fbAuth.signInWithCredential(credential);" +
            "  const u = fbAuth.currentUser;" +
            "  if (!u) throw new Error('ورود انجام نشد.');" +
            "  const ref = fbDb.collection('users').doc(u.uid);" +
            "  const snap = await ref.get();" +
            "  if (snap.exists) {" +
            "   if (confirm('برای این حساب، پشتیبان ابری پیدا شد. اطلاعات ابری جایگزین اطلاعات فعلی این گوشی شود؟\\nتأیید = بازیابی از ابر\\nلغو = نگه‌داشتن اطلاعات گوشی و بارگذاری آن به ابر')) {" +
            "    const d = snap.data(); records = Array.isArray(d.records) ? d.records : [];" +
            "    localStorage.setItem(STORE, JSON.stringify(records));" +
            "    if (Array.isArray(d.customers)) localStorage.setItem('waterServiceCustomers_v1', JSON.stringify(d.customers));" +
            "    if (d.customerPhones && typeof d.customerPhones === 'object') {" +
            "     customerPhones = d.customerPhones; localStorage.setItem('waterServiceCustomerPhones_v1', JSON.stringify(customerPhones));" +
            "    } render(); alert('اطلاعات ابری بازیابی شد.');" +
            "   } else { await syncCloud(true); }" +
            "  } else { await syncCloud(true); alert('اتصال انجام شد و اطلاعات فعلی در حساب گوگل ذخیره شد.'); }" +
            " } catch(e) { setCloudStatus('خطا: ' + e.message); alert('اتصال انجام نشد.\\n' + e.message); }" +
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
            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
            firebaseAuth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            webView.evaluateJavascript(
                                    "window.onNativeGoogleSignInSuccess && window.onNativeGoogleSignInSuccess("
                                            + JSONObject.quote(googleCredential.getIdToken()) + ");", null);
                        } else {
                            sendAuthError(task.getException() == null ? "ورود به Firebase ناموفق بود."
                                    : task.getException().getMessage());
                        }
                    });
        } catch (Exception e) {
            sendAuthError(e.getMessage());
        }
    }

    private void sendAuthError(String message) {
        webView.post(() -> webView.evaluateJavascript(
                "window.onNativeGoogleSignInError && window.onNativeGoogleSignInError("
                        + JSONObject.quote(message == null ? "خطای ورود گوگل" : message) + ");", null));
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
