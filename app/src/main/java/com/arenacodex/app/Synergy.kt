package com.arenacodex.app

/**
 * Движок синергий «провайдер ↔ потребитель».
 * Каждая карта описывается двумя множествами ключей:
 *  prov — что карта ДАЁТ колоде (раса, хрип, клич, оружие, школа, трупы, раскопки, активаторы К'Туна…);
 *  cons — что карта ТРЕБУЕТ от колоды (упоминания расы/хрипов/оружия/школ/заклинаний/трупов/раскопок/payoff в тексте).
 * Бонус = за каждый cons-ключ: min(число провайдеров в колоде,3)*2;
 *         за каждый prov-ключ, если в колоде уже есть его потребитель: +2.
 */
object Synergy {

    private val cache = HashMap<String, Pair<Set<String>, Set<String>>>()

    private val RACE_WORDS = listOf(
        "DRAGON" to "дракон", "BEAST" to "звер", "ELEMENTAL" to "элементал",
        "MECHANICAL" to "механизм", "MURLOC" to "мурлок", "PIRATE" to "пират",
        "DEMON" to "демон", "UNDEAD" to "нежить", "NAGA" to "нага",
        "QUILBOAR" to "свинобраз", "TOTEM" to "тотем"
    )

    private fun isSpend(t: String) =
        t.contains("расходует") || t.contains("тратит") ||
        t.contains("потратьте") || t.contains("за каждый труп")

    fun tags(name: String): Pair<Set<String>, Set<String>> {
        cache[name]?.let { return it }
        val prov = HashSet<String>()
        val cons = HashSet<String>()
        val m = Store.meta.optJSONObject(Store.norm(name))
        if (m != null) {
            val text = Store.norm(m.optString("x"))
            val race = m.optString("r")
            val type = m.optString("t")
            val mechs = m.optJSONArray("m")

            if (race.isNotEmpty()) prov.add("RACE_" + race)
            if (type == "WEAPON") prov.add("WEAPON")
            if (type == "SPELL") {
                prov.add("SPELLS")
                if (m.optString("s").isNotEmpty()) prov.add("SCHOOL")
            }
            if (mechs != null) for (i in 0 until mechs.length()) {
                when (mechs.getString(i)) {
                    "DEATHRATTLE" -> prov.add("DEATHRATTLE")
                    "BATTLECRY" -> prov.add("BATTLECRY")
                }
            }
            if (text.contains("труп")) prov.add(if (isSpend(text)) "CORPSE_SPEND" else "CORPSE_GEN")
            if (text.contains("раскопк")) prov.add("EXCAVATE")
            if (text.contains("собирает")) prov.add("ASSEMBLE")
            if (name == "К'Тун, Око Империи") prov.add("CTHUN_PAYOFF")
            else if (text.contains("к'тун")) prov.add("CTHUN_ACT")

            for ((rk, w) in RACE_WORDS) if (text.contains(w)) cons.add("RACE_" + rk)
            if (text.contains("предсмертным хрипом") || text.contains("с предсмертным хрипом")) cons.add("DEATHRATTLE")
            if (text.contains("боевой клич") && (text.contains("ваш") || text.contains("после"))) cons.add("BATTLECRY")
            if (text.contains("вооружены") || text.contains("оружи") || text.contains("герой атакует")) cons.add("WEAPON")
            if (text.contains("школ")) cons.add("SCHOOL")
            if (text.contains("заклинани") && (text.contains("за кажд") || text.contains("разыграли"))) cons.add("SPELLS")
            if (text.contains("труп")) cons.add(if (isSpend(text)) "CORPSE_GEN" else "CORPSE_SPEND")
            if (text.contains("раскопк")) cons.add("EXCAVATE")
            if (name == "Порождение К'Туна") cons.add("CTHUN_PAYOFF")
            if (name == "К'Тун, Око Империи") cons.add("CTHUN_ACT")
        }
        val res = prov.toSet() to cons.toSet()
        cache[name] = res
        return res
    }

    fun clearCache() = cache.clear()

    private fun provCount(deck: List<String>, key: String) = deck.count { tags(it).first.contains(key) }
    private fun consCount(deck: List<String>, key: String) = deck.count { tags(it).second.contains(key) }

    private fun label(k: String): String = when {
        k.startsWith("RACE_") -> k.removePrefix("RACE_").lowercase()
        k == "DEATHRATTLE" -> "хрипы"
        k == "BATTLECRY" -> "боевые кличи"
        k == "WEAPON" -> "оружие"
        k == "SCHOOL" -> "школы заклинаний"
        k == "SPELLS" -> "заклинания"
        k == "CORPSE_GEN" -> "генераторы трупов"
        k == "CORPSE_SPEND" -> "расходители трупов"
        k == "EXCAVATE" -> "раскопки"
        k == "ASSEMBLE" -> "сборка"
        k == "CTHUN_ACT" -> "активаторы К'Туна"
        k == "CTHUN_PAYOFF" -> "payoff К'Туна"
        else -> k
    }

    fun bonus(name: String, deck: List<String>): Pair<Int, List<String>> {
        val (prov, cons) = tags(name)
        var b = 0
        val why = ArrayList<String>()

        for (k in cons) {
            val have = provCount(deck, k)
            if (have > 0) {
                val add = minOf(have, 3) * 2
                b += add
                why.add("+$add под синергию: ${label(k)} ×$have")
            }
        }
        for (k in prov) {
            if (consCount(deck, k) > 0) {
                b += 2
                why.add("+2 усиливает: ${label(k)}")
            }
        }
        if (cons.contains("CTHUN_PAYOFF") && name != "К'Тун, Око Империи" && provCount(deck, "CTHUN_PAYOFF") == 0) {
            b -= 40
            why.add("−40 нет payoff К'Туна")
        }
        if (b > 12) b = 12
        if (b < -40) b = -40
        return b to why
    }
}
