package com.arenacodex.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object Store {
    lateinit var ctx: Context
    val prefs: SharedPreferences by lazy { ctx.getSharedPreferences("ac", Context.MODE_PRIVATE) }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    var tiers: JSONObject = JSONObject()
    var meta: JSONObject = JSONObject()
    var stamp: String = "встроенный срез"
    var live: Boolean = false

    fun norm(s: String): String =
        s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun cacheFile() = File(ctx.filesDir, "cache.json")

    fun loadCache() {
        try {
            val f = cacheFile()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            tiers = o.optJSONObject("tiers") ?: JSONObject()
            meta = o.optJSONObject("meta") ?: JSONObject()
            stamp = o.optString("stamp", stamp)
            live = o.optBoolean("live", false)
        } catch (_: Exception) {}
    }

    fun saveCache() {
        try {
            val o = JSONObject()
            o.put("tiers", tiers); o.put("meta", meta)
            o.put("stamp", stamp); o.put("live", live)
            cacheFile().writeText(o.toString())
        } catch (_: Exception) {}
    }

    fun payload(): String {
        val o = JSONObject()
        o.put("tiers", tiers); o.put("meta", meta)
        o.put("stamp", stamp); o.put("live", live)
        o.put("overlay", OverlayService.running)
        o.put("ocr", Ocr.ready())
        o.put("ocrErr", Ocr.lastError)
        o.put("ocrEngine", Ocr.engineInfo)
        o.put("lang", prefs.getString("ocr_lang", "rus"))
        return o.toString()
    }

    fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) ArenaCodex/1.0")
            .header("Accept-Language", "ru,en;q=0.8")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
            return r.body?.string() ?: ""
        }
    }

    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        val html = get("https://heartharena.com/ru/tierlist")
        val doc = Jsoup.parse(html)
        val out = JSONObject()
        var count = 0
        for (sec in doc.select("section.tab.tierlist")) {
            val id = sec.id()
            if (id.isEmpty() || id == "change-log") continue
            val arr = JSONArray()
            for (dl in sec.select("dl.card")) {
                val dt = dl.selectFirst("dt") ?: continue
                val dd = dl.selectFirst("dd") ?: continue
                val name = dt.text().replace("Новый", "").trim()
                val score = Regex("\\d+").find(dd.text())?.value?.toIntOrNull() ?: continue
                if (name.length < 2) continue
                arr.put(JSONArray().put(name).put(score)); count++
            }
            if (arr.length() > 0) out.put(id, arr)
        }
        if (count < 200) throw IllegalStateException("Источник вернул слишком мало карт")
        tiers = out
        try {
            val cards = JSONArray(get("https://api.hearthstonejson.com/v1/latest/ruRU/cards.collectible.json"))
            val m = JSONObject()
            for (i in 0 until cards.length()) {
                val c = cards.getJSONObject(i)
                val e = JSONObject()
                e.put("n", c.optString("name"))
                e.put("c", if (c.has("cost")) c.optInt("cost") else -1)
                e.put("t", c.optString("type"))
                e.put("r", c.optString("race"))
                e.put("q", c.optString("rarity"))
                e.put("k", c.optString("cardClass"))
                e.put("s", c.optString("spellSchool"))
                e.put("id", c.optString("id"))
                e.put("m", c.optJSONArray("mechanics") ?: JSONArray())
                e.put("x", c.optString("text")
                    .replace(Regex("\\[x]|</?[bi]>|[$#&]"), "")
                    .replace(Regex("\\s+"), " ").take(300))
                m.put(norm(c.optString("name")), e)
            }
            meta = m
        } catch (_: Exception) {}
        live = true
        stamp = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale("ru")).format(java.util.Date())
        saveCache()
        count
    }

    fun pool(cls: String): List<Pair<String, Int>> {
        val arr = tiers.optJSONArray(cls) ?: return emptyList()
        val out = ArrayList<Pair<String, Int>>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.getJSONArray(i)
            out.add(p.getString(0) to p.getInt(1))
        }
        return out
    }

    fun score(cls: String, name: String): Int? {
        val key = norm(name)
        for (k in listOf(cls, "any")) {
            for (p in pool(k)) if (norm(p.first) == key) return p.second
        }
        return null
    }

    fun cost(name: String): Int {
        val e = meta.optJSONObject(norm(name)) ?: return -1
        return e.optInt("c", -1)
    }

    fun tier(s: Int) = when {
        s >= 100 -> "S"; s >= 90 -> "A"; s >= 80 -> "B"; s >= 70 -> "C"
        s >= 60 -> "D"; s >= 40 -> "E"; else -> "F"
    }

    /** Сопоставление по имени (нечёткое). */
    fun match(raw: String, cls: String): Pair<String, Int>? {
        val q = norm(raw)
        if (q.length < 3) return null
        val candidates = pool(cls) + pool("any")
        var best: Pair<String, Int>? = null
        var bestSim = 0.0
        for (c in candidates) {
            val n = norm(c.first)
            if (n.isEmpty()) continue
            var sim = similarity(q, n)
            if (n.contains(q) || q.contains(n)) sim = max(sim, 0.86)
            val qt = q.split(" ").toSet(); val nt = n.split(" ").toSet()
            if (qt.isNotEmpty() && nt.isNotEmpty()) {
                val inter = qt.intersect(nt).size
                sim = max(sim, inter.toDouble() / max(qt.size, nt.size))
            }
            if (sim > bestSim) { bestSim = sim; best = c }
        }
        return if (bestSim >= 0.60) best else null
    }

    /** Сопоставление по распознанному ТЕКСТУ карты (основной способ в оверлее). */
    fun matchByText(raw: String, cls: String): Pair<String, Int>? {
        val q = norm(raw)
        if (q.length < 12) return null
        val qt = q.split(" ").filter { it.length > 3 }.toSet()
        if (qt.isEmpty()) return null
        var best: Pair<String, Int>? = null
        var bestS = 0.0
        for (c in pool(cls) + pool("any")) {
            val m = meta.optJSONObject(norm(c.first)) ?: continue
            val xt = norm(m.optString("x"))
            val xtoks = xt.split(" ").filter { it.length > 3 }.toSet()
            if (xtoks.isEmpty()) continue
            val inter = qt.intersect(xtoks).size
            if (inter == 0) continue
            val dice = 2.0 * inter / (qt.size + xtoks.size)
            val cov = inter.toDouble() / qt.size
            val s = max(dice, cov * 0.9)
            if (s > bestS) { bestS = s; best = c }
        }
        return if (bestS >= 0.30) best else null
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val m = a.length; val n = b.length
        if (m == 0 || n == 0) return 0.0
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return 1.0 - prev[n].toDouble() / max(m, n)
    }

    fun advise(name: String, cls: String, deck: List<String>): Triple<Int, Int, String> {
        val base = score(cls, name) ?: 60
        var bonus = 0; val why = ArrayList<String>()
        val c = cost(name)
        val curve = IntArray(8)
        deck.forEach { val k = cost(it); if (k >= 0) curve[min(7, k)]++ }
        if ((c == 2 || c == 3) && curve[2] + curve[3] < 6) { bonus += 7; why.add("кривая") }
        if (c >= 6 && curve[6] + curve[7] >= 4) { bonus -= 6; why.add("перегруз") }
        val e = meta.optJSONObject(norm(name))
        val text = norm(e?.optString("x") ?: "")
        val mechs = e?.optJSONArray("m")
        var taunt = false; var dr = false
        if (mechs != null) for (i in 0 until mechs.length()) {
            val v = mechs.getString(i)
            if (v == "TAUNT") taunt = true
            if (v == "DEATHRATTLE") dr = true
        }
        if (text.contains("уничтожает")) { bonus += 5; why.add("ремувал") }
        if (text.contains("берете") || text.contains("добирает")) { bonus += 4; why.add("добор") }
        if (taunt) { bonus += 2 }
        if (dr && deck.count { d ->
                val mm = meta.optJSONObject(norm(d))?.optJSONArray("m") ?: JSONArray()
                (0 until mm.length()).any { mm.getString(it) == "DEATHRATTLE" }
            } >= 3) { bonus += 4; why.add("хрипы") }
        val race = e?.optString("r") ?: ""
        if (race.isNotEmpty() && deck.count { meta.optJSONObject(norm(it))?.optString("r") == race } >= 2) {
            bonus += 6; why.add("тип-пакет")
        }
        if (text.contains("к'тун")) {
            if (deck.any { norm(it).contains("к тун око империи") }) { bonus += 8; why.add("пакет богов") }
        }
        return Triple(base, max(1, base + bonus), why.joinToString(" · "))
    }

    fun deck(): List<String> {
        val s = prefs.getString("deck", "[]") ?: "[]"
        return try {
            val a = JSONArray(s); (0 until a.length()).map { a.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
}

object Ocr {
    private var api: Any? = null
    private var lang: String = ""
    private var usedClass: String = ""
    private var mImage: Method? = null
    private var mText: Method? = null
    private var mEnd: Method? = null
    private var mPsm: Method? = null

    var lastError: String = ""
    var engineInfo: String = ""

    private val CLASSES = listOf(
        "cz.adaptech.tesseract4android.TessBaseAPI",
        "com.googlecode.tesseract.android.TessBaseAPI"
    )

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "tessdata"); if (!d.exists()) d.mkdirs(); return d
    }

    fun file(ctx: Context, l: String) = File(dir(ctx), "$l.traineddata")

    fun has(ctx: Context, l: String): Boolean {
        val f = file(ctx, l)
        return f.exists() && f.length() > 100_000L
    }

    fun ready() = api != null

    fun sourceUrl(l: String, source: String): String = when (source) {
        "fast" -> "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$l.traineddata"
        "legacy" -> "https://github.com/tesseract-ocr/tessdata/raw/3.04.00/$l.traineddata"
        "best" -> "https://github.com/tesseract-ocr/tessdata_best/raw/main/$l.traineddata"
        else -> "https://github.com/tesseract-ocr/tessdata/raw/main/$l.traineddata"
    }

    suspend fun download(ctx: Context, l: String, source: String, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val url = sourceUrl(l, source)
            val req = Request.Builder().url(url).build()
            Store.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP " + r.code)
                val body = r.body ?: throw IllegalStateException("пустой ответ")
                val total = body.contentLength()
                val tmp = File(dir(ctx), "$l.traineddata.tmp")
                body.byteStream().use { input ->
                    tmp.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024); var read = 0L; var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            os.write(buf, 0, n); read += n
                            if (total > 0) onProgress(((read * 100) / total).toInt())
                        }
                    }
                }
                if (tmp.length() < 100_000L) { tmp.delete(); throw IllegalStateException("файл слишком мал") }
                val dst = file(ctx, l)
                if (dst.exists()) dst.delete()
                if (!tmp.renameTo(dst)) { tmp.copyTo(dst, overwrite = true); tmp.delete() }
            }
            release()
            lastError = ""
        }

    fun init(ctx: Context, l: String): Boolean {
        if (api != null && lang == l) return true
        if (!has(ctx, l)) { lastError = "Нет файла $l.traineddata. Скачайте модель."; return false }
        release()
        for (cn in CLASSES) {
            val cls: Class<*> = try { Class.forName(cn) } catch (_: Throwable) { continue }
            val a: Any = try { cls.getDeclaredConstructor().newInstance() } catch (_: Throwable) { continue }
            val init2 = cls.methods.firstOrNull {
                it.name == "init" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
            }
            val init3 = cls.methods.firstOrNull {
                it.name == "init" && it.parameterCount == 3 &&
                    it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java &&
                    it.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            if (init2 == null && init3 == null) { safeEnd(cls, a); continue }
            val path = ctx.filesDir.absolutePath
            var ok = false; var used = ""
            if (init3 != null) for (oem in listOf(1, 0, 3)) {
                ok = try { (init3.invoke(a, path, l, oem) as? Boolean) ?: false } catch (_: Throwable) { false }
                if (ok) { used = "OEM $oem"; break }
            }
            if (!ok && init2 != null) {
                ok = try { (init2.invoke(a, path, l) as? Boolean) ?: false } catch (_: Throwable) { false }
                if (ok) used = "default"
            }
            if (!ok) { lastError = "$cn: движок не принял модель"; safeEnd(cls, a); continue }
            mImage = cls.methods.firstOrNull {
                it.name == "setImage" && it.parameterCount == 1 && it.parameterTypes[0] == Bitmap::class.java
            }
            mText = cls.methods.firstOrNull { (it.name == "getUTF8Text" || it.name == "getUT8Text") && it.parameterCount == 0 }
            mEnd = cls.methods.firstOrNull { (it.name == "end" || it.name == "recycle" || it.name == "stop") && it.parameterCount == 0 }
            mPsm = cls.methods.firstOrNull {
                it.name == "setPageSegMode" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            // PSM 6 = единый блок текста (подходит для рамки описания карты)
            try { mPsm?.invoke(a, 6) } catch (_: Throwable) {}
            if (mImage == null || mText == null) { lastError = "$cn: нет setImage/getUTF8Text"; safeEnd(cls, a); continue }
            api = a; lang = l; usedClass = cn; engineInfo = used
            lastError = ""
            return true
        }
        return false
    }

    private fun safeEnd(cls: Class<*>, a: Any) {
        try {
            cls.methods.firstOrNull { (it.name == "end" || it.name == "recycle" || it.name == "stop") && it.parameterCount == 0 }?.invoke(a)
        } catch (_: Throwable) {}
    }

    fun read(bmp: Bitmap): String {
        val a = api ?: return ""
        val img = mImage ?: return ""
        val txt = mText ?: return ""
        return try {
            synchronized(a) { img.invoke(a, bmp); (txt.invoke(a) as? String ?: "").trim() }
        } catch (t: Throwable) { lastError = "Чтение кадра: " + t.javaClass.simpleName; "" }
    }

    fun release() {
        val a = api
        if (a != null) { try { mEnd?.invoke(a) } catch (_: Throwable) {} }
        api = null; lang = ""; usedClass = ""; mImage = null; mText = null; mEnd = null; mPsm = null
    }

    fun diag(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("Движки:\n")
        CLASSES.forEach { cn ->
            sb.append("  ").append(cn.substringAfterLast('.')).append(": ")
            try { Class.forName(cn); sb.append("да\n") } catch (_: Throwable) { sb.append("нет\n") }
        }
        listOf("rus", "eng").forEach { l ->
            val f = file(ctx, l)
            sb.append(l).append(".traineddata: ")
            if (f.exists()) sb.append(String.format("%.2f МБ", f.length() / 1048576.0)).append("\n")
            else sb.append("нет\n")
        }
        sb.append("Активен: ").append(if (ready()) usedClass.substringAfterLast('.') + " (" + engineInfo + ", " + lang + ")" else "нет").append("\n")
        sb.append("Ошибка: ").append(if (lastError.isEmpty()) "нет" else lastError).append("\n")
        sb.append("Оверлей: ").append(if (OverlayService.running) "включён" else "выключен").append("\n")
        sb.append("Карт в базе: ").append(Store.tiers.length())
        return sb.toString()
    }
}
