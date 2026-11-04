package dev.josemiguel.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import dev.josemiguel.launcher.ui.BlockedScreen
import dev.josemiguel.launcher.ui.GroupsScreen
import dev.josemiguel.launcher.ui.HomeScreen
import dev.josemiguel.launcher.ui.LauncherTheme
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
    data object Groups : Screen
    data class Pause(val app: AppEntry) : Screen
    data class Blocked(val app: AppEntry) : Screen
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
    private var groupsVersion by mutableStateOf(0)
    private var countsToday by mutableStateOf(0 to 0)
    private var optionsFor by mutableStateOf<AppEntry?>(null)
    private var isDefaultLauncher by mutableStateOf(true)
    private var heldReady by mutableStateOf(listOf<HeldNotifications.Held>())
    private var listenerEnabled by mutableStateOf(true)
    private var usageAccessGranted by mutableStateOf(false)
    // Strict mode is mandatory (always on); kept as a field only for readability.
    private val strictMode = true
    private var guardEnabled by mutableStateOf(false)
    private var uninstallProtected by mutableStateOf(false)
    private var exactAlarmsAllowed by mutableStateOf(true)

    private val notifPermRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LauncherTheme.background)
                    .systemBarsPadding()
            ) {
                // Always consume back so the home screen never slides away as
                // the system tries to background the launcher; on Home it's a
                // deliberate no-op.
                BackHandler(enabled = true) {
                    screen = when (screen) {
                        Screen.Home -> Screen.Home
                        Screen.SettingsApps, Screen.Groups -> Screen.Settings
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
                        guardEnabled = guardEnabled,
                        uninstallProtected = uninstallProtected,
                        onToggleUninstallProtection = ::toggleUninstallProtection,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenApps = { screen = Screen.SettingsApps },
                        onOpenGroups = { screen = Screen.Groups },
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
                            DropScheduler.scheduleNext(this@MainActivity)
                        },
                        exactAlarmsAllowed = exactAlarmsAllowed,
                        onGrantExactAlarm = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            }
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
                        groupNames = prefs.groups.map { it.name },
                        groupNameOf = { prefs.groupOf(it)?.name },
                        onAssignGroup = { pkg, groupName ->
                            prefs.assignToGroup(pkg, groupName)
                            frictionVersion++
                        },
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

                    Screen.Groups -> GroupsScreen(
                        groups = groupsVersion.let { prefs.groups },
                        canChangeLimit = { prefs.canChangeGroupLimit(it) },
                        daysUntilChangeable = { prefs.daysUntilChangeable(it) },
                        onAddGroup = { prefs.addGroup(it); groupsVersion++ },
                        onDeleteGroup = { prefs.deleteGroup(it); groupsVersion++ },
                        onSetLimit = { name, minutes ->
                            prefs.setGroupLimit(name, minutes)
                            groupsVersion++
                        },
                    )

                    is Screen.Blocked -> {
                        val group = prefs.groupOf(s.app.packageName)
                        BlockedScreen(
                            groupName = group?.name ?: "",
                            limitMinutes = group?.limitMinutes ?: 0,
                            onClose = { screen = Screen.Home },
                        )
                    }

                    is Screen.Pause -> PauseScreen(
                        app = s.app,
                        group = if (usageAccessGranted) prefs.groupOf(s.app.packageName) else null,
                        friction = prefs.frictionFor(s.app.packageName),
                        windDown = windDownActive(),
                        windDownEndLabel = windDownEnd.toString(),
                        baseSeconds = pauseSeconds,
                        longSeconds = longPauseSeconds,
                        onOpen = { openOrBlock(s.app) },
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
        uninstallProtected = getSystemService(android.app.admin.DevicePolicyManager::class.java)
            .isAdminActive(adminComponent)
        guardEnabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(packageName) == true
        exactAlarmsAllowed = DropScheduler.exactAlarmsAllowed(this)
        DropScheduler.scheduleNext(this)
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
            // Strict mode is mandatory, so the in-app removal path (gated on
            // strict mode being off) is never reachable: uninstall protection,
            // once on, can only be removed with the adb escape hatch in the
            // README. Kept here for clarity.
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
        when {
            // Considered apps breathe first; the time-limit block is only
            // revealed after the pause, when they choose to open.
            app.packageName in considered -> screen = Screen.Pause(app)
            usageAccessGranted && isOverLimit(app.packageName) -> screen = Screen.Blocked(app)
            else -> open(app)
        }
    }

    private fun openOrBlock(app: AppEntry) {
        if (usageAccessGranted && isOverLimit(app.packageName)) {
            screen = Screen.Blocked(app)
        } else {
            open(app)
        }
    }

    private fun isOverLimit(packageName: String): Boolean {
        val group = prefs.groupOf(packageName) ?: return false
        val used = UsageStatsRepository.groupUsedMinutes(this, group)
        return used >= group.limitMinutes
    }

    private fun open(app: AppEntry) {
        prefs.incrementIntentional()
        countsToday = prefs.countsToday()
        screen = Screen.Home
        AppRepository.launch(this, app)
    }
}
