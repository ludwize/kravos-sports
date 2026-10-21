package net.kravos.sports.ui

import net.kravos.sports.data.Event
import net.kravos.sports.data.statusSr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dm = DateTimeFormatter.ofPattern("d.M.")
    private val dmy = DateTimeFormatter.ofPattern("d.M.yyyy.")
    private val SR = Locale.forLanguageTag("sr-Latn-RS")

    fun time(epochSec: Long): String = hm.format(Instant.ofEpochSecond(epochSec).atZone(zone))
    fun date(epochSec: Long): String = dm.format(Instant.ofEpochSecond(epochSec).atZone(zone))
    fun fullDate(epochSec: Long): String = dmy.format(Instant.ofEpochSecond(epochSec).atZone(zone))
    fun localDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate()

    private val DAYS = listOf("Pon", "Uto", "Sre", "Čet", "Pet", "Sub", "Ned")
    private val DAYS_LONG = listOf("Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota", "Nedelja")

    fun dayShort(d: LocalDate): String = DAYS[d.dayOfWeek.value - 1]
    fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Danas"
            today.minusDays(1) -> "Juče"
            today.plusDays(1) -> "Sutra"
            else -> "${DAYS_LONG[d.dayOfWeek.value - 1]}, ${d.dayOfMonth}.${d.monthValue}."
        }
    }

    /** Live clock text for the score row ("67'", "3. četv.", "Poluvreme"). */
    fun liveClock(e: Event): String = e.clock?.takeIf { it.isNotBlank() } ?: statusSr(e.status.description).ifBlank { "Uživo" }

    /** Short status text for the score row. */
    fun statusShort(e: Event): String = when {
        e.status.live -> liveClock(e)
        e.status.finished -> when (e.status.description.lowercase()) {
            "aet" -> "Prod."
            "ap" -> "Pen."
            "retired" -> "Predaja"
            "walkover" -> "B.b."
            else -> "Kraj"
        }
        e.status.cancelled -> statusSr(e.status.description)
        else -> time(e.start)
    }

    fun ordinal(n: Int) = "$n."

    fun num(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

    fun money(v: Long, cur: String?): String {
        val c = when (cur) { "EUR", null -> "€"; "USD" -> "$"; "GBP" -> "£"; else -> cur }
        return when {
            v >= 1_000_000 -> String.format(Locale.US, "%s%.1fM", c, v / 1_000_000.0)
            v >= 1_000 -> String.format(Locale.US, "%s%dK", c, v / 1000)
            else -> "$c$v"
        }
    }

    fun age(birthSec: Long): Int {
        val b = localDate(birthSec)
        val now = LocalDate.now()
        var a = now.year - b.year
        if (now.dayOfYear < b.dayOfYear) a--
        return a
    }

    @Suppress("unused") val locale = SR
}
