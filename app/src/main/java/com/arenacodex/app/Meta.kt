package com.arenacodex.app

/**
 * База знаний драфта v4 (HearthArena + HSReplay, 21.11.2026).
 * Использует реальные баллы карт из тирлиста и винрейты легендарных групп.
 */
object Meta {

    private val ARCH = mapOf(
        "demon-hunter" to "tempo", "hunter" to "aggro",
        "rogue" to "tempo", "shaman" to "midrange",
        "paladin" to "midrange", "druid" to "midrange", "death-knight" to "control",
        "mage" to "value", "priest" to "value",
        "warrior" to "control", "warlock" to "value"
    )

    private val CURVE = mapOf(
        "aggro" to intArrayOf(2, 8, 7, 5, 3, 1, 0),
        "tempo" to intArrayOf(1, 7, 7, 6, 4, 2, 1),
        "midrange" to intArrayOf(1, 6, 7, 6, 4, 2, 1),
        "value" to intArrayOf(1, 5, 6, 6, 5, 3, 2),
        "control" to intArrayOf(0, 5, 6, 6, 5, 4, 3)
    )

    private val W = mapOf(
        "death-knight" to mapOf("CORPSE_GEN" to 5, "CORPSE_SPEND" to 5, "REBORN" to 4, "AOE" to 5, "VALUE" to 3),
        "hunter" to mapOf("BEAST" to 4, "DAMAGE" to 5, "REMOVAL" to 4, "RUSH" to 3, "VALUE" to 1),
        "shaman" to mapOf("ELEMENTAL" to 5, "TOTEM" to 4, "WINDFURY" to 3, "AOE" to 5, "OVERLOAD" to -3, "VALUE" to 3),
        "paladin" to mapOf("BUFF" to 6, "DIVINE_SHIELD" to 5, "REMOVAL" to 4, "TOKEN" to 4, "TAUNT" to 3, "VALUE" to 2),
        "priest" to mapOf("HEAL" to 4, "SHADOW" to 4, "VALUE" to 6, "REMOVAL" to 4, "TAUNT" to 3, "DRAW" to 4),
        "warrior" to mapOf("WEAPON" to 6, "REMOVAL" to 5, "AOE" to 6, "ARMOR" to 4, "TAUNT" to 3, "VALUE" to 2),
        "warlock" to mapOf("DEMON" to 4, "VALUE" to 5, "TAUNT" to 4, "AOE" to 4, "DISCARD" to -6, "DRAW" to 3),
        "mage" to mapOf("SPELL" to 4, "SPELLPOWER" to 4, "FREEZE" to 4, "AOE" to 6, "REMOVAL" to 4, "DRAW" to 4, "VALUE" to 4),
        "rogue" to mapOf("WEAPON" to 5, "COMBO" to 4, "REMOVAL" to 4, "DRAW" to 3, "VALUE" to 2),
        "druid" to mapOf("BEAST" to 4, "TOKEN" to 3, "BUFF" to 3, "RAMP" to -5, "VALUE" to 2),
        "demon-hunter" to mapOf("OUTCAST" to 4, "DAMAGE" to 4, "RUSH" to 3, "DEMON" to 3, "VALUE" to 2)
    )

    private val TRAP = mapOf(
        "demon-hunter" to "38.2% винрейт — аггро не работает, строй темпо с добором",
        "druid" to "рампа в арене не работает, 40.8% винрейт — слабый класс",
        "warlock" to "дискард без поддержки = мёртвый вес",
        "shaman" to "overload даёт неровные ходы",
        "mage" to "чистый аггро-маг проигрывает — нужен value/контроль стола"
    )

    private val PATCH = mapOf(
        "Темный диакон" to 6, "Сетеш" to 6, "И'Шарадж, Гроза Титанов" to 5,
        "К'Тун, Око Империи" to 4, "Г'уун" to -2, "Ваяние плоти" to -2,
        "Окулярный молот" to -2, "Присмотр" to -2, "Молниеносные рефлексы" to -2,
        "Герой в криостазисе" to -2, "Ритуал призыва" to -8, "Нестабильный прототип" to -6,
        "Костоправ" to -5, "Полуночный волк" to 2, "Хлыст-щупальце" to 2,
        "Цветок смерти" to 2, "Любопытная мракорысь" to 2, "Бесплотные искры" to 2
    )

    private val BUNDLE = mapOf(
        "death-knight" to listOf("CORPSE_GEN", "CORPSE_SPEND", "REBORN", "AOE"),
        "demon-hunter" to listOf("OUTCAST", "DAMAGE", "RUSH", "DRAW"),
        "druid" to listOf("BEAST", "TOKEN", "BUFF"),
        "hunter" to listOf("BEAST", "DAMAGE", "RUSH"),
        "mage" to listOf("AOE", "SPELL", "SPELLPOWER", "FREEZE", "VALUE", "DRAW"),
        "paladin" to listOf("BUFF", "DIVINE_SHIELD", "TOKEN", "TAUNT"),
        "priest" to listOf("HEAL", "VALUE", "SHADOW", "REMOVAL", "DRAW"),
        "rogue" to listOf("WEAPON", "COMBO", "REMOVAL"),
        "shaman" to listOf("ELEMENTAL", "TOTEM", "AOE", "WINDFURY"),
        "warlock" to listOf("DEMON", "VALUE", "TAUNT", "AOE", "DRAW"),
        "warrior" to listOf("WEAPON", "ARMOR", "AOE", "REMOVAL")
    )

    private val TRIBES = listOf(
        "BEAST" to "звер", "DEMON" to "демон", "ELEMENTAL" to "элементал",
        "MURLOC" to "мурлок", "PIRATE" to "пират", "MECHANICAL" to "механизм", "DRAGON" to "дракон"
    )

    fun archetype(cls: String) = ARCH[cls] ?: "midrange"

    fun plan(cls: String): String {
        val a = archetype(cls)
        val t = CURVE[a]!!
        return "Архетип $a · кривая ~${t[1]}×2 / ${t[2]}×3 / ≤${t[6]}×7+ · ремувал 4–6 (≥2 АОЕ) · добор 2+ · " +
            (W[cls]?.entries?.filter { it.value > 0 }?.sortedByDescending { it.value }?.take(3)
                ?.joinToString(", ") { it.key } ?: "") +
            (TRAP[cls]?.let { " · осторожно: $it" } ?: "")
    }

    fun roles(name: String): Set<String> {
        val m = Store.meta.optJSONObject(Store.norm(name)) ?: return emptySet()
        val raw = m.optString("x")
        val t = Store.norm(raw)
        val out = HashSet<String>()
        when (m.optString("t")) { "WEAPON" -> out.add("WEAPON"); "SPELL" -> out.add("SPELL") }
        val mechs = m.optJSONArray("m")
        if (mechs != null) for (i in 0 until mechs.length()) {
            when (mechs.getString(i)) {
                "TAUNT" -> out.add("TAUNT"); "DIVINE_SHIELD" -> out.add("DIVINE_SHIELD")
                "RUSH", "CHARGE" -> out.add("RUSH"); "COMBO" -> out.add("COMBO")
                "OUTCAST" -> out.add("OUTCAST"); "REBORN" -> out.add("REBORN")
                "SPELLPOWER" -> out.add("SPELLPOWER"); "FREEZE" -> out.add("FREEZE")
                "WINDFURY" -> out.add("WINDFURY"); "POISONOUS" -> out.add("REMOVAL")
                "LIFESTEAL", "DEATHRATTLE", "BATTLECRY", "DISCOVER" -> out.add("VALUE")
            }
        }
        when (m.optString("r")) {
            "BEAST" -> out.add("BEAST"); "DEMON" -> out.add("DEMON"); "ELEMENTAL" -> out.add("ELEMENTAL")
            "TOTEM" -> out.add("TOTEM"); "MECHANICAL", "MURLOC", "PIRATE" -> out.add("TOKEN")
        }
        when (m.optString("s")) { "SHADOW" -> out.add("SHADOW"); "FROST" -> out.add("FREEZE") }
        if (t.contains("уничтожает") || (t.contains("урона") && t.contains("существу"))) out.add("REMOVAL")
        if (t.contains("всем существам") || t.contains("всем противникам") || t.contains("всем вражеским")) out.add("AOE")
        if (t.contains("берете") || t.contains("добирает")) out.add("DRAW")
        if (t.contains("восполняет") || t.contains("восстанавливает")) out.add("HEAL")
        if (Regex("\\+\\d\\s*/\\s*\\+\\d").containsMatchIn(raw)) out.add("BUFF")
        if (t.contains("урона герою") || t.contains("урона противнику")) out.add("DAMAGE")
        if (t.contains("броню") || t.contains("брони")) out.add("ARMOR")
        if (t.contains("призывает") || t.contains("создает") || t.contains("создаёт")) out.add("TOKEN")
        if (t.contains("кристалл маны") || t.contains("рампа")) out.add("RAMP")
        if (t.contains("перегрузк")) out.add("OVERLOAD")
        if (t.contains("сбрасыва") || t.contains("сброс")) out.add("DISCARD")
        if (t.contains("труп")) out.add(if (t.contains("расходует") || t.contains("тратит")) "CORPSE_SPEND" else "CORPSE_GEN")
        return out
    }

    private fun text(name: String) = Store.norm(Store.meta.optJSONObject(Store.norm(name))?.optString("x") ?: "")

    private fun curve(deck: List<String>): IntArray {
        val c = IntArray(8)
        deck.forEach { val k = Store.cost(it); if (k >= 0) c[minOf(7, k)]++ }
        return c
    }

    fun bundleAdvice(cls: String, names: List<String>): Pair<Int, String> {
        val (wr, advice) = MetaData.groupWinrate(names)
        if (wr > 0) return (wr * 2).toInt() to advice
        
        val pref = BUNDLE[cls] ?: emptyList()
        var s = 0; val hits = ArrayList<String>()
        for (n in names) {
            val r = roles(n)
            for (k in pref) if (r.contains(k)) { s += 3; hits.add(k) }
            s += MetaData.tierBonus(cls, n) / 2
        }
        return s to (if (hits.isEmpty()) "группа не усиливает план класса" else "план класса: " + hits.distinct().joinToString(","))
    }

    fun advise(name: String, cls: String, deck: List<String>): Triple<Int, Int, String> {
        val rawBase = Store.score(cls, name) ?: Store.score("any", name) ?: 60
        val tierBonus = MetaData.tierBonus(cls, name)
        val patchAdj = PATCH[name] ?: 0
        val win12Bonus = if (MetaData.isWin12Card(name)) 5 else 0
        val base = rawBase + patchAdj + tierBonus + win12Bonus
        var bonus = 0.0
        val why = ArrayList<String>()
        if (patchAdj != 0) why.add((if (patchAdj > 0) "+" else "") + patchAdj + " правка 37.0.2")
        if (tierBonus >= 6) why.add("+$tierBonus топ-карта HearthArena")
        if (win12Bonus > 0) why.add("+$win12Bonus часто в 12-win деках")

        val arch = archetype(cls)
        val target = CURVE[arch]!!
        val w = W[cls] ?: emptyMap()
        val c = Store.cost(name)
        val cv = curve(deck)
        val r = roles(name)
        val t = text(name)

        if (c != null && c in 0..7) {
            val have = cv[c]; val want = target[c]
            if (have < want) { bonus += 4; why.add("+4 кривая($arch): нужен $c-дроп") }
            else if (have >= want + 2) { bonus -= 4; why.add("−4 перебор $c-дропов") }
            if (c >= 7 && cv[7] >= 2) { bonus -= 4; why.add("−4 много топ-энда (лимит 3)") }
        }

        for (role in r) {
            val weight = w[role] ?: 0
            if (weight > 0) { bonus += weight; why.add("+$weight сила класса: $role") }
            else if (weight < 0) { bonus += weight; why.add("$weight ловушка: $role") }
        }

        val rem = deck.count { roles(it).contains("REMOVAL") }
        val aoe = deck.count { roles(it).contains("AOE") }
        val drw = deck.count { roles(it).contains("DRAW") }
        val tnt = deck.count { roles(it).contains("TAUNT") }
        val minions = deck.count { Store.meta.optJSONObject(Store.norm(it))?.optString("t") == "MINION" }
        if (r.contains("REMOVAL") && rem < 5) { bonus += 4; why.add("+4 дефицит ремувала ($rem/4–6)") }
        if (r.contains("AOE") && aoe < 2) { bonus += 4; why.add("+4 нужно АОЕ ($aoe/2)") }
        if (r.contains("DRAW") && drw < 2) { bonus += 3; why.add("+3 нужен добор ($drw/2)") }
        if (r.contains("TAUNT") && tnt < 2) { bonus += 2; why.add("+2 нужна защита ($tnt/2)") }
        if (!r.contains("SPELL") && !r.contains("WEAPON") && minions < 15) { bonus += 3; why.add("+3 мало существ") }

        val cons = HashSet<String>()
        for ((k, word) in TRIBES) if (t.contains(word)) cons.add(k)
        if (t.contains("предсмертным хрипом") || t.contains("с предсмертным хрипом")) cons.add("DEATHRATTLE")
        if (t.contains("вооружены") || t.contains("герой атакует")) cons.add("WEAPON")
        if (t.contains("школ")) cons.add("SPELL")
        if (t.contains("труп")) cons.add(if (t.contains("расходует") || t.contains("тратит")) "CORPSE_GEN" else "CORPSE_SPEND")
        if (t.contains("к'тун") && name != "К'Тун, Око Империи") cons.add("CTHUN")
        if (name == "Порождение К'Туна") cons.add("CTHUN_PAYOFF")

        var syn = 0
        for (need in cons) {
            val have = deck.count { d ->
                roles(d).contains(need) ||
                    (need == "CTHUN_PAYOFF" && d == "К'Тун, Око Империи") ||
                    (need == "CTHUN" && text(d).contains("к'тун")) ||
                    (need == "DEATHRATTLE" && text(d).contains("хрип"))
            }
            if (have > 0) { val add = minOf(have, 3) * 2; syn += add; why.add("+$add ретро-синергия: $need ×$have") }
            else { syn -= 3; why.add("−3 предиктивно: нужен $need, которого нет") }
        }
        for ((k, word) in TRIBES) if (r.contains(k)) {
            val needers = deck.count { text(it).contains(word) }
            if (needers > 0) { syn += 2; why.add("+2 поддерживает племя $k") }
        }
        if (r.contains("WEAPON")) {
            val needers = deck.count { val x = text(it); x.contains("вооружены") || x.contains("герой атакует") }
            if (needers > 0) { syn += 2; why.add("+2 поддерживает оружие") }
        }
        if (r.contains("CORPSE_GEN")) {
            val needers = deck.count { val x = text(it); x.contains("расходует") || x.contains("тратит") }
            if (needers > 0) { syn += 2; why.add("+2 кормит расходителей") }
        }
        bonus += syn.coerceIn(-8, 12)

        val adj = maxOf(1, (base + bonus).toInt())
        return Triple(base, adj, why.joinToString(" · "))
    }
}
