package net.kravos.sports.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.ui.graphics.vector.ImageVector

enum class Sport(
    val slug: String,
    val label: String,
    val icon: ImageVector,
    val lsSlug: String = slug,
    val espnPath: String? = null,
) {
    FOOTBALL("football", "Fudbal", Icons.Filled.SportsSoccer),
    BASKETBALL("basketball", "Košarka", Icons.Filled.SportsBasketball),
    TENNIS("tennis", "Tenis", Icons.Filled.SportsTennis),
    ICE_HOCKEY("ice-hockey", "Hokej", Icons.Filled.SportsHockey, lsSlug = "hockey"),
    F1("f1", "Formula 1", Icons.Filled.SportsMotorsports, espnPath = "racing/f1"),
    MMA("mma", "MMA (UFC)", Icons.Filled.SportsMma, espnPath = "mma/ufc"),
    AMERICAN_FOOTBALL("american-football", "NFL", Icons.Filled.SportsFootball, espnPath = "football/nfl"),
    BASEBALL("baseball", "MLB", Icons.Filled.SportsBaseball, espnPath = "baseball/mlb"),
    CRICKET("cricket", "Kriket", Icons.Filled.SportsCricket);

    /** Sports where the per-period table is the main score detail. */
    val setBased get() = this == TENNIS
    val hasPitch get() = this == FOOTBALL

    companion object {
        fun of(slug: String?): Sport = entries.firstOrNull { it.slug == slug } ?: FOOTBALL
    }
}

/** Translate status descriptions to Serbian. */
fun statusSr(desc: String): String = when (desc.lowercase()) {
    "not started" -> "Nije počelo"
    "ended", "finished" -> "Kraj"
    "ht" -> "Poluvreme"
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

/** Serbian names for the statistic labels the sources return most often. */
fun statSr(name: String): String = STAT_SR[name.lowercase()] ?: name

private val STAT_SR = mapOf(
    "expected goals (xg)" to "Očekivani golovi (xG)",
    "xg open play" to "xG iz igre",
    "xg set play" to "xG iz prekida",
    "xg on target (xgot)" to "xG u okvir (xGOT)",
    "non-penalty xg" to "xG bez penala",
    "touches in opposition box" to "Dodiri u protivničkom kaznenom",
    "big chances" to "Velike šanse",
    "big chances missed" to "Promašene velike šanse",
    "shots inside box" to "Šutevi iz kaznenog",
    "shots outside box" to "Šutevi izvan kaznenog",
    "own half" to "Pasovi na svojoj polovini",
    "opposition half" to "Pasovi na protivničkoj polovini",
    "accurate long balls" to "Precizne duge lopte",
    "accurate crosses" to "Precizni centaršutevi",
    "fouls committed" to "Faulovi",
    "corners" to "Korneri",
    "keeper saves" to "Odbrane golmana",
    "duels won" to "Osvojeni dueli",
    "ground duels won" to "Osvojeni dueli na zemlji",
    "aerial duels won" to "Osvojeni dueli u vazduhu",
    "successful dribbles" to "Uspešni driblinzi",
    "tackles won" to "Uspešni startovi",
    "shot accuracy" to "Preciznost šuta",
    "minutes played" to "Odigrani minuti",
    "fotmob rating" to "Ocena",
    "chances created" to "Stvorene šanse",
    "big chances created" to "Stvorene velike šanse",
    "expected assists (xa)" to "Očekivane asistencije (xA)",
    "xg + xa" to "xG + xA",
    "defensive actions" to "Defanzivne akcije",
    "passes into final third" to "Pasovi u završnu trećinu",
    "touches" to "Dodiri",
    "headed clearance" to "Izbijanja glavom",
    "was fouled" to "Fauliran",
    "duels lost" to "Izgubljeni dueli",
    "dribbled past" to "Predriblan",
    "xg non-penalty" to "xG bez penala",
    "expected goals on target (xgot)" to "xG u okvir (xGOT)",
    "saves" to "Odbrane",
    "goals conceded" to "Primljeni golovi",
    "acted as sweeper" to "Igrao kao libero",
    "diving save" to "Odbrane u skoku",
    "saves inside box" to "Odbrane u kaznenom",
    "punches" to "Izbijanja pesnicom",
    "throws" to "Izbacivanja rukom",
    "high claim" to "Hvatanja visokih lopti",
    "recoveries" to "Povraćene lopte",
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
    "top stats" -> "Najvažnije"
    "expected goals (xg)" -> "Očekivani golovi (xG)"
    "defence" -> "Odbrana"
    "discipline" -> "Disciplina"
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


fun statTitleSr(t: String): String = TOP_STAT_SR[t] ?: when (t.lowercase()) {
    "top scorer", "goals" -> "Strelci"
    "assists" -> "Asistencije"
    "goals + assists" -> "Golovi + asistencije"
    "fotmob rating", "rating" -> "Prosečna ocena"
    "goals per 90" -> "Golovi na 90 min"
    "expected goals (xg)" -> "Očekivani golovi (xG)"
    "expected goals (xg) per 90" -> "xG na 90 min"
    "xg + xa per 90" -> "xG + xA na 90 min"
    "shots on target per 90" -> "Šutevi u okvir na 90 min"
    "shots per 90" -> "Šutevi na 90 min"
    "accurate passes per 90" -> "Precizni pasovi na 90 min"
    "big chances created" -> "Stvorene velike šanse"
    "chances created" -> "Stvorene šanse"
    "accurate long balls per 90" -> "Precizne duge lopte na 90 min"
    "expected assist (xa)", "expected assists (xa)" -> "Očekivane asistencije (xA)"
    "xa per 90" -> "xA na 90 min"
    "successful dribbles per 90" -> "Uspešni driblinzi na 90 min"
    "big chances missed" -> "Promašene velike šanse"
    "penalties won" -> "Izboreni penali"
    "successful tackles per 90" -> "Uspešni startovi na 90 min"
    "interceptions per 90" -> "Presečene lopte na 90 min"
    "clearances per 90" -> "Izbijene lopte na 90 min"
    "blocks per 90" -> "Blokade na 90 min"
    "possession won final 3rd per 90" -> "Osvojene lopte u završnoj trećini na 90 min"
    "clean sheets" -> "Sačuvana mreža"
    "save percentage" -> "Procenat odbrana"
    "saves per 90" -> "Odbrane na 90 min"
    "goals prevented" -> "Sprečeni golovi"
    "goals conceded per 90" -> "Primljeni golovi na 90 min"
    "fouls committed per 90" -> "Faulovi na 90 min"
    "yellow cards" -> "Žuti kartoni"
    "red cards" -> "Crveni kartoni"
    "matches" -> "Utakmice"
    "started" -> "U startnoj postavi"
    "minutes played" -> "Minuti"
    else -> t
}

fun zoneSr(z: String): String = z
    .replace("Champions League qualification", "kvalifikacije za Ligu šampiona")
    .replace("Champions League", "Liga šampiona")
    .replace("Europa League qualification", "kvalifikacije za Ligu Evrope")
    .replace("Europa League", "Liga Evrope")
    .replace("Conference League qualification", "kvalifikacije za Ligu konferencija")
    .replace("Conference League", "Liga konferencija")
    .replace("Championship playoff", "Plej-of za titulu")
    .replace("Relegation playoff", "Plej-of za opstanak")
    .replace("Relegation qualification", "Baraž za opstanak")
    .replace("Relegation", "Ispadanje")
    .replace("Promotion playoff", "Plej-of za plasman")
    .replace("Promotion", "Plasman u viši rang")
    .replace("Playoffs", "Plej-of")
    .replace("Play-offs", "Plej-of")
    .replace("Knockout phase playoffs", "Baraž za nokaut fazu")
    .replace("Round of 16", "Osmina finala")

private val COUNTRY_SR = mapOf(
    "SRB" to "Srbija", "INT" to "Međunarodno", "INT-2" to "Međunarodno", "EUR" to "Evropa", "ENG" to "Engleska", "ESP" to "Španija",
    "ITA" to "Italija", "GER" to "Nemačka", "FRA" to "Francuska", "NED" to "Holandija", "POR" to "Portugal", "TUR" to "Turska",
    "BEL" to "Belgija", "SCO" to "Škotska", "GRE" to "Grčka", "CRO" to "Hrvatska", "BIH" to "Bosna i Hercegovina", "MNE" to "Crna Gora",
    "SVN" to "Slovenija", "MKD" to "Severna Makedonija", "AUT" to "Austrija", "SUI" to "Švajcarska", "DEN" to "Danska", "SWE" to "Švedska",
    "NOR" to "Norveška", "POL" to "Poljska", "CZE" to "Češka", "SVK" to "Slovačka", "HUN" to "Mađarska", "ROU" to "Rumunija", "BUL" to "Bugarska",
    "RUS" to "Rusija", "UKR" to "Ukrajina", "USA" to "SAD", "BRA" to "Brazil", "ARG" to "Argentina", "MEX" to "Meksiko", "KSA" to "Saudijska Arabija",
    "JPN" to "Japan", "CHN" to "Kina", "AUS" to "Australija", "WAL" to "Vels", "NIR" to "Severna Irska", "IRL" to "Irska", "CYP" to "Kipar",
    "ISR" to "Izrael", "FIN" to "Finska", "ISL" to "Island", "ALB" to "Albanija", "KOS" to "Kosovo", "EGY" to "Egipat", "MAR" to "Maroko",
)

fun countryName(code: String?): String? = code?.let { COUNTRY_SR[it.uppercase()] ?: it }
