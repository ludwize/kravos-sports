package net.kravos.sports.data

/** Orders tournaments the way a Serbian fan expects: favorites, Serbia, European cups, world, big-5, rest of Europe, rest. */
object Ranking {
    private val BIG = setOf("ENG", "ESP", "ITA", "GER", "FRA")
    private val EUROPE = setOf(
        "ALB", "AND", "AUT", "BLR", "BEL", "BIH", "BUL", "CRO", "CYP", "CZE", "DEN", "EST", "FIN", "GRE", "HUN", "ISL", "IRL", "LVA", "LIE",
        "LTU", "LUX", "MLT", "MDA", "MNE", "NED", "MKD", "NOR", "POL", "POR", "ROU", "RUS", "SMR", "SVK", "SVN", "SWE", "SUI", "UKR",
        "TUR", "KOS", "SCO", "WAL", "NIR", "GEO", "ARM", "AZE", "ISR", "FRO", "KAZ", "ABA",
    )
    private val TOP_INT = listOf("champions league", "europa league", "conference league", "world cup", "euro", "nations league", "evroliga", "evrokup")
    private val OTHER_CONT = listOf("caf ", "afc ", "concacaf", "conmebol", "libertadores", "sudamericana", "ofc ", "africa", "asian", "arab")

    /** Well-known competitions get a fixed order inside their tier (FotMob league ids). */
    private val ORDER = listOf(
        "fm:42", "fm:73", "fm:10216", "fm:77", "fm:50", "fm:9806", "el:E", "el:U",   // UCL, UEL, UECL, WC, EURO, UNL, EL, EC
        "fm:182", "fm:9497",                                                        // SuperLiga, Kup Srbije
        "fm:47", "fm:87", "fm:55", "fm:54", "fm:53",                                // PL, LaLiga, Serie A, Bundesliga, Ligue 1
    )
    fun order(t: Tournament?): Int = t?.key?.let { k -> ORDER.indexOf(k).takeIf { it >= 0 } } ?: 99

    fun tier(t: Tournament?, favLeagues: Set<String>): Int {
        t ?: return 9
        if (t.key != null && t.key in favLeagues) return 0
        val c = t.country?.uppercase()
        val n = t.name.lowercase()
        val youth = n.contains("u19") || n.contains("u21") || n.contains("u17") || n.contains("u20") || n.contains("youth") || n.contains("women") || n.contains("femen") || n.contains("kvinner") || n.contains("w ")
        return when {
            youth -> 7
            OTHER_CONT.any { n.contains(it) } -> 6
            c == "SRB" -> 1
            (c == "INT" || c == "INT-2" || c == "EUR") && TOP_INT.any { n.contains(it) } -> 2
            c == "EUR" || n.contains("uefa") || n.contains("fiba") -> 2
            c == "INT" || c == "INT-2" -> 3
            c in BIG -> 4
            c in EUROPE -> 5
            c == "USA" -> 6
            n.startsWith("atp") || n.startsWith("wta") -> if (n.contains("challenger") || n.contains("itf")) 6 else 2
            else -> 7
        }
    }

    fun <T> sortGroups(groups: Map<Tournament?, T>, favLeagues: Set<String>): List<Pair<Tournament?, T>> =
        groups.entries.sortedWith(
            compareBy<Map.Entry<Tournament?, T>> { tier(it.key, favLeagues) }
                .thenBy { order(it.key) }
                .thenByDescending { it.key?.priority ?: 0 }
                .thenBy { it.key?.categoryName ?: "" }
                .thenBy { it.key?.name ?: "" },
        ).map { it.key to it.value }
}
