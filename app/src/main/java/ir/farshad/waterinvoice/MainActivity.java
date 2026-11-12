package ir.farshad.waterinvoice;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
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
import android.provider.MediaStore;

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
    private static final int REQUEST_INVOICE_CAMERA = 4103;
    private static final int REQUEST_INVOICE_GALLERY = 4104;
    private Uri pendingCameraUri;
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
            @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                android.widget.TextView title = new android.widget.TextView(MainActivity.this);
                title.setText("همگام‌سازی اطلاعات");
                title.setTextColor(android.graphics.Color.WHITE);
                title.setTextSize(18);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                title.setGravity(android.view.Gravity.RIGHT);
                title.setPadding(26, 24, 26, 8);

                android.widget.TextView body = new android.widget.TextView(MainActivity.this);
                body.setText(message == null ? "" : message);
                body.setTextColor(android.graphics.Color.rgb(220, 224, 233));
                body.setTextSize(15);
                body.setGravity(android.view.Gravity.RIGHT);
                body.setTextDirection(android.view.View.TEXT_DIRECTION_RTL);
                body.setLineSpacing(5, 1.0f);
                body.setPadding(26, 12, 26, 22);

                final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setCustomTitle(title)
                        .setView(body)
                        .setPositiveButton("بازیابی از ابر", (d, which) -> result.confirm())
                        .setNegativeButton("نگه‌داشتن اطلاعات گوشی", (d, which) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .create();
                dialog.setOnShowListener(d -> {
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(android.graphics.Color.rgb(23, 27, 37));
                    bg.setCornerRadius(24 * getResources().getDisplayMetrics().density);
                    bg.setStroke((int)(1 * getResources().getDisplayMetrics().density), android.graphics.Color.rgb(65, 72, 88));
                    if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(bg);
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.rgb(132, 190, 255));
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.rgb(205, 211, 222));
                });
                dialog.show();
                return true;
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                boolean wantsImage = false;
                String[] accepted = params.getAcceptTypes();
                if (accepted != null) {
                    for (String type : accepted) {
                        if (type != null && (type.toLowerCase().contains("image/") || type.trim().equals("*/*"))) {
                            wantsImage = true;
                            break;
                        }
                    }
                }
                try {
                    if (wantsImage) {
                        if (params.isCaptureEnabled()) {
                            launchInvoiceCamera();
                        } else {
                            Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            gallery.setType("image/*");
                            gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivityForResult(gallery, REQUEST_INVOICE_GALLERY);
                        }
                    } else {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "application/octet-stream"});
                        startActivityForResult(intent, REQUEST_RESTORE_FILE);
                    }
                } catch (Exception e) {
                    pendingFileChooser = null;
                    callback.onReceiveValue(null);
                    android.widget.Toast.makeText(MainActivity.this, "بازکردن دوربین یا گالری ناموفق بود: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
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
            "  if (await window.appConfirm('برای این حساب، پشتیبان ابری پیدا شد. اطلاعات ابری جایگزین اطلاعات فعلی این گوشی شود؟\\nتأیید = بازیابی از ابر\\nلغو = نگه‌داشتن اطلاعات گوشی و بارگذاری آن به ابر')) {" +
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

        webView.evaluateJavascript(script, null);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESTORE_FILE || requestCode == REQUEST_INVOICE_CAMERA || requestCode == REQUEST_INVOICE_GALLERY) {
            if (pendingFileChooser != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK) {
                    if (requestCode == REQUEST_INVOICE_CAMERA) {
                        if (pendingCameraUri != null) result = new Uri[]{pendingCameraUri};
                    } else if (data != null) {
                        if (data.getClipData() != null) {
                            ClipData clip = data.getClipData();
                            result = new Uri[clip.getItemCount()];
                            for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
                        } else if (data.getData() != null) {
                            result = new Uri[]{data.getData()};
                        }
                    }
                }
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            if (requestCode == REQUEST_INVOICE_CAMERA && (resultCode != RESULT_OK)) pendingCameraUri = null;
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

    private void launchInvoiceCamera() throws Exception {
        File folder = new File(getCacheDir(), "shared-images");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("ساخت پوشه موقت عکس ناموفق بود");
        File photo = File.createTempFile("invoice_", ".jpg", folder);
        pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
        Intent camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (camera.resolveActivity(getPackageManager()) == null) throw new IllegalStateException("برنامه دوربین در گوشی پیدا نشد");
        startActivityForResult(camera, REQUEST_INVOICE_CAMERA);
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
                    pendingSaveBytes = Base64.decode(encoded, Base64.DEFAULT);
                    String safeName = (filename == null || filename.trim().isEmpty() ? "gozaresh.png" : filename).replaceAll("[^A-Za-z0-9._-]", "_");
                    if (!safeName.toLowerCase().endsWith(".png")) safeName += ".png";
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/png");
                    intent.putExtra(Intent.EXTRA_TITLE, safeName);
                    startActivityForResult(intent, REQUEST_SAVE_FILE);
                } catch (Exception e) {
                    pendingSaveBytes = null;
                    android.widget.Toast.makeText(MainActivity.this, "ذخیره تصویر ناموفق بود: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public boolean saveImageToGalleryBase64(String encoded, String filename) {
            Uri imageUri = null;
            try {
                byte[] imageBytes = Base64.decode(encoded, Base64.DEFAULT);
                String normalizedName = (filename == null || filename.trim().isEmpty() ? "gozaresh.png" : filename)
                        .replaceAll("[^A-Za-z0-9._-]", "_");
                final String safeName = normalizedName.toLowerCase().endsWith(".png")
                        ? normalizedName : normalizedName + ".png";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/دفتر هوشمند فرشاد");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    imageUri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri == null) throw new IllegalStateException("ساخت فایل در گالری ناموفق بود");
                    try (OutputStream output = getContentResolver().openOutputStream(imageUri)) {
                        if (output == null) throw new IllegalStateException("دسترسی نوشتن به گالری برقرار نشد");
                        output.write(imageBytes);
                        output.flush();
                    }
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(imageUri, ready, null, null);
                    runOnUiThread(() -> android.widget.Toast.makeText(
                            MainActivity.this, "تصویر گزارش در گالری ذخیره شد.",
                            android.widget.Toast.LENGTH_LONG).show());
                    return true;
                }

                // Android versions before 10 use the system save-location picker.
                pendingSaveBytes = imageBytes;
                runOnUiThread(() -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/png");
                        intent.putExtra(Intent.EXTRA_TITLE, safeName);
                        startActivityForResult(intent, REQUEST_SAVE_FILE);
                    } catch (Exception e) {
                        pendingSaveBytes = null;
                        android.widget.Toast.makeText(MainActivity.this,
                                "بازکردن محل ذخیره تصویر ناموفق بود: " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
                return true;
            } catch (Exception e) {
                if (imageUri != null) {
                    try { getContentResolver().delete(imageUri, null, null); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> android.widget.Toast.makeText(
                        MainActivity.this, "ذخیره تصویر در گالری ناموفق بود: " + e.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show());
                return false;
            }
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
