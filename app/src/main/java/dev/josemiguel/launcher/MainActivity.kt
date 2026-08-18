package dev.josemiguel.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import dev.josemiguel.launcher.ui.AppOptionsDialog
import dev.josemiguel.launcher.ui.HomeScreen
import dev.josemiguel.launcher.ui.LauncherTheme
import dev.josemiguel.launcher.ui.LeavingScreen
import dev.josemiguel.launcher.ui.PauseScreen
import dev.josemiguel.launcher.ui.ReflectionScreen
import dev.josemiguel.launcher.ui.SearchScreen
import dev.josemiguel.launcher.ui.SettingsAppsScreen
import dev.josemiguel.launcher.ui.SettingsScreen
import dev.josemiguel.launcher.ui.TrayScreen
import java.time.LocalTime

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Reflect : Screen
    data object Tray : Screen
    data object Settings : Screen
    data object SettingsApps : Screen
    data object Leaving : Screen
    data class Pause(val app: AppEntry) : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var apps by mutableStateOf(listOf<AppEntry>())
    private var favorites by mutableStateOf(listOf<String>())
    private var considered by mutableStateOf(setOf<String>())
    private var essentials by mutableStateOf(setOf<String>())
    private var batched by mutableStateOf(setOf<String>())
    private var frictionVersion by mutableStateOf(0)
    private var countsToday by mutableStateOf(0 to 0)
    private var optionsFor by mutableStateOf<AppEntry?>(null)
    private var isDefaultLauncher by mutableStateOf(true)
    private var heldReady by mutableStateOf(listOf<HeldNotifications.Held>())
    private var listenerEnabled by mutableStateOf(true)
    private var usageAccessGranted by mutableStateOf(false)
    private var strictMode by mutableStateOf(false)
    private var guardEnabled by mutableStateOf(false)
    private var uninstallProtected by mutableStateOf(false)

    private val adminComponent by lazy {
        android.content.ComponentName(this, UninstallGuardAdmin::class.java)
    }
    private var windDownEnabled by mutableStateOf(true)
    private var windDownStart by mutableStateOf(LocalTime.of(22, 0))
    private var windDownEnd by mutableStateOf(LocalTime.of(6, 0))
    private var pauseSeconds by mutableStateOf(8)
    private var longPauseSeconds by mutableStateOf(25)
    private var dropTimes by mutableStateOf(listOf<LocalTime>())

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
                BackHandler(enabled = screen != Screen.Home) {
                    screen = when (screen) {
                        Screen.SettingsApps, Screen.Leaving -> Screen.Settings
                        else -> Screen.Home
                    }
                }

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        favorites = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } },
                        essentials = essentials,
                        windDownEnabled = windDownEnabled,
                        windDownStart = windDownStart,
                        windDownEnd = windDownEnd,
                        countsToday = countsToday,
                        heldReadyCount = heldReady.size,
                        needsListenerGrant = batched.isNotEmpty() && !listenerEnabled,
                        isDefaultLauncher = isDefaultLauncher,
                        onRequestDefault = ::requestDefaultLauncher,
                        onOpenTray = {
                            heldReady = HeldNotifications.ready(this@MainActivity)
                            screen = Screen.Tray
                        },
                        onEnableFiltering = ::openListenerSettings,
                        onOpenApp = ::requestOpen,
                        onLongPressApp = { optionsFor = it },
                        onReorder = { order ->
                            prefs.favorites = order
                            favorites = order
                        },
                        onOpenSearch = { screen = Screen.Search },
                        onOpenReflection = { screen = Screen.Reflect },
                        onOpenSettings = { screen = Screen.Settings },
                    )

                    Screen.Search -> SearchScreen(
                        apps = apps,
                        considered = considered,
                        onOpenApp = ::requestOpen,
                        onLongPressApp = { optionsFor = it },
                    )

                    Screen.Reflect -> {
                        val installDate = java.time.Instant
                            .ofEpochMilli(packageManager.getPackageInfo(packageName, 0).firstInstallTime)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        ReflectionScreen(
                            history = prefs.history(14)
                                .filter { !it.date.isBefore(installDate) },
                            strictStays = prefs.strictStays,
                        )
                    }

                    Screen.Tray -> TrayScreen(
                        items = heldReady,
                        appLabel = { pkg -> apps.find { it.packageName == pkg }?.label ?: pkg },
                        onOpenApp = { pkg ->
                            apps.find { it.packageName == pkg }?.let(::requestOpen)
                        },
                        onClearAll = {
                            HeldNotifications.clearReady(this@MainActivity)
                            heldReady = emptyList()
                            screen = Screen.Home
                        },
                    )

                    Screen.Settings -> SettingsScreen(
                        windDownEnabled = windDownEnabled,
                        windDownStart = windDownStart,
                        windDownEnd = windDownEnd,
                        pauseSeconds = pauseSeconds,
                        longPauseSeconds = longPauseSeconds,
                        dropTimes = dropTimes,
                        isDefaultLauncher = isDefaultLauncher,
                        usageAccessGranted = usageAccessGranted,
                        listenerEnabled = listenerEnabled,
                        strictMode = strictMode,
                        strictReasons = prefs.strictReasons,
                        guardEnabled = guardEnabled,
                        onEnableStrict = { reasons ->
                            prefs.strictReasons = reasons
                            prefs.strictSince = System.currentTimeMillis()
                            prefs.strictMode = true
                            strictMode = true
                        },
                        onBeginLeaving = { screen = Screen.Leaving },
                        uninstallProtected = uninstallProtected,
                        onToggleUninstallProtection = ::toggleUninstallProtection,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenApps = { screen = Screen.SettingsApps },
                        onToggleWindDown = {
                            prefs.windDownEnabled = !windDownEnabled
                            windDownEnabled = prefs.windDownEnabled
                        },
                        onSetWindDownStart = {
                            prefs.windDownStart = it
                            windDownStart = prefs.windDownStart
                        },
                        onSetWindDownEnd = {
                            prefs.windDownEnd = it
                            windDownEnd = prefs.windDownEnd
                        },
                        onSetPauseSeconds = {
                            prefs.pauseSeconds = it
                            pauseSeconds = prefs.pauseSeconds
                        },
                        onSetLongPauseSeconds = {
                            prefs.longPauseSeconds = it
                            longPauseSeconds = prefs.longPauseSeconds
                        },
                        onSetDropTimes = {
                            prefs.dropTimes = it
                            dropTimes = prefs.dropTimes
                        },
                        onRequestDefault = ::requestDefaultLauncher,
                        onGrantUsage = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onGrantListener = ::openListenerSettings,
                        onClearHeld = {
                            HeldNotifications.clearAll(this@MainActivity)
                            heldReady = emptyList()
                        },
                        onResetToday = {
                            prefs.resetToday()
                            countsToday = prefs.countsToday()
                        },
                    )

                    Screen.SettingsApps -> SettingsAppsScreen(
                        apps = apps,
                        favorites = favorites,
                        considered = considered,
                        essentials = essentials,
                        batched = batched,
                        frictionVersion = frictionVersion,
                        frictionFor = { prefs.frictionFor(it) },
                        onToggleFavorite = {
                            prefs.toggleFavorite(it)
                            favorites = prefs.favorites
                        },
                        onToggleConsidered = {
                            prefs.toggleConsidered(it)
                            considered = prefs.considered
                            batched = prefs.batched
                        },
                        onToggleEssential = {
                            prefs.toggleEssential(it)
                            essentials = prefs.essentials
                        },
                        onToggleBatched = {
                            prefs.toggleBatched(it)
                            batched = prefs.batched
                        },
                        onCycleFriction = {
                            prefs.cycleFriction(it)
                            frictionVersion++
                        },
                    )

                    Screen.Leaving -> {
                        val daysOn = if (prefs.strictSince > 0L) {
                            ((System.currentTimeMillis() - prefs.strictSince) / 86_400_000L)
                                .toInt().coerceIn(0, 365)
                        } else 0
                        LeavingScreen(
                            reasons = prefs.strictReasons,
                            daysOn = daysOn,
                            pausedClosedSince = prefs.history((daysOn + 1).coerceAtMost(60))
                                .sumOf { it.pausedClosed },
                            staysCount = prefs.strictStays,
                            onStay = {
                                prefs.strictStays += 1
                                screen = Screen.Home
                            },
                            onTurnOff = {
                                prefs.strictMode = false
                                strictMode = false
                                screen = Screen.Settings
                            },
                        )
                    }

                    is Screen.Pause -> PauseScreen(
                        app = s.app,
                        friction = prefs.frictionFor(s.app.packageName),
                        windDown = windDownActive(),
                        windDownEndLabel = windDownEnd.toString(),
                        baseSeconds = pauseSeconds,
                        longSeconds = longPauseSeconds,
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
                        isEssential = app.packageName in essentials,
                        frictionLabel = frictionVersion.let { prefs.frictionFor(app.packageName).label },
                        onToggleEssential = {
                            prefs.toggleEssential(app.packageName)
                            essentials = prefs.essentials
                        },
                        onCycleFriction = {
                            prefs.cycleFriction(app.packageName)
                            frictionVersion++
                        },
                        onToggleFavorite = {
                            prefs.toggleFavorite(app.packageName)
                            favorites = prefs.favorites
                        },
                        onToggleConsidered = {
                            prefs.toggleConsidered(app.packageName)
                            considered = prefs.considered
                            batched = prefs.batched
                        },
                        onAppInfo = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.packageName, null),
                                )
                            )
                        },
                        onUninstall = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_DELETE,
                                    Uri.fromParts("package", app.packageName, null),
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
        essentials = prefs.essentials
        batched = prefs.batched
        windDownEnabled = prefs.windDownEnabled
        windDownStart = prefs.windDownStart
        windDownEnd = prefs.windDownEnd
        pauseSeconds = prefs.pauseSeconds
        longPauseSeconds = prefs.longPauseSeconds
        dropTimes = prefs.dropTimes
        countsToday = prefs.countsToday()
        heldReady = HeldNotifications.ready(this)
        listenerEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
        usageAccessGranted = UsageStatsRepository.hasPermission(this)
        strictMode = prefs.strictMode
        uninstallProtected = getSystemService(android.app.admin.DevicePolicyManager::class.java)
            .isAdminActive(adminComponent)
        guardEnabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(packageName) == true
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

    /** Pressing home while already in the launcher returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.Home
        optionsFor = null
    }

    private fun windDownActive(): Boolean =
        windDownEnabled && WindDown.isActive(windDownStart, windDownEnd)

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

    private fun toggleUninstallProtection() {
        val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            // Removing protection is the commitment being undone: only allowed
            // once strict mode has been turned off through the leaving flow.
            if (!strictMode) {
                dpm.removeActiveAdmin(adminComponent)
                uninstallProtected = false
            }
        } else {
            dev.josemiguel.launcher.guard.GuardBypass.allowAdminScreen()
            startActivity(
                Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(
                        android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        adminComponent,
                    )
                    putExtra(
                        android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Blocks uninstalling Intentional. Removable in the app's " +
                            "settings once strict mode is off.",
                    )
                }
            )
        }
    }

    private fun openListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
