package com.kire.remotecontroller.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kire.remotecontroller.epg.ChannelGuideRow
import com.kire.remotecontroller.epg.GenreFilter
import com.kire.remotecontroller.epg.ProgrammeSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val gridRows by viewModel.epgGridRows.collectAsState()
    val current by viewModel.currentChannel.collectAsState()
    val status by viewModel.status.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val genre by viewModel.genreFilter.collectAsState()
    val userTags by viewModel.userTags.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentChannel()
        viewModel.loadCachedEpg()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenContentInsets()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = { viewModel.refreshEpg() }) { Text("Refresh") }
        }
        current?.let { Text("Now: $it", style = MaterialTheme.typography.bodyMedium) }
        Text(status, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search channel or programme") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GenreFilter.entries.forEach { g ->
                FilterChip(
                    selected = genre == g,
                    onClick = { viewModel.setGenreFilter(g) },
                    label = { Text(g.label) },
                )
            }
        }

        if (userTags.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedTagId == null,
                    onClick = { viewModel.setSelectedTagId(null) },
                    label = { Text("All tags") },
                )
                userTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = {
                            viewModel.setSelectedTagId(if (selectedTagId == tag.id) null else tag.id)
                        },
                        label = { Text(tag.name) },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(gridRows, key = { it.channelId }) { row ->
                ChannelGuideRowItem(
                    row = row,
                    onChannelClick = { viewModel.watchTv() },
                    onSlotClick = { viewModel.watchTv() },
                    onTagProgramme = { stableKey, tag -> viewModel.addTagToProgramme(stableKey, tag) },
                    onRemoveTag = { stableKey, tag -> viewModel.removeTagFromProgramme(stableKey, tag) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelGuideRowItem(
    row: ChannelGuideRow,
    onChannelClick: () -> Unit,
    onSlotClick: () -> Unit,
    onTagProgramme: (String, String) -> Unit,
    onRemoveTag: (String, String) -> Unit,
) {
    var tagDialogSlot by remember { mutableStateOf<ProgrammeSlot?>(null) }
    var newTag by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val slotScroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(88.dp)
                .combinedClickable(onClick = onChannelClick)
                .padding(8.dp),
        ) {
            Text(
                text = row.channelNumber?.let { "$it" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = row.channelName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(slotScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.slots.forEach { slot ->
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .combinedClickable(
                            onClick = onSlotClick,
                            onLongClick = { tagDialogSlot = slot },
                        ),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            timeFmt.format(Date(slot.startMillis)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            slot.title.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        slot.tagNames.take(2).forEach { tag ->
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    tagDialogSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { tagDialogSlot = null },
            title = { Text("Tags for ${slot.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (slot.tagNames.isNotEmpty()) {
                        slot.tagNames.forEach { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(tag)
                                TextButton(onClick = {
                                    onRemoveTag(slot.stableKey, tag)
                                    tagDialogSlot = null
                                }) { Text("Remove") }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTag.isNotBlank()) {
                            onTagProgramme(slot.stableKey, newTag)
                            newTag = ""
                        }
                        tagDialogSlot = null
                    },
                ) { Text("Add tag") }
            },
            dismissButton = {
                TextButton(onClick = { tagDialogSlot = null }) { Text("Close") }
            },
        )
    }
}
