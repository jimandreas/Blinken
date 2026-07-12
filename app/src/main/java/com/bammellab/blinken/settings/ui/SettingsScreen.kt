package com.bammellab.blinken.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bammellab.blinken.notification.canUseFullScreenIntent
import com.bammellab.blinken.notification.isNotificationAccessGranted
import com.bammellab.blinken.notification.isPostNotificationsGranted
import com.bammellab.blinken.settings.AllowlistRepository
import com.bammellab.blinken.settings.AppEntry
import com.bammellab.blinken.settings.AppSettings
import com.bammellab.blinken.settings.InstalledApp
import com.bammellab.blinken.settings.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AllowlistRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsState(initial = AppSettings.DEFAULT)
    val installedAppsProvider = remember { InstalledAppsProvider(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showInfoDescriptionDialog by remember { mutableStateOf(false) }

    // Loaded once off the main thread; installed apps rarely change during a settings session.
    val allInstalledApps by produceState(initialValue = emptyList<InstalledApp>(), installedAppsProvider) {
        value = withContext(Dispatchers.IO) { installedAppsProvider.listInstalledApps() }
    }
    val allInstalledByPackage = remember(allInstalledApps) { allInstalledApps.associateBy { it.packageName } }

    var notificationAccessGranted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var postNotificationsGranted by remember { mutableStateOf(isPostNotificationsGranted(context)) }
    var fullScreenIntentAllowed by remember { mutableStateOf(canUseFullScreenIntent(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted = isNotificationAccessGranted(context)
                postNotificationsGranted = isPostNotificationsGranted(context)
                fullScreenIntentAllowed = canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Blinken") },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(imageVector = Icons.Filled.Info, contentDescription = "About")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PermissionBanner(
                notificationAccessGranted = notificationAccessGranted,
                postNotificationsGranted = postNotificationsGranted,
                fullScreenIntentAllowed = fullScreenIntentAllowed,
                onPostNotificationsResult = { granted -> postNotificationsGranted = granted },
                modifier = Modifier.padding(16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Eco mode (limit repeat flashes)")
                Switch(
                    checked = settings.ecoModeEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setEcoModeEnabled(enabled) }
                    },
                )
            }
            LazyColumn {
                items(settings.entries, key = { it.packageName }) { entry ->
                    AppEntryRow(
                        entry = entry,
                        icon = allInstalledByPackage[entry.packageName],
                        onColorSelected = { argb ->
                            scope.launch { repository.addOrUpdate(entry.copy(colorArgb = argb)) }
                        },
                        onDurationSelected = { ms ->
                            scope.launch { repository.addOrUpdate(entry.copy(durationMs = ms)) }
                        },
                        onEnabledChanged = { enabled ->
                            scope.launch { repository.addOrUpdate(entry.copy(enabled = enabled)) }
                        },
                        onRemove = {
                            scope.launch { repository.remove(entry.packageName) }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val excluded = remember(settings.entries) { settings.entries.map { it.packageName }.toSet() }
        val availableApps = remember(allInstalledApps, excluded) {
            allInstalledApps.filter { it.packageName !in excluded }
        }
        AddAppDialog(
            availableApps = availableApps,
            onAppSelected = { app: InstalledApp ->
                scope.launch {
                    repository.addOrUpdate(
                        AppEntry(
                            packageName = app.packageName,
                            label = app.label,
                            colorArgb = settings.globalDefaultColorArgb,
                            durationMs = settings.globalDefaultDurationMs,
                        )
                    )
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showInfoDialog) {
        InfoDialog(
            onDismiss = { showInfoDialog = false },
            onShowDescription = {
                showInfoDialog = false
                showInfoDescriptionDialog = true
            },
        )
    }
    if (showInfoDescriptionDialog) {
        InfoDescriptionDialog(
            onDismiss = {
                showInfoDescriptionDialog = false
                showInfoDialog = true
            },
        )
    }
}
