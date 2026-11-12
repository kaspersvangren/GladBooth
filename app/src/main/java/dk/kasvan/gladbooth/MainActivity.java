package dk.kasvan.gladbooth;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GladBooth er en tynd skal om index.html (den samme side, som kan åbnes i en browser).
 * Skallen står for tre ting, en browser ikke kan: rigtig fuld skærm, kameratilladelsen
 * og at skrive de gemte billeder direkte til telefonens billedmappe uden dialoger.
 */
public class MainActivity extends Activity {
    private static final String TAG = "GladBooth";
    // Siden ligger i appen, men indlæses under en https-adresse, fordi kameraet kun virker på sikre adresser.
    private static final String HOST = "gladbooth.invalid";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int ASK_CAMERA = 1;

    private WebView web;
    private PermissionRequest waitingRequest;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(8, 28, 21));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);   // ingen tekstmarkering ved langt tryk

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // lyde og kamera må starte uden et tryk
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);                              // følg ikke telefonens skriftstørrelse

        web.addJavascriptInterface(new Bridge(), "GladBooth");
        web.setWebViewClient(new Pages());
        web.setWebChromeClient(new Chrome());
        setContentView(web);
        hideBars();
        web.loadUrl(START);
    }

    /** Fuld skærm: ingen statuslinje og ingen navigationsknapper. Et swipe fra kanten viser dem kort. */
    private void hideBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideBars();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        web.destroy();
        super.onDestroy();
    }

    /** Tilbage-knappen lukker ikke photoboothen. */
    @Override
    public void onBackPressed() {
    }

    /** Leverer den indbyggede side og afviser alt andet, så intet hentes fra nettet. */
    private final class Pages extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            String scheme = u.getScheme();
            // data: og blob: (skrifttyper, lyde, billeder inde i siden) skal have lov at passere urørt
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            if (HOST.equals(u.getHost())) {
                String path = u.getPath();
                if (path == null || path.equals("/") || path.equals("/index.html")) {
                    try {
                        return new WebResourceResponse("text/html", "utf-8", getAssets().open("index.html"));
                    } catch (IOException e) {
                        Log.e(TAG, "index.html mangler i appen", e);
                    }
                }
            }
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !HOST.equals(request.getUrl().getHost());   // forlad aldrig den indbyggede side
        }
    }

    /** Kameraet: siden beder om det, og appen spørger Android én gang. */
    private final class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            boolean wantsCamera = false;
            for (String r : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
            }
            if (!wantsCamera) {
                request.deny();
                return;
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                return;
            }
            if (waitingRequest != null) waitingRequest.deny();
            waitingRequest = request;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, ASK_CAMERA);
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (request == waitingRequest) waitingRequest = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != ASK_CAMERA || waitingRequest == null) return;
        PermissionRequest r = waitingRequest;
        waitingRequest = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            r.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            r.deny();
        }
        hideBars();
    }

    /** Det, siden kan bede appen om. */
    private final class Bridge {
        /**
         * Skriver et billede til Billeder/<album>. Siden får svar via window.__gbSaved(id, ok),
         * først når filen ligger på disken.
         */
        @JavascriptInterface
        public void savePhoto(final int id, final String album, final String name, final String base64) {
            io.execute(() -> {
                boolean ok = false;
                try {
                    ok = writeImage(clean(album), clean(name), Base64.decode(base64, Base64.DEFAULT));
                } catch (Throwable t) {
                    Log.e(TAG, "Kunne ikke gemme " + name, t);
                }
                final String js = "window.__gbSaved&&window.__gbSaved(" + id + "," + ok + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            });
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9 ._-]", "").replace("..", "").trim();
    }

    private boolean writeImage(String album, String name, byte[] data) throws IOException {
        if (album.isEmpty() || name.isEmpty() || data.length == 0) return false;
        ContentResolver cr = getContentResolver();
        Uri images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String folder = Environment.DIRECTORY_PICTURES + "/" + album;

        // Ligger filen der allerede (et afbrudt forsøg, der bliver gentaget), er vi færdige
        try (Cursor c = cr.query(images, new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DISPLAY_NAME + "=? AND " + MediaStore.Images.Media.RELATIVE_PATH + "=?",
                new String[]{name, folder + "/"}, null)) {
            if (c != null && c.moveToFirst()) return true;
        } catch (Exception e) {
            Log.w(TAG, "Kunne ikke slå " + name + " op", e);
        }

        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, folder);
        v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(images, v);
        if (uri == null) return false;

        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w")) {
            if (pfd == null) throw new IOException("Kunne ikke åbne filen");
            try (FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
                out.write(data);
                out.flush();
                out.getFD().sync();   // helt ned på disken, før vi melder klar
            }
        } catch (IOException | RuntimeException e) {
            cr.delete(uri, null, null);
            throw e;
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        return true;
    }
}
