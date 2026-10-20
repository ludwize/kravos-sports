package net.kravos.sports.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import net.kravos.sports.App

enum class Tab(val label: String) { SCORES("Rezultati"), FAVORITES("Omiljeno"), LEAGUES("Lige"), SEARCH("Pretraga") }

sealed class Route {
    data class Match(val id: Int) : Route()
    data class League(val utId: Int, val name: String, val seasonId: Int? = null) : Route()
    data class TeamPage(val id: Int, val name: String) : Route()
    data class PlayerPage(val id: Int, val name: String) : Route()
    data object Settings : Route()
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    val tab = mutableStateOf(Tab.SCORES)
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); tab.value = t }
}

val LocalNav = staticCompositionLocalOf { Navigator() }
val LocalApp = staticCompositionLocalOf<App> { error("no app") }

/** Simple async state holder for screens. */
data class Load<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)

class Loader<T>(private val st: MutableState<Load<T>>, private val tick: MutableState<Int>) {
    val value: Load<T> get() = st.value
    val data: T? get() = st.value.data
    fun reload() { st.value = st.value.copy(loading = true, error = null); tick.value = tick.value + 1 }
}

/**
 * Loads [block] when [keys] change and, if [refreshMs] returns > 0, keeps refreshing in the background.
 * Keeps showing previous data while refreshing (no flicker).
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, refreshMs: (T?) -> Long = { 0L }, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf(Load<T>()) }
    val tick = remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(state, tick.value) {
        while (true) {
            try {
                val d = block()
                state.value = Load(d, false, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
            val r = refreshMs(state.value.data)
            if (r <= 0) break
            delay(r)
            // don't poll while the app is in the background
            while (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) delay(1500)
        }
    }
    return remember(state, tick) { Loader(state, tick) }
}
