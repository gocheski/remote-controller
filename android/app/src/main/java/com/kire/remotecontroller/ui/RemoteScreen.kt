package com.kire.remotecontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RemoteScreen(
    viewModel: AppViewModel,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val channelBuffer by viewModel.channelBuffer.collectAsState()
    var textInput by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenContentInsets()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Remote", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onOpenSettings) {
                Text("App")
            }
        }
        Text(status, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = { viewModel.powerOff() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
            Text("Shut down")
        }

        ShortcutRow(viewModel, onOpenGuide)

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { viewModel.key("MUTE") }) {
                Icon(Icons.Default.VolumeMute, contentDescription = "Mute")
            }
            IconButton(onClick = { viewModel.key("VOL_DOWN") }) {
                Icon(Icons.Default.VolumeDown, contentDescription = "Volume down")
            }
            IconButton(onClick = { viewModel.key("VOL_UP") }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Volume up")
            }
        }

        Dpad(
            viewModel = viewModel,
            onOk = {
                if (channelBuffer.isNotEmpty()) {
                    viewModel.sendChannelBuffer()
                } else {
                    viewModel.key("ENTER")
                }
            },
        )

        Text("Channel number", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (channelBuffer.isEmpty()) "Enter digits, then OK" else channelBuffer,
                style = MaterialTheme.typography.headlineMedium,
            )
            TextButton(onClick = { viewModel.clearChannelBuffer() }) {
                Text("Clear")
            }
        }
        NumericKeypad(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.recordVoiceAndSend() }) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Text("Voice")
            }
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Keyboard text") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.text(textInput)
                textInput = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = textInput.isNotBlank(),
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = null)
            Text("Send text")
        }
    }
}

@Composable
private fun ShortcutRow(viewModel: AppViewModel, onOpenGuide: () -> Unit) {
    val shortcuts = listOf(
        "Sources" to { viewModel.sources() },
        "TV" to { viewModel.watchTv() },
        "Settings" to { viewModel.tvSettings() },
        "Ambilight" to { viewModel.ambilight() },
        "Guide" to onOpenGuide,
        "YouTube" to { viewModel.youtube() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shortcuts.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, action) ->
                    if (label == "YouTube") {
                        Button(
                            onClick = action,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YoutubeRed,
                                contentColor = OnYoutubeRed,
                            ),
                        ) {
                            Text("YouTube")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = action,
                            modifier = Modifier.weight(1f),
                        ) {
                            when (label) {
                                "TV" -> {
                                    Icon(Icons.Default.Tv, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                }
                                "Settings" -> {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dpad(viewModel: AppViewModel, onOk: () -> Unit) {
    val dpadButtonHeight = 48.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { viewModel.key("UP") },
            modifier = Modifier
                .fillMaxWidth()
                .height(dpadButtonHeight),
        ) {
            Text("▲")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = { viewModel.key("LEFT") },
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Text("◀")
            }
            FilledTonalButton(
                onClick = onOk,
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Text("OK")
            }
            FilledTonalButton(
                onClick = { viewModel.key("RIGHT") },
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Text("▶")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = { viewModel.key("BACK") },
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            FilledTonalButton(
                onClick = { viewModel.key("DOWN") },
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Text("▼")
            }
            FilledTonalButton(
                onClick = { viewModel.key("HOME") },
                modifier = Modifier
                    .weight(1f)
                    .height(dpadButtonHeight),
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home")
            }
        }
    }
}

@Composable
private fun NumericKeypad(viewModel: AppViewModel) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { digit ->
                FilledTonalButton(
                    onClick = { viewModel.appendChannelDigit(digit) },
                    modifier = Modifier.weight(1f),
                ) { Text(digit) }
            }
        }
    }
    FilledTonalButton(
        onClick = { viewModel.appendChannelDigit("0") },
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        Text("0", style = MaterialTheme.typography.headlineMedium)
    }
}
