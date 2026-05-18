package com.kire.remotecontroller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kire.remotecontroller.epg.EpgRepository
import com.kire.remotecontroller.epg.ProgrammeEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val programmes by viewModel.epgProgrammes.collectAsState()
    val current by viewModel.currentChannel.collectAsState()
    val status by viewModel.status.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf(EpgRepository.DEFAULT_XMLTV) }

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentChannel()
        viewModel.loadCachedEpg()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = { viewModel.refreshEpg() }) { Text("Refresh guide") }
        }
        current?.let { Text("Now: $it") }
        Text(status)

        Box {
            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    EpgRepository.PRESET_URLS.firstOrNull { it.first == selectedUrl }?.second ?: "EPG source",
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EpgRepository.PRESET_URLS.forEach { (url, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedUrl = url
                            viewModel.setXmlTvUrl(url)
                            expanded = false
                        },
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(programmes) { item ->
                ProgrammeRow(item) { viewModel.watchTv() }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(item: ProgrammeEntity, onSelect: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.channelName, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Text(item.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(
                "${fmt.format(Date(item.startMillis))} – ${fmt.format(Date(item.endMillis))}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            if (item.description.isNotBlank()) {
                Text(item.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}
