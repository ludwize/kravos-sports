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

    fun tier(t: Tournament?, favLeagues: Set<String>): Int {
        t ?: return 9
        if (t.key != null && t.key in favLeagues) return 0
        val c = t.country?.uppercase()
        val n = t.name.lowercase()
        val youth = n.contains("u19") || n.contains("u21") || n.contains("u17") || n.contains("u20") || n.contains("youth") || n.contains("women") || n.contains("femen") || n.contains("kvinner") || n.contains("w ")
        return when {
            youth -> 7
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
                .thenByDescending { it.key?.priority ?: 0 }
                .thenBy { it.key?.categoryName ?: "" }
                .thenBy { it.key?.name ?: "" },
        ).map { it.key to it.value }
}
