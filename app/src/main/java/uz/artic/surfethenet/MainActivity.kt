package uz.artic.surfethenet

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        // Surfer'ning applicationId'sini shu yerga yozing.
        const val SURFER_PACKAGE = "uz.artic.browser"
        const val HOME = "file:///android_asset/home.html"
        const val REQ_VOICE = 42
        const val REQ_ACCOUNT = 43
        // Faqat shu serverlardan ma'lumot olish mumkin (vidjetlar va yangiliklar)
        val FETCH_HOSTS = setOf("open.er-api.com", "api.open-meteo.com", "news.google.com")
    }

    private lateinit var web: WebView
    @Volatile private var onHome = false
    private var openProfile = false   // Surfer'dan 'Profilni tahrirlash' bosilganda

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#202124")
        window.navigationBarColor = Color.parseColor("#202124")

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#202124"))
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                onHome = url.startsWith("file:///android_asset/")
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (openProfile && url.startsWith("file:///android_asset/")) {
                    openProfile = false
                    view.evaluateJavascript("window.profDlg && profDlg()", null)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val uri = request.url
                val host = uri.host ?: return false

                // google.com/url?q=REAL_SAYT yo'naltirishi
                if (isGoogle(host) && uri.path == "/url") {
                    val real = uri.getQueryParameter("q") ?: uri.getQueryParameter("url")
                    if (real != null && real.startsWith("http")) {
                        openInSurfer(real)
                        return true
                    }
                }
                if (isGoogle(host)) return false   // Google sahifalari shu ilovada qoladi
                openInSurfer(uri.toString())       // qolgan hamma sayt -> Surfer
                return true
            }
        }
        openProfile = intent?.getBooleanExtra("profile", false) == true
        web.loadUrl(HOME)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("profile", false) == true) {
            openProfile = true
            web.loadUrl(HOME)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun open(url: String) {
            if (!onHome) return
            if (url.startsWith("http://") || url.startsWith("https://")) runOnUiThread { openInSurfer(url) }
        }

        @JavascriptInterface
        fun voice() {
            if (!onHome) return
            runOnUiThread {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Gapiring...")
                }
                try { startActivityForResult(i, REQ_VOICE) } catch (_: ActivityNotFoundException) {}
            }
        }


        @JavascriptInterface
        fun getProfile(): String = if (onHome) ProfileStore.json(this@MainActivity) else ""

        @JavascriptInterface
        fun saveProfile(json: String) {
            if (onHome) ProfileStore.save(this@MainActivity, json)
        }

        // Telefondagi Google akkauntlar ro'yxatidan tanlash (tizim oynasi)
        @JavascriptInterface
        fun pickAccount() {
            if (!onHome) return
            runOnUiThread {
                @Suppress("DEPRECATION")
                val i = AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), null, null, null, null
                )
                try { startActivityForResult(i, REQ_ACCOUNT) } catch (_: Exception) {}
            }
        }

        // JS tomondan CORS'siz so'rov: Android.fetch(url, callbackId)
        @JavascriptInterface
        fun fetch(url: String, id: String) {
            if (!onHome) return
            thread {
                var ok = false
                var body = ""
                try {
                    val u = URL(url)
                    require(u.protocol == "https" && u.host in FETCH_HOSTS)
                    val c = u.openConnection() as HttpURLConnection
                    c.connectTimeout = 10000
                    c.readTimeout = 10000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SurfeTheNet")
                    body = c.inputStream.bufferedReader().use { it.readText() }
                    ok = true
                } catch (e: Exception) {
                    body = e.message ?: "error"
                }
                val js = "__cb(${JSONObject.quote(id)},$ok,${JSONObject.quote(body)})"
                runOnUiThread { web.evaluateJavascript(js, null) }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_VOICE -> {
                val q = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
                web.loadUrl("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
            }
            REQ_ACCOUNT -> {
                val email = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return
                web.evaluateJavascript("window.__account && __account(${JSONObject.quote(email)})", null)
            }
        }
    }

    private fun isGoogle(host: String): Boolean {
        val h = host.lowercase()
        return h == "google.com" || h.endsWith(".google.com") ||
            h.contains(".google.") || h.startsWith("google.") ||
            h.endsWith(".gstatic.com") || h.endsWith(".googleusercontent.com")
    }

    private fun openInSurfer(url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(SURFER_PACKAGE)
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Ochish"))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!onHome && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
