package com.riftrecall.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riftrecall.app.AppViewModel
import com.riftrecall.app.CardsState
import com.riftrecall.app.data.Entry
import com.riftrecall.app.data.Pool
import com.riftrecall.app.data.Settings
import com.riftrecall.app.logic.Mode
import com.riftrecall.app.logic.SessionBuilder
import com.riftrecall.app.logic.Srs
import com.riftrecall.app.data.Card as GameCard

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onStart: (Mode) -> Unit,
    onStudy: () -> Unit,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
) {
    val cardsState by vm.cards.collectAsState()
    val settings by vm.settings.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        when (val cs = cardsState) {
            is CardsState.Loading -> Column(Modifier.padding(pad)) { CenterMessage(cs.msg, loading = true) }
            is CardsState.Error -> Column(
                Modifier.padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Couldn't load the card list", style = MaterialTheme.typography.titleLarge)
                Text(cs.msg)
                Text("The card data is downloaded once and then stored for offline use.")
                Button(onClick = { vm.refresh() }) { Text("Try again") }
            }
            is CardsState.Ready -> HomeContent(pad, cs.all, settings, progress, vm, onStart, onStudy, onCollection, onSettings)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(
    pad: PaddingValues,
    all: List<GameCard>,
    settings: Settings,
    progress: Map<String, Entry>,
    vm: AppViewModel,
    onStart: (Mode) -> Unit,
    onStudy: () -> Unit,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
) {
    val pool = remember(all, settings.sets, settings.includeExtras) { Pool.filter(all, settings) }
    val counts = remember(pool, progress) {
        val now = System.currentTimeMillis()
        Mode.values().associateWith { SessionBuilder.counts(pool, it, progress, now) }
    }
    val setIds = remember(all) { all.map { it.setId }.filter { it.isNotBlank() }.distinct().sorted() }
    val mastered = remember(pool, progress) {
        if (pool.isEmpty()) 0f else pool.map { Srs.cardMastery(it, progress) }.average().toFloat()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = pad.calculateTopPadding() + 12.dp,
            bottom = pad.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Rift Recall", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${pool.size} cards in your deck - overall mastery ${(mastered * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MasteryBar(mastered)
            }
        }

        if (!settings.cropReviewed) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("First, check the art crop", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The quiz shows only the artwork. Make sure the crop hides the name, cost and text.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onSettings) { Text("Open crop settings") }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sets", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.sets.isEmpty(),
                        onClick = { vm.update { it.copy(sets = emptySet()) } },
                        label = { Text("All") },
                    )
                    for (id in setIds) {
                        FilterChip(
                            selected = id in settings.sets,
                            onClick = {
                                vm.update { s -> s.copy(sets = if (id in s.sets) s.sets - id else s.sets + id) }
                            },
                            label = { Text(id) },
                        )
                    }
                }
            }
        }

        item { Text("Quiz modes", style = MaterialTheme.typography.titleMedium) }
        items(Mode.values().toList()) { mode ->
            val c = counts[mode]
            Card(
                onClick = { onStart(mode) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(mode.title, style = MaterialTheme.typography.titleMedium)
                    Text(mode.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c != null && (c.due > 0 || c.fresh > 0)) {
                            if (c.due > 0) Pill("${c.due} due", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            if (c.fresh > 0) Pill("${c.fresh} new", color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                        } else {
                            Pill("All caught up - practise ahead")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onStudy) { Text("Flashcards") }
                OutlinedButton(onClick = onCollection) { Text("Collection") }
                OutlinedButton(onClick = onSettings) { Text("Settings") }
            }
        }
    }
}
