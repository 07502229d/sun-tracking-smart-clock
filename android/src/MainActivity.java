package __PACKAGE__;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_GEO = 42;
    private WebView web;
    // Geolocation prompt awaiting the runtime-permission result. The WebView
    // callback must NOT be invoked until onRequestPermissionsResult arrives;
    // answering it early (v1.0 behaviour) made the page's first GPS request
    // fail because the system permission was not actually granted yet.
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    /**
     * Native download bridge exposed to the page as window.SunBridge.
     * The web app calls SunBridge.saveFile(filename, mimeType, base64) for
     * CSV/PDF saves; without this bridge WebView silently swallows blob
     * downloads. Writes go to the public Downloads folder (MediaStore on
     * API 29+, legacy public Downloads dir below) off the UI thread.
     */
    public class SunBridge {
        /**
         * Print bridge (v1.6): the page's Save action is a print flow first.
         * SunBridge.print() opens the Android system print dialog for the
         * WebView; the page's print stylesheet makes the output exactly the
         * annual report sheet on one A4 portrait page, and the user picks
         * "Save as PDF" in the dialog. Must run on the UI thread.
         */
        @JavascriptInterface
        public void print() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        String jobName = "Sun Annual Report";
                        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(jobName);
                        pm.print(jobName, adapter, new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build());
                    } catch (Exception e) {
                        showToast("列印失敗：" + e.getMessage());
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String filename, String mimeType, String base64) {
            final String name = sanitizeFilename(filename);
            final String mime = (mimeType == null || mimeType.length() == 0)
                    ? "application/octet-stream" : mimeType;
            String b64 = base64 == null ? "" : base64.trim();
            if (b64.startsWith("data:")) {
                int comma = b64.indexOf(',');
                if (comma >= 0) b64 = b64.substring(comma + 1);
            }
            final String payload = b64;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = Base64.decode(payload, Base64.DEFAULT);
                        String savedAs = writeToDownloads(name, mime, bytes);
                        showToast("已儲存到「下載」：" + savedAs);
                    } catch (Exception e) {
                        showToast("儲存失敗：" + e.getMessage());
                    }
                }
            }).start();
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) name = "";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.length() == 0) name = "download.bin";
        return name;
    }

    private String writeToDownloads(String name, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("MediaStore insert failed");
            OutputStream out = cr.openOutputStream(uri);
            if (out == null) throw new Exception("openOutputStream failed");
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return name;
        }
        // Legacy path (API 26-28): needs WRITE_EXTERNAL_STORAGE at runtime.
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new Exception("未取得儲存權限");
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        File f = uniqueFile(dir, name);
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return f.getName();
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, name);
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new SunBridge(), "SunBridge");
        // Legacy Downloads writes (API < 29) need the storage permission;
        // ask once up front so SunBridge saves do not fail later.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 43);
        }
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file://") || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "無法開啟連結", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    // Permission already held: answer at once.
                    callback.invoke(origin, true, false);
                    return;
                }
                // Not granted yet: hold the callback and wait for the runtime
                // permission result instead of answering optimistically.
                boolean alreadyPending = pendingGeoCallback != null;
                if (alreadyPending) {
                    // A second prompt arrived while one is pending: answer the
                    // superseded callback (denied) so it is never leaked, and
                    // let the in-flight system dialog resolve the newest one.
                    pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
                }
                pendingGeoCallback = callback;
                pendingGeoOrigin = origin;
                if (!alreadyPending) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
                }
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (url.startsWith("http")) {
                    try {
                        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                        req.setMimeType(mimeType);
                        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).getLastPathSegment());
                        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                        Toast.makeText(MainActivity.this, "開始下載", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "下載失敗", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_GEO) {
            // Other requests (e.g. legacy storage, code 43) need no action:
            // SunBridge re-checks the permission at save time.
            return;
        }
        // Granted if either fine or coarse location was approved (grantResults
        // is empty if the request was interrupted, which counts as denied).
        boolean granted = false;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (pendingGeoCallback != null) {
            GeolocationPermissions.Callback cb = pendingGeoCallback;
            String origin = pendingGeoOrigin;
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            cb.invoke(origin, granted, false);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
