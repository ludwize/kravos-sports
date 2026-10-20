package net.kravos.sports.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class FavTeam(val id: Int, val name: String, val sport: String)
data class FavLeague(val utId: Int, val name: String, val sport: String, val alpha2: String?)

enum class ThemeMode { SYSTEM, DARK, LIGHT }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _teams = MutableStateFlow(loadTeams())
    val teams: StateFlow<List<FavTeam>> = _teams
    private val _leagues = MutableStateFlow(loadLeagues())
    val leagues: StateFlow<List<FavLeague>> = _leagues
    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val theme: StateFlow<ThemeMode> = _theme
    private val _sports = MutableStateFlow(loadSports())
    /** Ordered, enabled sports shown in the sport bar. */
    val sports: StateFlow<List<Sport>> = _sports

    var lastSport: Sport
        get() = Sport.of(sp.getString("lastSport", "football"))
        set(v) = sp.edit().putString("lastSport", v.slug).apply()

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()

    var skippedBuild: Int
        get() = sp.getInt("skippedBuild", 0)
        set(v) = sp.edit().putInt("skippedBuild", v).apply()

    fun setTheme(m: ThemeMode) { sp.edit().putString("theme", m.name).apply(); _theme.value = m }

    fun isFavTeam(id: Int) = _teams.value.any { it.id == id }
    fun toggleTeam(t: Team, sport: String) {
        val cur = _teams.value
        _teams.value = if (cur.any { it.id == t.id }) cur.filterNot { it.id == t.id } else cur + FavTeam(t.id, t.name, sport)
        saveTeams()
    }

    fun isFavLeague(utId: Int?) = utId != null && _leagues.value.any { it.utId == utId }
    fun toggleLeague(utId: Int, name: String, sport: String, alpha2: String?) {
        val cur = _leagues.value
        _leagues.value = if (cur.any { it.utId == utId }) cur.filterNot { it.utId == utId } else cur + FavLeague(utId, name, sport, alpha2)
        saveLeagues()
    }

    fun setSports(list: List<Sport>) {
        _sports.value = list.ifEmpty { listOf(Sport.FOOTBALL) }
        sp.edit().putString("sports", _sports.value.joinToString(",") { it.slug }).apply()
    }

    private fun loadSports(): List<Sport> {
        val s = sp.getString("sports", null) ?: return Sport.entries.toList()
        return s.split(',').mapNotNull { slug -> Sport.entries.firstOrNull { it.slug == slug } }.ifEmpty { Sport.entries.toList() }
    }

    private fun loadTeams(): List<FavTeam> = runCatching {
        val a = JSONArray(sp.getString("favTeams", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { FavTeam(it.getInt("id"), it.getString("name"), it.optString("sport", "football")) }
    }.getOrDefault(emptyList())

    private fun saveTeams() {
        val a = JSONArray()
        _teams.value.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("sport", it.sport)) }
        sp.edit().putString("favTeams", a.toString()).apply()
    }

    private fun loadLeagues(): List<FavLeague> = runCatching {
        val a = JSONArray(sp.getString("favLeagues", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            FavLeague(it.getInt("utId"), it.getString("name"), it.optString("sport", "football"), it.optString("alpha2").ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    private fun saveLeagues() {
        val a = JSONArray()
        _leagues.value.forEach { a.put(JSONObject().put("utId", it.utId).put("name", it.name).put("sport", it.sport).put("alpha2", it.alpha2 ?: "")) }
        sp.edit().putString("favLeagues", a.toString()).apply()
    }
}
