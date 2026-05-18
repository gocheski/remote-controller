package com.kire.remotecontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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

@Composable
fun PairingScreen(
    viewModel: AppViewModel,
    onDone: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    var philipsPin by rememberSaveable { mutableStateOf("") }
    var atvPin by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pair with TV", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(status)

        Text("Step 1 — Philips TV (JointSpace)")
        Button(onClick = { viewModel.startPhilipsPairing() }, modifier = Modifier.fillMaxWidth()) {
            Text("Show Philips PIN on TV")
        }
        OutlinedTextField(
            value = philipsPin,
            onValueChange = { philipsPin = it },
            label = { Text("PIN on TV screen") },
            placeholder = { Text("e.g. 1234") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.pairPhilips(philipsPin) },
            modifier = Modifier.fillMaxWidth(),
            enabled = philipsPin.isNotBlank(),
        ) {
            Text("Confirm Philips pairing")
        }

        Text("Step 2 — Android TV Remote")
        OutlinedTextField(
            value = atvPin,
            onValueChange = { atvPin = it },
            label = { Text("6-char hex PIN from TV") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.pairAtv(atvPin) },
            modifier = Modifier.fillMaxWidth(),
            enabled = atvPin.length == 6,
        ) {
            Text("Confirm Android TV pairing")
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Continue to remote")
        }
    }
}
