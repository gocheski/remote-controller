package com.kire.remotecontroller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kire.remotecontroller.discovery.DiscoveredTv

@Composable
fun DiscoverScreen(
    viewModel: AppViewModel,
    onSelected: () -> Unit,
) {
    val tvs by viewModel.tvs.collectAsState()
    val status by viewModel.status.collectAsState()
    var manualHost by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("My TV", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(status)
        Button(onClick = { viewModel.scan() }, modifier = Modifier.fillMaxWidth()) {
            Text("Scan network")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(tvs) { tv ->
                TvCard(tv) {
                    viewModel.selectTv(tv)
                    onSelected()
                }
            }
        }
        OutlinedTextField(
            value = manualHost,
            onValueChange = { manualHost = it },
            label = { Text("TV IP address") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (manualHost.isNotBlank()) {
                    viewModel.selectManual(manualHost.trim())
                    onSelected()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = manualHost.isNotBlank(),
        ) {
            Text("Connect manually")
        }
    }
}

@Composable
private fun TvCard(tv: DiscoveredTv, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(tv.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text("${tv.host}:${tv.port}")
        }
    }
}
