package com.arenacodex.app

/**
 * База знаний драфта: архетипы классов, целевые кривые, классовые веса ролей,
 * ловушки и ретроактивные/предиктивные синергии (по гайдам арены 2026).
 */
object Meta {

    private val ARCH = mapOf(
        "demon-hunter" to "aggro", "hunter" to "aggro",
        "rogue" to "tempo", "shaman" to "tempo",
        "paladin" to "midrange", "druid" to "midrange", "death-knight" to "midrange",
        "mage" to "value", "priest" to "value",
        "warrior" to "control", "warlock" to "control"
    )

    // Целевая кривая [1,2,3,4,5,6,7+] по архетипам
    private val CURVE = mapOf(
        "aggro" to intArrayOf(2, 8, 7, 5, 3, 1, 0),
        "tempo" to intArrayOf(1, 7, 7, 6, 4, 2, 1),
        "midrange" to intArrayOf(1, 6, 7, 6, 4, 2, 1),
        "value" to intArrayOf(1, 5, 6, 6, 5, 3, 2),
        "control" to intArrayOf(0, 5, 6, 6, 5, 4, 3)
    )

    // Насколько класс любит каждую роль/механику (плюс = любит, минус = ловушка)
    private val W = mapOf(
        "warrior" to mapOf("WEAPON" to 6, "REMOVAL" to 5, "AOE" to 6, "ARMOR" to 4, "TAUNT" to 3, "VALUE" to 2),
        "paladin" to mapOf("BUFF" to 5, "DIVINE_SHIELD" to 4, "REMOVAL" to 4, "TOKEN" to 3, "TAUNT" to 3, "VALUE" to 2),
        "mage" to mapOf("SPELL" to 3, "SPELLPOWER" to 4, "FREEZE" to 4, "AOE" to 6, "REMOVAL" to 4, "DRAW" to 3, "VALUE" to 3),
        "priest" to mapOf("HEAL" to 4, "SHADOW" to 3, "VALUE" to 5, "REMOVAL" to 4, "TAUNT" to 3, "DRAW" to 3),
        "rogue" to mapOf("WEAPON" to 5, "COMBO" to 4, "REMOVAL" to 4, "DRAW" to 3, "VALUE" to 2),
        "hunter" to mapOf("BEAST" to 4, "DAMAGE" to 5, "REMOVAL" to 4, "RUSH" to 3, "VALUE" to 1),
        "druid" to mapOf("BEAST" to 4, "TOKEN" to 3, "BUFF" to 3, "RAMP" to -5, "VALUE" to 2),
        "shaman" to mapOf("ELEMENTAL" to 4, "TOTEM" to 3, "WINDFURY" to 3, "AOE" to 4, "OVERLOAD" to -3, "VALUE" to 2),
        "warlock" to mapOf("DEMON" to 4, "VALUE" to 5, "TAUNT" to 4, "AOE" to 4, "DISCARD" to -5),
        "death-knight" to mapOf("CORPSE_GEN" to 4, "CORPSE_SPEND" to 4, "REBORN" to 3, "AOE" to 4, "VALUE" to 2),
        "demon-hunter" to mapOf("OUTCAST" to 5, "DAMAGE" to 5, "RUSH" to 4, "DEMON" to 3, "VALUE" to 1)
    )

    private val TRIBES = listOf(
        "BEAST" to "звер", "DEMON" to "демон", "ELEMENTAL" to "элементал",
        "MURLOC" to "мурлок", "PIRATE" to "пират", "MECHANICAL" to "механизм", "DRAGON" to "дракон"
    )

    fun archetype(cls: String) = ARCH[cls] ?: "midrange"

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

    fun advise(name: String, cls: String, deck: List<String>): Triple<Int, Int, String> {
        val base = Store.score(cls, name) ?: Store.score("any", name) ?: 60
        var bonus = 0.0
        val why = ArrayList<String>()
        val arch = archetype(cls)
        val target = CURVE[arch] ?: CURVE["midrange"]!!
        val w = W[cls] ?: emptyMap()
        val c = Store.cost(name)
        val cv = curve(deck)
        val r = roles(name)
        val t = text(name)

        // 1) посадка в кривую относительно архетипа класса
        if (c != null && c in 0..7) {
            val have = cv[c]; val want = target[c]
            if (have < want) { bonus += 4; why.add("+4 кривая($arch): не хватает $c-дропа") }
            else if (have >= want + 2) { bonus -= 4; why.add("−4 перебор $c-дропов") }
            if (c >= 7 && cv[7] >= 2) { bonus -= 4; why.add("−4 много топ-энда") }
        }

        // 2) роли, которые ценит именно этот класс
        for (role in r) {
            val weight = w[role] ?: 0
            if (weight > 0) { bonus += weight; why.add("+$weight сила класса: $role") }
            else if (weight < 0) { bonus += weight; why.add("$weight ловушка класса: $role") }
        }

        // 3) глобальные потребности колоды: 4–6 ремувала, ≥2 АОЕ, немного добора
        val rem = deck.count { roles(it).contains("REMOVAL") }
        val aoe = deck.count { roles(it).contains("AOE") }
        val drw = deck.count { roles(it).contains("DRAW") }
        val minions = deck.count { Store.meta.optJSONObject(Store.norm(it))?.optString("t") == "MINION" }
        if (r.contains("REMOVAL") && rem < 5) { bonus += 4; why.add("+4 дефицит ремувала ($rem/4–6)") }
        if (r.contains("AOE") && aoe < 2) { bonus += 4; why.add("+4 нужно АОЕ ($aoe/2)") }
        if (r.contains("DRAW") && drw < 2) { bonus += 3; why.add("+3 нужен добор") }
        if (!r.contains("SPELL") && !r.contains("WEAPON") && minions < 15) { bonus += 3; why.add("+3 мало существ") }

        // 4) ретроактивные синергии (только с тем, что УЖЕ взято) + штраф за предиктивность
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
                    (need == "DEATHRATTLE" && roles(d).contains("VALUE") && text(d).contains("хрип"))
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
