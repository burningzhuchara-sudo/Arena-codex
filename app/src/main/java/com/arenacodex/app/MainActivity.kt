package com.arenacodex.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.*
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingProjection = false

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK && r.data != null) {
                OverlayService.start(this, r.resultCode, r.data!!)
                push("overlay", "true")
                toast("Оверлей включён — переключитесь на Hearthstone")
            } else toast("Захват экрана не разрешён")
            pendingProjection = false
        }

    private val overlayPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) requestProjection()
            else toast("Разрешение «Поверх других приложений» не выдано")
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.ctx = applicationContext
        Store.loadCache()

        if (Build.VERSION.SDK_INT >= 33)
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
        }
        WebView.setWebContentsDebuggingEnabled(true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(req.url)
            override fun onPageFinished(v: WebView, url: String) { pushAll() }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        if (Store.tiers.length() == 0) scope.launch { silentRefresh() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun js(code: String) = web.post { web.evaluateJavascript(code, null) }

    private fun push(key: String, value: String) =
        js("window.AC && AC.nativeUpdate('$key', ${JSONObjectQuote(value)});")

    private fun JSONObjectQuote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun pushAll() {
        js("window.AC && AC.setData(${Store.payload()});")
        js("window.AC && AC.setDeck(${Store.prefs.getString("deck", "[]")});")
        js("window.AC && AC.setClass('${Store.prefs.getString("cls", "mage")}');")
    }

    private suspend fun silentRefresh() {
        try { Store.refresh(); pushAll() } catch (_: Exception) {}
    }

    private fun requestProjection() {
        if (pendingProjection) return
        pendingProjection = true
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    inner class Bridge {
        @JavascriptInterface fun payload(): String = Store.payload()
        @JavascriptInterface fun deck(): String = Store.prefs.getString("deck", "[]") ?: "[]"
        @JavascriptInterface fun cls(): String = Store.prefs.getString("cls", "mage") ?: "mage"
        @JavascriptInterface fun overlayRunning(): Boolean = OverlayService.running
        @JavascriptInterface fun ocrReady(): Boolean = Ocr.ready()
        @JavascriptInterface fun ocrHas(l: String): Boolean = Ocr.has(this@MainActivity, l)

        @JavascriptInterface fun saveDeck(json: String) {
            Store.prefs.edit().putString("deck", json).apply()
        }
        @JavascriptInterface fun saveClass(c: String) {
            Store.prefs.edit().putString("cls", c).apply()
        }
        @JavascriptInterface fun setPref(k: String, v: String) {
            Store.prefs.edit().putString(k, v).apply()
        }

        @JavascriptInterface fun refresh() {
            scope.launch {
                js("window.AC && AC.setStatus('Загрузка полного пула…');")
                try {
                    val n = Store.refresh()
                    pushAll()
                    js("window.AC && AC.setStatus('Загружено $n оценок');")
                } catch (e: Exception) {
                    js("window.AC && AC.setStatus('Ошибка загрузки: ${e.message?.replace("'", "") ?: ""}');")
                }
            }
        }

        @JavascriptInterface fun downloadOcr(lang: String) {
            scope.launch {
                js("window.AC && AC.setStatus('Скачивание OCR-модели $lang…');")
                try {
                    Ocr.download(this@MainActivity, lang) { pct ->
                        js("window.AC && AC.setStatus('OCR $lang: $pct%');")
                    }
                    Ocr.init(this@MainActivity, lang)
                    Store.prefs.edit().putString("ocr_lang", lang).apply()
                    js("window.AC && AC.setStatus('OCR $lang готов');")
                    pushAll()
                } catch (e: Exception) {
                    js("window.AC && AC.setStatus('Не удалось скачать OCR: ${e.message?.replace("'", "") ?: ""}');")
                }
            }
        }

        @JavascriptInterface fun startOverlay() {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    overlayPermLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                    )
                } else requestProjection()
            }
        }

        @JavascriptInterface fun stopOverlay() {
            runOnUiThread { OverlayService.stop(this@MainActivity); push("overlay", "false") }
        }

        @JavascriptInterface fun calibrate() {
            runOnUiThread {
                if (!OverlayService.running) { toast("Сначала включите оверлей"); return@runOnUiThread }
                val i = Intent(this@MainActivity, OverlayService::class.java)
                i.action = "calib"
                // панель открывается кнопкой ⚙ на плавающей панели поверх игры
                toast("Нажмите ⚙ на плавающей панели поверх игры")
            }
        }

        @JavascriptInterface fun importData(text: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    val doc = org.jsoup.Jsoup.parse(text)
                    val out = org.json.JSONObject(); var count = 0
                    for (sec in doc.select("section.tab.tierlist")) {
                        val id = sec.id(); if (id.isEmpty() || id == "change-log") continue
                        val arr = JSONArray()
                        for (dl in sec.select("dl.card")) {
                            val dt = dl.selectFirst("dt") ?: continue
                            val dd = dl.selectFirst("dd") ?: continue
                            val name = dt.text().replace("Новый", "").trim()
                            val sc = Regex("\\d+").find(dd.text())?.value?.toIntOrNull() ?: continue
                            arr.put(JSONArray().put(name).put(sc)); count++
                        }
                        if (arr.length() > 0) out.put(id, arr)
                    }
                    if (count > 200) {
                        Store.tiers = out; Store.live = true; Store.stamp = "импорт"
                        Store.saveCache()
                        withContext(Dispatchers.Main) { pushAll() }
                        js("window.AC && AC.setStatus('Импортировано $count оценок');")
                    } else js("window.AC && AC.setStatus('В тексте не найдено карт');")
                } catch (e: Exception) {
                    js("window.AC && AC.setStatus('Импорт не удался');")
                }
            }
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onResume() { super.onResume(); pushAll() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
