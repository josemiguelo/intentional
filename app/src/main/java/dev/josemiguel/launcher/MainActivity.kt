package dev.josemiguel.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.josemiguel.launcher.ui.AppOptionsDialog
import dev.josemiguel.launcher.ui.HomeScreen
import dev.josemiguel.launcher.ui.LauncherTheme
import dev.josemiguel.launcher.ui.PauseScreen
import dev.josemiguel.launcher.ui.SearchScreen
import androidx.compose.foundation.layout.Box

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Pause(val app: AppEntry) : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var apps by mutableStateOf(listOf<AppEntry>())
    private var favorites by mutableStateOf(listOf<String>())
    private var considered by mutableStateOf(setOf<String>())
    private var countsToday by mutableStateOf(0 to 0)
    private var optionsFor by mutableStateOf<AppEntry?>(null)
    private var isDefaultLauncher by mutableStateOf(true)

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshDefaultStatus()
        }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            apps = AppRepository.loadApps(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)

        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LauncherTheme.background)
                    .systemBarsPadding()
            ) {
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        favorites = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } },
                        countsToday = countsToday,
                        isDefaultLauncher = isDefaultLauncher,
                        onRequestDefault = ::requestDefaultLauncher,
                        onOpenApp = ::requestOpen,
                        onLongPressApp = { optionsFor = it },
                        onReorder = { order ->
                            prefs.favorites = order
                            favorites = order
                        },
                        onOpenSearch = { screen = Screen.Search },
                    )

                    Screen.Search -> SearchScreen(
                        apps = apps,
                        considered = considered,
                        onOpenApp = ::requestOpen,
                        onLongPressApp = { optionsFor = it },
                    )

                    is Screen.Pause -> PauseScreen(
                        app = s.app,
                        onOpen = { open(s.app) },
                        onClose = {
                            prefs.incrementPausedClosed()
                            countsToday = prefs.countsToday()
                            screen = Screen.Home
                        },
                    )
                }

                optionsFor?.let { app ->
                    AppOptionsDialog(
                        app = app,
                        isFavorite = app.packageName in favorites,
                        isConsidered = app.packageName in considered,
                        onToggleFavorite = {
                            prefs.toggleFavorite(app.packageName)
                            favorites = prefs.favorites
                        },
                        onToggleConsidered = {
                            prefs.toggleConsidered(app.packageName)
                            considered = prefs.considered
                        },
                        onAppInfo = {
                            startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", app.packageName, null),
                                )
                            )
                        },
                        onUninstall = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_DELETE,
                                    android.net.Uri.fromParts("package", app.packageName, null),
                                )
                            )
                        },
                        onDismiss = { optionsFor = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        apps = AppRepository.loadApps(this)
        favorites = prefs.favorites
        considered = prefs.considered
        countsToday = prefs.countsToday()
        screen = Screen.Home
        refreshDefaultStatus()
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }

    private fun refreshDefaultStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        isDefaultLauncher = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun requestDefaultLauncher() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    /** Pressing home while already in the launcher returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.Home
        optionsFor = null
    }

    private fun requestOpen(app: AppEntry) {
        if (app.packageName in considered) {
            screen = Screen.Pause(app)
        } else {
            open(app)
        }
    }

    private fun open(app: AppEntry) {
        prefs.incrementIntentional()
        countsToday = prefs.countsToday()
        screen = Screen.Home
        AppRepository.launch(this, app)
    }
}
