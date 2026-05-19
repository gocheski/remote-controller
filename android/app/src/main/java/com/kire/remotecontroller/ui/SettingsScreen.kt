package com.kire.remotecontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kire.remotecontroller.BuildConfig
import com.kire.remotecontroller.epg.EpgRepository

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val host by viewModel.selectedHost.collectAsState()
    val tvName by viewModel.tvName.collectAsState()
    var hostInput by rememberSaveable { mutableStateOf(host ?: "") }
    LaunchedEffect(host) { hostInput = host ?: "" }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }
    var confirmClearEpg by rememberSaveable { mutableStateOf(false) }
    var epgExpanded by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenContentInsets()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("App settings", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(status)

        Text("TV", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        tvName?.let { Text("Name: $it") }
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("TV IP address") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.updateTvHost(hostInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = hostInput.isNotBlank(),
        ) {
            Text("Save IP")
        }
        Button(onClick = { viewModel.testConnection() }, modifier = Modifier.fillMaxWidth()) {
            Text("Test connection")
        }

        Text("Pairing", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Button(onClick = onOpenPairing, modifier = Modifier.fillMaxWidth()) {
            Text("Re-pair with TV")
        }
        Button(onClick = { viewModel.clearPairingOnly() }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear pairing credentials")
        }

        Text("TV Guide", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        EpgSourcePicker(
            selectedUrl = viewModel.xmlTvUrl,
            expanded = epgExpanded,
            onExpandedChange = { epgExpanded = it },
            onSelect = { viewModel.setXmlTvUrl(it) },
        )

        Text("Storage", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Button(onClick = { confirmClearEpg = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear TV guide cache")
        }
        Button(onClick = { confirmClearAll = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear all app data")
        }

        Text("About", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }

    if (confirmClearEpg) {
        AlertDialog(
            onDismissRequest = { confirmClearEpg = false },
            title = { Text("Clear guide cache?") },
            text = { Text("Downloaded programmes will be removed. Tags are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearEpgCache()
                    confirmClearEpg = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearEpg = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all data?") },
            text = { Text("Removes pairing, TV IP, guide, and tags. You will need to set up again.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    confirmClearAll = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun EpgSourcePicker(
    selectedUrl: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.layout.Box {
        Button(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                EpgRepository.PRESET_URLS.firstOrNull { it.first == selectedUrl }?.second
                    ?: "EPG source",
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            EpgRepository.PRESET_URLS.forEach { (url, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(url)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
