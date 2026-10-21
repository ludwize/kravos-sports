package net.kravos.sports.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kravos.sports.App
import net.kravos.sports.ui.screens.FavoritesScreen
import net.kravos.sports.ui.screens.LeagueScreen
import net.kravos.sports.ui.screens.LeaguesScreen
import net.kravos.sports.ui.screens.MatchScreen
import net.kravos.sports.ui.screens.PlayerScreen
import net.kravos.sports.ui.screens.ScoresScreen
import net.kravos.sports.ui.screens.SearchScreen
import net.kravos.sports.ui.screens.SettingsScreen
import net.kravos.sports.ui.screens.TeamScreen
import net.kravos.sports.ui.theme.SportsTheme
import net.kravos.sports.update.Release
import net.kravos.sports.update.Updater
import net.kravos.sports.widget.ScoresWidget

class MainActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        intent?.getStringExtra(EXTRA_MATCH)?.let { nav.go(Route.Match(it)) }
        setContent {
            val theme by app.prefs.theme.collectAsState()
            SportsTheme(theme) {
                CompositionLocalProvider(LocalNav provides nav, LocalApp provides app) { Root(app) }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MATCH)?.let { nav.go(Route.Match(it)) }
    }

    override fun onStop() {
        super.onStop()
        ScoresWidget.refreshAsync(applicationContext)
    }

    companion object { const val EXTRA_MATCH = "match" }
}

@Composable
private fun Root(app: App) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var bannerHidden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (app.prefs.autoUpdateCheck) release = Updater.latest() }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab.value != Tab.SCORES) {
        if (!nav.back()) nav.tab(Tab.SCORES)
    }

    val top = nav.stack.lastOrNull()
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val sel = nav.tab.value == t && top == null
                    NavigationBarItem(
                        selected = sel,
                        onClick = { nav.tab(t) },
                        icon = {
                            Icon(when (t) {
                                Tab.SCORES -> Icons.Filled.SportsSoccer
                                Tab.FAVORITES -> Icons.Filled.Star
                                Tab.LEAGUES -> Icons.Filled.EmojiEvents
                                Tab.SEARCH -> Icons.Filled.Search
                            }, t.label)
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            val r = release
            if (r != null && Updater.isNewer(r) && !bannerHidden && r.build != app.prefs.skippedBuild) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding()
                        .clickable { bannerHidden = true; nav.go(Route.Settings) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Nova verzija 1.0.${r.build} je spremna — dodirni za instalaciju", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Kasnije", Modifier.clickable { bannerHidden = true; app.prefs.skippedBuild = r.build }, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                }
            }
            val showingBanner = r != null && Updater.isNewer(r) && !bannerHidden && r.build != app.prefs.skippedBuild
            val p = if (showingBanner) PaddingValues(top = 0.dp, bottom = pad.calculateBottomPadding()) else pad
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AnimatedContent(targetState = top ?: nav.tab.value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { dest ->
                    holder.SaveableStateProvider(if (dest is Tab) "tab-${dest.name}" else "r-${nav.stack.size}-$dest") {
                    when (dest) {
                        is Route.Match -> MatchScreen(dest.key, p)
                        is Route.League -> LeagueScreen(dest, p)
                        is Route.TeamPage -> TeamScreen(dest, p)
                        is Route.PlayerPage -> PlayerScreen(dest, p)
                        is Route.Settings -> SettingsScreen(p, release) { scope.launch { release = Updater.latest() } }
                        Tab.SCORES -> ScoresScreen(p)
                        Tab.FAVORITES -> FavoritesScreen(p)
                        Tab.LEAGUES -> LeaguesScreen(p)
                        Tab.SEARCH -> SearchScreen(p)
                        else -> Unit
                    }
                    }
                }
            }
        }
    }
    @Suppress("unused") val c = Color.Unspecified
}
