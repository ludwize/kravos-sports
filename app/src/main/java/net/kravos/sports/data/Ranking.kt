package net.kravos.sports.data

/** Orders tournaments the way a European (Serbian) fan expects: favorites, Serbia, European cups, world, big-5, rest of Europe, rest. */
object Ranking {
    private val BIG = setOf("EN", "ES", "IT", "DE", "FR")
    private val EUROPE = setOf(
        "AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "GR", "HU", "IS", "IE", "LV", "LI",
        "LT", "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "SK", "SI", "SE", "CH", "UA",
        "TR", "XK", "SC", "WA", "NI", "GE", "AM", "AZ", "IL", "FO", "GI", "KZ",
    )

    fun tier(t: Tournament?, favLeagues: Set<Int>): Int {
        t ?: return 9
        if (t.utId != null && t.utId in favLeagues) return 0
        val a = t.alpha2?.uppercase()
        val cat = (t.categorySlug ?: t.categoryName ?: "").lowercase()
        return when {
            a == "RS" -> 1
            cat.contains("europe") -> 2
            cat.contains("world") || cat.contains("international") -> 3
            a in BIG -> 4
            cat == "usa" || a == "US" -> 6
            a in EUROPE -> 5
            cat.contains("atp") || cat.contains("wta") || cat.contains("itf") || cat.contains("challenger") -> when {
                cat.contains("itf") -> 7
                cat.contains("challenger") -> 6
                else -> 2
            }
            else -> 7
        }
    }

    fun <T> sortGroups(groups: Map<Tournament?, T>, favLeagues: Set<Int>): List<Pair<Tournament?, T>> =
        groups.entries.sortedWith(
            compareBy<Map.Entry<Tournament?, T>> { tier(it.key, favLeagues) }
                .thenByDescending { it.key?.priority ?: 0 }
                .thenBy { it.key?.categoryName ?: "" }
                .thenBy { it.key?.name ?: "" },
        ).map { it.key to it.value }
}
