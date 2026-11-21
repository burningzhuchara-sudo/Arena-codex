package com.arenacodex.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val CHANNEL = "ac_overlay"
        var running = false; private set
        var panelOpen = false
        fun start(c: Context, rc: Int, data: Intent) {
            val i = Intent(c, OverlayService::class.java)
            i.putExtra("rc", rc); i.putExtra("data", data)
            ContextCompat.startForegroundService(c, i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, OverlayService::class.java)) }
    }

    private lateinit var wm: WindowManager
    private lateinit var projection: MediaProjection
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private lateinit var labels: LabelView
    private var trigger: LinearLayout? = null
    private var panel: ScrollView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var sw = 1080; private var sh = 1920; private var dpi = 420

    private val p get() = Store.prefs
    private fun f(k: String, d: Float) = p.getFloat(k, d)
    private fun setF(k: String, v: Float) { p.edit().putFloat(k, v).apply() }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (p.getBoolean("auto", true)) analyze()
            handler.postDelayed(this, f("interval", 1400f).toLong())
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.ctx = applicationContext
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        sw = m.widthPixels; sh = m.heightPixels; dpi = m.densityDpi

        val ch = NotificationChannel(CHANNEL, "Оверлей", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true).build()
        ServiceCompat.startForeground(
            this, 7, n,
            if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        val rc = i?.getIntExtra("rc", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = i?.getParcelableExtra("data")
        if (rc != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(rc, data)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { shutdown() }
        }, handler)

        val scale = 1280f / max(sw, sh)
        val cw = (sw * minOf(1f, scale)).roundToInt()
        val chh = (sh * minOf(1f, scale)).roundToInt()
        reader = ImageReader.newInstance(cw, chh, PixelFormat.RGBA_8888, 2)
        display = projection.createVirtualDisplay(
            "ac", cw, chh, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        addLabels(); addTrigger()
        running = true
        Ocr.init(this, p.getString("ocr_lang", "rus") ?: "rus")
        handler.postDelayed(tick, 900)
        return START_STICKY
    }

    private fun overlayParams(w: Int, h: Int, touchable: Boolean, gravity: Int): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
    }

    private fun addLabels() {
        labels = LabelView(this)
        wm.addView(labels, overlayParams(-1, -1, false, Gravity.TOP or Gravity.START))
    }

    private fun addTrigger() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6150E26"))
            setPadding(14, 10, 14, 10)
        }
        fun btn(t: String, col: String, act: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(Color.parseColor(col)); textSize = 15f
            setPadding(18, 10, 18, 10)
            setOnClickListener { act() }
        }
        bar.addView(btn("⚡", "#F5B942") { analyze() })
        bar.addView(btn("⚙", "#22D3EE") { togglePanel() })
        bar.addView(btn("✕", "#EF4444") { stop(this) })
        val lp = overlayParams(-2, -2, true, Gravity.TOP or Gravity.END)
        lp.y = 120
        trigger = bar
        wm.addView(bar, lp)

        var dx = 0; var dy = 0; var sx = 0f; var sy = 0f
        bar.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = lp.x; dy = lp.y; sx = e.rawX; sy = e.rawY; false }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = dx - (e.rawX - sx).toInt(); lp.y = dy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(v, lp); true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        if (panelOpen) { panel?.let { wm.removeView(it) }; panel = null; panelOpen = false; labels.calib = false; labels.invalidate(); return }
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2150E26"))
            setPadding(26, 22, 26, 26)
        }
        fun title(t: String) = TextView(ctx).apply {
            text = t; setTextColor(Color.parseColor("#EDE3D2")); textSize = 15f
            setPadding(0, 6, 0, 12); setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title("Калибровка оверлея · двигайте ползунки поверх игры"))

        fun slider(label: String, key: String, def: Float, min: Float, max: Float, scale: Float = 100f) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val cap = TextView(ctx).apply { setTextColor(Color.parseColor("#A79BC4")); textSize = 12f }
            val sb = SeekBar(ctx)
            sb.max = ((max - min) * scale).toInt()
            sb.progress = ((f(key, def) - min) * scale).toInt()
            cap.text = "$label: ${"%.2f".format(f(key, def))}"
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                    val value = min + v / scale
                    setF(key, value); cap.text = "$label: ${"%.2f".format(value)}"
                    labels.invalidate()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            row.addView(cap); row.addView(sb); root.addView(row)
        }

        slider("Ряд по вертикали (Y)", "row_y", 0.44f, 0f, 1f)
        slider("Высота области имён", "row_h", 0.07f, 0.01f, 0.4f)
        slider("Левый край", "row_x", 0.10f, 0f, 0.9f)
        slider("Ширина ряда", "row_w", 0.80f, 0.1f, 1f)
        slider("Зазор между картами", "gap", 0.02f, 0f, 0.2f)
        slider("Отступ подписи вниз", "label_dy", 0.09f, 0f, 0.5f)
        slider("Размер шрифта", "font", 13f, 8f, 28f, 1f)
        slider("Прозрачность фона", "alpha", 0.88f, 0.2f, 1f)
        slider("Интервал анализа, мс", "interval", 1400f, 400f, 5000f, 1f)

        val auto = Switch(ctx).apply {
            text = "Авто-анализ"; setTextColor(Color.parseColor("#EDE3D2"))
            isChecked = p.getBoolean("auto", true)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean("auto", v).apply() }
        }
        val showName = Switch(ctx).apply {
            text = "Показывать название"; setTextColor(Color.parseColor("#EDE3D2"))
            isChecked = p.getBoolean("show_name", true)
            setOnCheckedChangeListener { _, v -> p.edit().putBoolean("show_name", v).apply(); labels.invalidate() }
        }
        val guides = Switch(ctx).apply {
            text = "Показать рамки калибровки"; setTextColor(Color.parseColor("#EDE3D2"))
            isChecked = true
            setOnCheckedChangeListener { _, v -> labels.calib = v; labels.invalidate() }
        }
        root.addView(auto); root.addView(showName); root.addView(guides)

        val spin = Spinner(ctx)
        val langs = listOf("rus", "eng", "rus+eng")
        spin.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, langs)
        spin.setSelection(langs.indexOf(p.getString("ocr_lang", "rus")).coerceAtLeast(0))
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val l = langs[pos]; p.edit().putString("ocr_lang", l).apply()
                worker.execute { Ocr.init(ctx, l); handler.post { labels.invalidate() } }
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        root.addView(TextView(ctx).apply { text = "Язык OCR"; setTextColor(Color.parseColor("#A79BC4")); textSize = 12f })
        root.addView(spin)

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        fun action(t: String, bg: String, act: () -> Unit) = TextView(ctx).apply {
            text = t; setTextColor(Color.parseColor("#0A0713")); textSize = 14f
            setBackgroundColor(Color.parseColor(bg)); setPadding(26, 16, 26, 16)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 10 }
            setOnClickListener { act() }
        }
        row.addView(action("Анализ", "#F5B942") { analyze() })
        row.addView(action("Сброс", "#22D3EE") {
            listOf("row_y" to .44f, "row_h" to .07f, "row_x" to .10f, "row_w" to .80f,
                "gap" to .02f, "label_dy" to .09f, "font" to 13f, "alpha" to .88f, "interval" to 1400f)
                .forEach { setF(it.first, it.second) }
            togglePanel(); togglePanel()
        })
        row.addView(action("Готово", "#A3E635") { togglePanel() })
        root.addView(row)

        val sv = ScrollView(ctx)
        sv.addView(root)
        val lp = overlayParams(-1, (sh * 0.62f).toInt(), true, Gravity.BOTTOM)
        panel = sv; panelOpen = true; labels.calib = true
        wm.addView(sv, lp)
        labels.invalidate()
    }

    private fun analyze() {
        if (busy || !running) return
        val r = reader ?: return
        busy = true
        worker.execute {
            var img: Image? = null
            var bmp: Bitmap? = null
            try {
                if (!Store.prefs.getBoolean("ocr_ok", false)) {
                    handler.post {
                        labels.status = "OCR не проверен: в приложении нажмите «Проверить OCR»"
                        labels.invalidate()
                    }
                    busy = false; return@execute
                }
                img = r.acquireLatestImage()
                if (img == null) { busy = false; return@execute }
                bmp = toBitmap(img)
                val lang = p.getString("ocr_lang", "rus") ?: "rus"
                if (!Ocr.init(this, lang)) {
                    handler.post {
                        labels.status = if (Ocr.lastError.isEmpty()) "OCR не готов" else Ocr.lastError
                        labels.invalidate()
                    }
                    busy = false; return@execute
                }
                val cls = p.getString("cls", "mage") ?: "mage"
                val deck = Store.deck()
                val res = ArrayList<Slot>()
                val rects = rects(bmp.width, bmp.height)
                for (i in 0..2) {
                    val rc = rects[i]
                    val crop = Bitmap.createBitmap(bmp, rc.left, rc.top, rc.width(), rc.height())
                    val scaled = Bitmap.createScaledBitmap(crop, crop.width * 3, crop.height * 3, true)
                    val raw = Ocr.read(scaled)
                    crop.recycle(); scaled.recycle()
                    val hit = Store.match(raw, cls)
                    if (hit == null) res.add(Slot(null, raw, 0, 0, ""))
                    else {
                        val a = Store.advise(hit.first, cls, deck)
                        res.add(Slot(hit.first, raw, a.second, a.first, a.third))
                    }
                }
                val bestIdx = res.indices.filter { res[it].name != null }
                    .maxByOrNull { res[it].adj } ?: -1
                handler.post {
                    labels.slots = res; labels.best = bestIdx; labels.status = ""
                    labels.invalidate()
                }
            } catch (_: Throwable) {
            } finally {
                try { img?.close() } catch (_: Throwable) {}
                bmp?.recycle()
                busy = false
            }
        }
    }
    private fun rects(w: Int, h: Int): List<Rect> {
        val x = f("row_x", .10f) * w
        val y = f("row_y", .44f) * h
        val total = f("row_w", .80f) * w
        val hh = (f("row_h", .07f) * h).toInt().coerceAtLeast(8)
        val gap = f("gap", .02f) * w
        val cw = ((total - gap * 2) / 3).toInt().coerceAtLeast(8)
        return (0..2).map { i ->
            val l = (x + i * (cw + gap)).toInt().coerceIn(0, w - cw)
            val t = y.toInt().coerceIn(0, h - hh)
            Rect(l, t, l + cw, t + hh)
        }
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buf = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val padding = rowStride - pixelStride * image.width
        val wide = Bitmap.createBitmap(
            image.width + padding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(buf)
        val out = Bitmap.createBitmap(wide, 0, 0, image.width, image.height)
        if (out != wide) wide.recycle()
        return out
    }

    private fun shutdown() {
        running = false
        handler.removeCallbacks(tick)
        try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { trigger?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { wm.removeView(labels) } catch (_: Throwable) {}
        panel = null; trigger = null; panelOpen = false
        try { display?.release() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { projection.stop() } catch (_: Throwable) {}
        display = null; reader = null
        worker.execute { Ocr.release() }
        stopSelf()
    }

    override fun onDestroy() { shutdown(); super.onDestroy() }

    data class Slot(val name: String?, val raw: String, val adj: Int, val base: Int, val why: String)

    inner class LabelView(c: Context) : View(c) {
        var slots: List<Slot> = emptyList()
        var best: Int = -1
        var status: String = ""
        var calib = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun col(t: String) = when (t) {
            "S" -> "#F5B942"; "A" -> "#A3E635"; "B" -> "#22D3EE"; "C" -> "#8B5CF6"
            "D" -> "#F97316"; "E" -> "#EF4444"; else -> "#9F1239"
        }

        override fun onDraw(canvas: Canvas) {
            val rects = rects(width, height)
            val alpha = (f("alpha", .88f) * 255).toInt()
            val fs = f("font", 13f) * resources.displayMetrics.density
            val dy = f("label_dy", .09f) * height

            if (calib) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                paint.color = Color.parseColor("#22D3EE")
                rects.forEach { canvas.drawRect(it, paint) }
                paint.style = Paint.Style.FILL
            }

            if (status.isNotEmpty()) {
                tp.textSize = fs
                tp.color = Color.parseColor("#FED7AA")
                paint.color = Color.parseColor("#E6150E26")
                canvas.drawRoundRect(20f, 20f, width - 20f, 20f + fs * 2.2f, 14f, 14f, paint)
                canvas.drawText(status, 34f, 20f + fs * 1.5f, tp)
            }

            slots.forEachIndexed { i, s ->
                val r = rects[i]
                val top = r.bottom + dy
                val w = r.width().toFloat()
                val lines = ArrayList<String>()
                if (s.name == null) {
                    lines.add("не распознано")
                    if (s.raw.isNotBlank()) lines.add(s.raw.take(22))
                } else {
                    val t = Store.tier(s.adj)
                    lines.add("$t · ${s.adj}" + (if (i == best) "  ★ БЕРИ" else ""))
                    if (p.getBoolean("show_name", true)) lines.add(s.name.take(26))
                    if (s.why.isNotBlank()) lines.add(s.why.take(28))
                }
                val boxH = fs * (lines.size * 1.35f + 0.7f)
                val tierColor = if (s.name == null) "#7C6FA0" else col(Store.tier(s.adj))
                paint.color = Color.parseColor("#150E26")
                paint.alpha = alpha
                canvas.drawRoundRect(r.left.toFloat(), top, r.left + w, top + boxH, 12f, 12f, paint)
                paint.alpha = 255
                paint.style = Paint.Style.STROKE; paint.strokeWidth = if (i == best) 4f else 2f
                paint.color = Color.parseColor(tierColor)
                canvas.drawRoundRect(r.left.toFloat(), top, r.left + w, top + boxH, 12f, 12f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawRect(r.left.toFloat(), top, r.left + 8f, top + boxH, paint)

                lines.forEachIndexed { li, line ->
                    tp.textSize = if (li == 0) fs * 1.12f else fs * 0.86f
                    tp.isFakeBoldText = li == 0
                    tp.color = Color.parseColor(if (li == 0) tierColor else "#EDE3D2")
                    canvas.drawText(line, r.left + 16f, top + fs * (1.25f + li * 1.3f), tp)
                }
            }
        }
    }
}
