package net.kravos.sports.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsRugby
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Circle
import androidx.compose.ui.graphics.vector.ImageVector

enum class Sport(val slug: String, val label: String, val icon: ImageVector, val periodLabel: String) {
    FOOTBALL("football", "Fudbal", Icons.Filled.SportsSoccer, "Pol."),
    BASKETBALL("basketball", "Košarka", Icons.Filled.SportsBasketball, "Č"),
    TENNIS("tennis", "Tenis", Icons.Filled.SportsTennis, "Set"),
    HANDBALL("handball", "Rukomet", Icons.Filled.SportsHandball, "Pol."),
    VOLLEYBALL("volleyball", "Odbojka", Icons.Filled.SportsVolleyball, "Set"),
    WATERPOLO("waterpolo", "Vaterpolo", Icons.Filled.Pool, "Č"),
    ICE_HOCKEY("ice-hockey", "Hokej", Icons.Filled.SportsHockey, "Tr."),
    AMERICAN_FOOTBALL("american-football", "Am. fudbal", Icons.Filled.SportsFootball, "Č"),
    BASEBALL("baseball", "Bejzbol", Icons.Filled.SportsBaseball, "In."),
    RUGBY("rugby", "Ragbi", Icons.Filled.SportsRugby, "Pol."),
    FUTSAL("futsal", "Futsal", Icons.Filled.SportsSoccer, "Pol."),
    TABLE_TENNIS("table-tennis", "Stoni tenis", Icons.Filled.SportsTennis, "Set"),
    BADMINTON("badminton", "Badminton", Icons.Filled.SportsTennis, "Set"),
    BEACH_VOLLEY("beach-volley", "Odbojka na pesku", Icons.Filled.SportsVolleyball, "Set"),
    MMA("mma", "MMA", Icons.Filled.SportsMma, "R"),
    DARTS("darts", "Pikado", Icons.Filled.Adjust, "Set"),
    SNOOKER("snooker", "Snuker", Icons.Filled.Circle, "Fr."),
    CRICKET("cricket", "Kriket", Icons.Filled.SportsCricket, "In."),
    FLOORBALL("floorball", "Florbol", Icons.Filled.SportsHockey, "Tr."),
    ESPORTS("esports", "E-sport", Icons.Filled.SportsEsports, "Mapa");

    /** Sports where the per-period table is the main score detail. */
    val setBased get() = this in setOf(TENNIS, VOLLEYBALL, TABLE_TENNIS, BADMINTON, BEACH_VOLLEY, DARTS, SNOOKER)
    val hasPitch get() = this == FOOTBALL || this == FUTSAL

    companion object {
        fun of(slug: String?): Sport = entries.firstOrNull { it.slug == slug } ?: FOOTBALL
    }
}

/** Translate SofaScore status descriptions to Serbian. */
fun statusSr(desc: String): String = when (desc.lowercase()) {
    "not started" -> "Nije počelo"
    "ended", "finished" -> "Kraj"
    "aet", "after extra time" -> "Posle prod."
    "ap", "after penalties" -> "Posle penala"
    "halftime" -> "Poluvreme"
    "1st half" -> "1. pol."
    "2nd half" -> "2. pol."
    "1st extra" -> "1. prod."
    "2nd extra" -> "2. prod."
    "extra time halftime", "awaiting extra time" -> "Pauza"
    "penalties" -> "Penali"
    "awaiting penalties" -> "Čeka penale"
    "1st quarter" -> "1. četv."
    "2nd quarter" -> "2. četv."
    "3rd quarter" -> "3. četv."
    "4th quarter" -> "4. četv."
    "overtime" -> "Produžetak"
    "pause" -> "Pauza"
    "1st period" -> "1. tr."
    "2nd period" -> "2. tr."
    "3rd period" -> "3. tr."
    "1st set" -> "1. set"
    "2nd set" -> "2. set"
    "3rd set" -> "3. set"
    "4th set" -> "4. set"
    "5th set" -> "5. set"
    "postponed" -> "Odloženo"
    "canceled", "cancelled" -> "Otkazano"
    "interrupted" -> "Prekinuto"
    "abandoned" -> "Napušteno"
    "suspended" -> "Suspendovano"
    "walkover" -> "Bez borbe"
    "retired" -> "Predaja"
    "started" -> "Počelo"
    "awaiting" -> "Čeka"
    else -> desc
}

/** Serbian names for the statistic labels SofaScore returns most often. */
fun statSr(name: String): String = STAT_SR[name.lowercase()] ?: name

private val STAT_SR = mapOf(
    "ball possession" to "Posed lopte",
    "expected goals" to "Očekivani golovi (xG)",
    "big chances" to "Velike šanse",
    "total shots" to "Ukupno šuteva",
    "goalkeeper saves" to "Odbrane golmana",
    "corner kicks" to "Korneri",
    "fouls" to "Faulovi",
    "passes" to "Pasovi",
    "tackles" to "Startovi",
    "free kicks" to "Slobodni udarci",
    "yellow cards" to "Žuti kartoni",
    "red cards" to "Crveni kartoni",
    "shots on target" to "Šutevi u okvir",
    "shots off target" to "Šutevi van okvira",
    "blocked shots" to "Blokirani šutevi",
    "hit woodwork" to "Stativa/prečka",
    "shots inside box" to "Šutevi iz kaznenog",
    "shots outside box" to "Šutevi izvan kaznenog",
    "big chances scored" to "Iskorišćene velike šanse",
    "big chances missed" to "Promašene velike šanse",
    "through balls" to "Prodorni pasovi",
    "touches in penalty area" to "Dodiri u kaznenom",
    "fouled in final third" to "Faulirani u završnoj trećini",
    "offsides" to "Ofsajdi",
    "accurate passes" to "Precizni pasovi",
    "throw-ins" to "Auti",
    "final third entries" to "Ulasci u završnu trećinu",
    "final third phase" to "Igra u završnoj trećini",
    "long balls" to "Duge lopte",
    "crosses" to "Centaršutevi",
    "duels" to "Dueli",
    "dispossessed" to "Izgubljene lopte",
    "ground duels" to "Dueli na zemlji",
    "aerial duels" to "Dueli u vazduhu",
    "dribbles" to "Driblinzi",
    "tackles won" to "Uspešni startovi",
    "total tackles" to "Ukupno startova",
    "interceptions" to "Presečene lopte",
    "recoveries" to "Povraćene lopte",
    "clearances" to "Izbijene lopte",
    "errors lead to a shot" to "Greške do šuta",
    "errors lead to a goal" to "Greške do gola",
    "total saves" to "Ukupno odbrana",
    "goals prevented" to "Sprečeni golovi",
    "high claims" to "Hvatanja visokih lopti",
    "punches" to "Izbijanja pesnicom",
    "goal kicks" to "Udarci od gola",
    "free throws" to "Slobodna bacanja",
    "2 pointers" to "Šut za 2",
    "3 pointers" to "Šut za 3",
    "field goals" to "Šut iz igre",
    "rebounds" to "Skokovi",
    "defensive rebounds" to "Skokovi u odbrani",
    "offensive rebounds" to "Skokovi u napadu",
    "assists" to "Asistencije",
    "turnovers" to "Izgubljene lopte",
    "steals" to "Ukradene lopte",
    "blocks" to "Blokade",
    "max points in a row" to "Najveća serija",
    "lead changes" to "Promene vođstva",
    "biggest lead" to "Najveća prednost",
    "time spent in lead" to "Vreme u vođstvu",
    "timeouts" to "Tajm-auti",
    "aces" to "Asovi",
    "double faults" to "Dvostruke greške",
    "first serve" to "Prvi servis",
    "second serve" to "Drugi servis",
    "first serve points" to "Poeni na prvi servis",
    "second serve points" to "Poeni na drugi servis",
    "break points saved" to "Spašene brejk lopte",
    "service games played" to "Servis gemovi",
    "first serve return points" to "Poeni na return prvog",
    "second serve return points" to "Poeni na return drugog",
    "break points converted" to "Iskorišćene brejk lopte",
    "return games played" to "Return gemovi",
    "total won" to "Ukupno osvojenih poena",
    "service points won" to "Osvojeni poeni na servis",
    "receiver points won" to "Osvojeni poeni na return",
    "max games in a row" to "Najviše gemova zaredom",
    "tiebreaks" to "Taj-brejkovi",
    "games won" to "Osvojeni gemovi",
    "goals" to "Golovi",
    "shots" to "Šutevi",
    "saves" to "Odbrane",
    "power play goals" to "Golovi sa igračem više",
    "penalty minutes" to "Kaznene minute",
    "faceoffs won" to "Osvojena vbacivanja",
    "hits" to "Udarci telom",
    "7m goals" to "Golovi sa 7m",
    "suspensions" to "Isključenja",
    "fast break goals" to "Golovi iz kontre",
    "attack points" to "Poeni iz napada",
    "service errors" to "Greške na servisu",
    "block points" to "Poeni iz bloka",
    "opponent errors" to "Greške protivnika",
    "receiver errors" to "Greške na prijemu",
)

fun statGroupSr(name: String): String = when (name.lowercase()) {
    "match overview" -> "Pregled"
    "shots" -> "Šutevi"
    "attack" -> "Napad"
    "passes" -> "Pasovi"
    "duels" -> "Dueli"
    "defending" -> "Odbrana"
    "goalkeeping" -> "Golman"
    "scoring" -> "Poeni"
    "other" -> "Ostalo"
    "service" -> "Servis"
    "return" -> "Return"
    "points" -> "Poeni"
    "games" -> "Gemovi"
    "lead" -> "Vođstvo"
    "expected" -> "Očekivano"
    else -> name
}

fun periodSr(p: String): String = when (p.uppercase()) {
    "ALL" -> "Ceo meč"
    "1ST" -> "1."
    "2ND" -> "2."
    "3RD" -> "3."
    "4TH" -> "4."
    "5TH" -> "5."
    "OT" -> "Prod."
    else -> p
}

val TOP_STAT_SR = mapOf(
    "rating" to "Prosečna ocena", "goals" to "Golovi", "assists" to "Asistencije",
    "goalsAssistsSum" to "Golovi + asistencije", "expectedGoals" to "xG", "expectedAssists" to "xA",
    "bigChancesCreated" to "Stvorene velike šanse", "bigChancesMissed" to "Promašene velike šanse",
    "accuratePasses" to "Precizni pasovi", "keyPasses" to "Ključni pasovi", "successfulDribbles" to "Uspešni driblinzi",
    "tackles" to "Startovi", "interceptions" to "Presečene lopte", "clearances" to "Izbijene lopte",
    "saves" to "Odbrane", "cleanSheet" to "Mreža sačuvana", "yellowCards" to "Žuti kartoni", "redCards" to "Crveni kartoni",
    "totalShots" to "Šutevi", "shotsOnTarget" to "Šutevi u okvir", "penaltyGoals" to "Golovi iz penala",
    "points" to "Poeni", "rebounds" to "Skokovi", "steals" to "Ukradene lopte", "blocks" to "Blokade",
    "threePointsMade" to "Trojke", "freeThrowsMade" to "Slobodna bacanja", "pir" to "PIR", "minutesPlayed" to "Minuti",
    "accurateLongBalls" to "Precizne duge lopte", "aerialDuelsWon" to "Osvojeni dueli u vazduhu",
)
