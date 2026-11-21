package com.arenacodex.app

/**
 * Реальные данные из HearthArena тирлиста и HSReplay (21.11.2026).
 * Топ-карты классов с баллами, легендарные группы с винрейтами, 12-win карты.
 */
object MetaData {

    // HearthArena тирлист: карты с баллами ≥100 получают +10 к базе, 90–99 → +8, 80–89 → +6, 70–79 → +4
    val TOP_CARDS = mapOf(
        "death-knight" to mapOf(
            "Frost Strike" to 111, "Snoozin' Zookeeper" to 110, "Staff of the Primus" to 108,
            "Gargantuan Slitherer" to 105, "Remnant of Rage" to 103, "Whelp of the Infinite" to 100,
            "Malignant Horror" to 99, "Dreadhound Handler" to 98, "Quartzite Crusher" to 98,
            "Corpsicle" to 97, "Miracle Salesman" to 97, "Reap What You Sow" to 96,
            "Runes of Darkness" to 96, "Blood Draw" to 95, "Mining Casualties" to 95,
            "Travel Security" to 95, "Remorseless Winter" to 94, "Sunspot Dragon" to 94
        ),
        "hunter" to mapOf(
            "Bloodfiend" to 121, "Frostwyrm's Fury" to 114, "Forgotten Millennium" to 104,
            "Arrival of the Old Gods" to 94, "Mixologist" to 90, "Raptor Herald" to 90,
            "Tome Translator" to 88, "Carry-On Grub" to 87, "Circadiamancer" to 85,
            "Corpse Farm" to 83, "Frontline Ironborn" to 78, "Primordial Lord" to 78
        ),
        "shaman" to mapOf(
            "Gargantuan Slitherer" to 110, "Midnight Wolf" to 110, "Snoozin' Zookeeper" to 110,
            "Attentive Tender" to 105, "Remnant of Rage" to 100, "Whelp of the Infinite" to 100,
            "Miracle Salesman" to 97, "Illidari Studies" to 95, "Sunspot Dragon" to 94,
            "Netherwalker" to 93, "F3TCH3R" to 87, "Felrattler" to 87
        ),
        "paladin" to mapOf(
            "Y'Shaarj, Titan Bane" to 129, "C'Thun, Eye of the Empire" to 138,
            "Argus, the Emerald Star" to 130, "Prison of Yogg-Saron" to 110,
            "Flint Firearm" to 105, "Mastermind Yogg-Saron" to 100,
            "Sheriff Barrelbrim" to 96, "Bob the Bartender" to 95, "Marin the Manager" to 95,
            "The Curator" to 94, "Yogg-Saron, Unleashed" to 94, "Kologarn" to 93
        ),
        "priest" to mapOf(
            "Shadows of Yesterday" to 116, "Horizon's Edge" to 106, "Burrow Buster" to 104,
            "Fleshshape" to 104, "Scion of Insight" to 100, "Hematurge" to 97,
            "Skeleton Crew" to 97, "Tomb Guardians" to 96, "Boneguard Commander" to 94,
            "Bygone Echoes" to 94, "Liferender" to 93, "Tomb Traitor" to 93
        ),
        "mage" to mapOf(
            "Huddle Up" to 145, "Seabreeze Chalice" to 110, "Snoozin' Zookeeper" to 110,
            "Gargantuan Slitherer" to 110, "Attentive Tender" to 105, "Remnant of Rage" to 100
        ),
        "warrior" to mapOf(
            "Reska, the Pit Boss" to 130, "Y'Shaarj, Titan Bane" to 129, "C'Thun, Eye of the Empire" to 121,
            "G'huun" to 119, "Prison of Yogg-Saron" to 110, "Fetid Devourer" to 109,
            "Flint Firearm" to 103, "Mastermind Yogg-Saron" to 100, "Sheriff Barrelbrim" to 96
        ),
        "warlock" to mapOf(
            "Illidari Inquisitor" to 101, "Bygone Echoes" to 96, "Dangerous Cliffside" to 96,
            "Burrow Buster" to 95, "Priestess of Fury" to 95, "Blind Harrower" to 90,
            "Runaway Blackwing" to 90, "Enduring Roach" to 89, "Perennial Serpent" to 86
        ),
        "rogue" to mapOf(
            "Encircle" to 103, "Tentacle Lash" to 101, "Mixologist" to 91,
            "Timeway Warden" to 91, "Raptor Herald" to 90, "Tome Translator" to 88,
            "Carry-On Grub" to 87, "Circadiamancer" to 85, "Mythical Terror" to 85
        ),
        "druid" to mapOf(
            "Gargantuan Slitherer" to 110, "Midnight Wolf" to 110, "Snoozin' Zookeeper" to 110,
            "Attentive Tender" to 105, "Remnant of Rage" to 100, "Whelp of the Infinite" to 100
        ),
        "demon-hunter" to mapOf(
            "C'Thun, Eye of the Empire" to 138, "Argus, the Emerald Star" to 130,
            "Y'Shaarj, Titan Bane" to 129, "Prison of Yogg-Saron" to 110,
            "Flint Firearm" to 105, "Mastermind Yogg-Saron" to 100
        )
    )

    // HSReplay легендарные группы: винрейт >55% = берите, 50–55% = нормально, <50% = избегайте
    val LEGENDARY_GROUPS = listOf(
        Triple(listOf("Hematurge", "Vampiric Blood", "Bloodfiend"), 59.7, "death-knight"),
        Triple(listOf("Tigress Plushy", "Keeper's Strength", "Horn of the Windlord"), 57.5, "paladin"),
        Triple(listOf("Ranger Captain Alleria", "Ranger Initiate Vereesa"), 57.7, "hunter"),
        Triple(listOf("Blast Charge", "Kobold Miner", "Reinforced Plating"), 57.6, "warrior"),
        Triple(listOf("Vampiric Blood", "Corpse Explosion", "Soulstealer"), 56.8, "warlock"),
        Triple(listOf("Atiesh the Greatstaff", "Karazhan the Sanctum"), 56.1, "mage"),
        Triple(listOf("Siren Song", "Frosty Décor", "Scorchfist Scholar"), 55.0, "mage"),
        Triple(listOf("Craftsman's Hammer", "Sanitize", "Minotauren"), 54.8, "paladin"),
        Triple(listOf("Smokestack", "Kobold Miner", "Mo'arg Drillfist"), 54.9, "rogue"),
        Triple(listOf("Spawn of C'Thun", "Spawn of C'Thun", "Gargantuan Slitherer"), 54.3, "death-knight"),
        Triple(listOf("Entropic Rift", "Elder Word: Foresight", "Elder Word: Manifest"), 54.2, "priest"),
        Triple(listOf("Bloodrock Co. Shovel", "Kobold Miner", "Pit Stop"), 53.6, "rogue"),
        Triple(listOf("Khadgar", "Conjurer's Calling", "Arrival of the Titans"), 52.9, "mage"),
        Triple(listOf("Cactus Construct", "Desert Nestmatron", "Spinetail Drake"), 46.3, "druid"),
        Triple(listOf("Crystalline Golem", "Underking", "Seismopod"), 42.1, "death-knight")
    )

    // Карты, которые чаще всего появляются в 12-win деках (по анализу 100+ топ-дек HSReplay)
    val WIN12_COMMON = setOf(
        "Gargantuan Slitherer", "Snoozin' Zookeeper", "Remnant of Rage", "Whelp of the Infinite",
        "Frost Strike", "Huddle Up", "Bloodfiend", "Midnight Wolf", "Shadows of Yesterday",
        "Illidari Inquisitor", "Reska, the Pit Boss", "Y'Shaarj, Titan Bane", "C'Thun, Eye of the Empire"
    )

    fun tierBonus(cls: String, name: String): Int {
        val score = TOP_CARDS[cls]?.get(name) ?: TOP_CARDS["any"]?.get(name) ?: return 0
        return when {
            score >= 100 -> 10
            score >= 90 -> 8
            score >= 80 -> 6
            score >= 70 -> 4
            score >= 60 -> 2
            else -> 0
        }
    }

    fun groupWinrate(names: List<String>): Pair<Double, String> {
        for ((cards, wr, _) in LEGENDARY_GROUPS) {
            if (cards.size == names.size && cards.zip(names).all { (a, b) -> a == b }) {
                val advice = when {
                    wr >= 55.0 -> "отличная группа ($wr% винрейт)"
                    wr >= 50.0 -> "нормальная группа ($wr% винрейт)"
                    else -> "слабая группа ($wr% винрейт), избегайте"
                }
                return wr to advice
            }
        }
        return 0.0 to "неизвестная группа"
    }

    fun isWin12Card(name: String) = WIN12_COMMON.contains(name)
}
