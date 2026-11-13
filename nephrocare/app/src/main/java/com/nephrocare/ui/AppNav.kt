package com.nephrocare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nephrocare.AppContainer
import com.nephrocare.R

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EMERGENCY = "emergency"
    const val SETTINGS = "settings"
    const val WEIGHT = "weight"
    const val BP = "bp"
    const val LABS = "labs"
    const val LAB = "lab/{code}"
    const val NUTRITION = "nutrition"
    const val ARTICLE = "article/{id}"
    const val FISTULA = "fistula"
    const val SCHEDULE = "schedule"
    const val MEDS = "meds"
    const val MED_EDIT = "med/{id}"
    const val ISLAMIC = "islamic"
    const val SURAH = "surah/{id}"
    const val ENCYCLOPEDIA = "encyclopedia"
    const val MEDIA = "media"
    const val ILLUSTRATION = "illustration/{id}"
    const val VIDEO = "video/{id}"
    const val DIARY = "diary"
    const val FLUIDS = "fluids"
    const val CENTERS = "centers"
    const val REPORTS = "reports"
    const val SUPPORT = "support"
    const val PROFILE = "profile"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"

    fun lab(code: String) = "lab/$code"
    fun article(id: String) = "article/$id"
    fun med(id: Long) = "med/$id"
    fun surah(id: Int) = "surah/$id"
    fun illustration(id: String) = "illustration/$id"
    fun video(id: Long) = "video/$id"

    /** Every screen without arguments, used by the smoke tests. */
    val simple = listOf(
        HOME, SEARCH, EMERGENCY, SETTINGS, WEIGHT, BP, LABS, NUTRITION, FISTULA, SCHEDULE, MEDS,
        ISLAMIC, ENCYCLOPEDIA, MEDIA, DIARY, FLUIDS, CENTERS, REPORTS, SUPPORT, PROFILE, ABOUT, PRIVACY,
    )
}

@Composable
fun NephroRoot(container: AppContainer, pendingRoute: String?, onRouteHandled: () -> Unit, startRoute: String = Routes.HOME) {
    val settings by container.settings.settings.collectAsState()
    CompositionLocalProvider(LocalApp provides container) {
        NephroTheme(settings.theme, settings.textScale) {
            var unlocked by rememberSaveable { mutableStateOf(settings.pinHash == null) }
            when {
                !settings.onboardingDone -> OnboardingScreen()
                !unlocked -> LockScreen(onUnlocked = { unlocked = true })
                else -> MainNav(pendingRoute, onRouteHandled, startRoute)
            }
        }
    }
}

private data class TopItem(val route: String, val icon: ImageVector, val label: Int)

@Composable
fun MainNav(pendingRoute: String?, onRouteHandled: () -> Unit, startRoute: String) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topItems = listOf(
        TopItem(Routes.HOME, Icons.Filled.Home, R.string.nav_home),
        TopItem(Routes.SEARCH, Icons.Filled.Search, R.string.nav_search),
        TopItem(Routes.EMERGENCY, Icons.Filled.Emergency, R.string.nav_emergency),
        TopItem(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
    )
    val showBottom = route == null || topItems.any { it.route == route }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            runCatching { nav.navigate(pendingRoute) { launchSingleTop = true } }
            onRouteHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    topItems.forEach { item ->
                        val selected = route == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTop(item.route) },
                            icon = {
                                Icon(
                                    item.icon, contentDescription = null,
                                    tint = if (item.route == Routes.EMERGENCY) Danger else androidx.compose.ui.graphics.Color.Unspecified,
                                )
                            },
                            label = { Text(stringResource(item.label), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (showBottom) Modifier else Modifier.navigationBarsPadding())
                .imePadding(),
        ) {
            AppNavHost(nav, startRoute)
        }
    }
}

fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(nav: NavHostController, startRoute: String) {
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) }
    NavHost(nav, startDestination = startRoute) {
        composable(Routes.HOME) { HomeScreen(go) }
        composable(Routes.SEARCH) { SearchScreen(go) }
        composable(Routes.EMERGENCY) { EmergencyScreen(go) }
        composable(Routes.SETTINGS) { SettingsScreen(go) }
        composable(Routes.WEIGHT) { WeightScreen(back) }
        composable(Routes.BP) { BpScreen(back, go) }
        composable(Routes.LABS) { LabsScreen(back, go) }
        composable(Routes.LAB) { LabDetailScreen(it.arguments?.getString("code") ?: "K", back) }
        composable(Routes.NUTRITION) { NutritionScreen(back, go) }
        composable(Routes.ARTICLE) { ArticleScreen(it.arguments?.getString("id") ?: "", back, go) }
        composable(Routes.FISTULA) { FistulaScreen(back, go) }
        composable(Routes.SCHEDULE) { ScheduleScreen(back) }
        composable(Routes.MEDS) { MedsScreen(back, go) }
        composable(Routes.MED_EDIT) { MedEditScreen(it.arguments?.getString("id")?.toLongOrNull() ?: 0L, back) }
        composable(Routes.ISLAMIC) { IslamicScreen(back, go) }
        composable(Routes.SURAH) { SurahScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 1, back) }
        composable(Routes.ENCYCLOPEDIA) { ArticleListScreen("encyclopedia", R.string.section_encyclopedia, back, go) }
        composable(Routes.SUPPORT) { ArticleListScreen("support", R.string.section_support, back, go) }
        composable(Routes.MEDIA) { MediaScreen(back, go) }
        composable(Routes.ILLUSTRATION) { IllustrationScreen(it.arguments?.getString("id") ?: "kidney", back) }
        composable(Routes.VIDEO) { VideoScreen(it.arguments?.getString("id")?.toLongOrNull() ?: 0L, back) }
        composable(Routes.DIARY) { DiaryScreen(back) }
        composable(Routes.FLUIDS) { FluidsScreen(back, go) }
        composable(Routes.CENTERS) { CentersScreen(back) }
        composable(Routes.REPORTS) { ReportsScreen(back) }
        composable(Routes.PROFILE) { ProfileScreen(back) }
        composable(Routes.ABOUT) { AboutScreen(back) }
        composable(Routes.PRIVACY) { PrivacyScreen(back) }
    }
}
