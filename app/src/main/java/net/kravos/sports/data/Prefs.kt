package net.kravos.sports.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class FavTeam(val key: String, val name: String, val sport: String, val logo: String?)
data class FavLeague(val key: String, val name: String, val sport: String, val logo: String?)

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

    fun isFavTeam(key: String) = _teams.value.any { it.key == key }
    fun toggleTeam(t: Team) {
        val cur = _teams.value
        _teams.value = if (cur.any { it.key == t.key }) cur.filterNot { it.key == t.key } else cur + FavTeam(t.key, t.name, t.sport ?: "football", t.logo)
        saveTeams()
    }

    fun isFavLeague(key: String?) = key != null && _leagues.value.any { it.key == key }
    fun toggleLeague(key: String, name: String, sport: String, logo: String?) {
        val cur = _leagues.value
        _leagues.value = if (cur.any { it.key == key }) cur.filterNot { it.key == key } else cur + FavLeague(key, name, sport, logo)
        saveLeagues()
    }

    fun setSports(list: List<Sport>) {
        _sports.value = list.ifEmpty { listOf(Sport.FOOTBALL) }
        sp.edit().putString("sports2", _sports.value.joinToString(",") { it.slug }).apply()
    }

    private fun loadSports(): List<Sport> {
        val s = sp.getString("sports2", null) ?: return Sport.entries.toList()
        return s.split(',').mapNotNull { slug -> Sport.entries.firstOrNull { it.slug == slug } }.ifEmpty { Sport.entries.toList() }
    }

    private fun loadTeams(): List<FavTeam> = runCatching {
        val a = JSONArray(sp.getString("favTeams2", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { FavTeam(it.getString("key"), it.getString("name"), it.optString("sport", "football"), it.optString("logo").ifEmpty { null }) }
    }.getOrDefault(emptyList())

    private fun saveTeams() {
        val a = JSONArray()
        _teams.value.forEach { a.put(JSONObject().put("key", it.key).put("name", it.name).put("sport", it.sport).put("logo", it.logo ?: "")) }
        sp.edit().putString("favTeams2", a.toString()).apply()
    }

    private fun loadLeagues(): List<FavLeague> = runCatching {
        val a = JSONArray(sp.getString("favLeagues2", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            FavLeague(it.getString("key"), it.getString("name"), it.optString("sport", "football"), it.optString("logo").ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    private fun saveLeagues() {
        val a = JSONArray()
        _leagues.value.forEach { a.put(JSONObject().put("key", it.key).put("name", it.name).put("sport", it.sport).put("logo", it.logo ?: "")) }
        sp.edit().putString("favLeagues2", a.toString()).apply()
    }
}
