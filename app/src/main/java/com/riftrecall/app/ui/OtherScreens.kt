package com.riftrecall.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riftrecall.app.AppViewModel
import com.riftrecall.app.CardsState
import com.riftrecall.app.data.Crop
import com.riftrecall.app.data.Pool
import com.riftrecall.app.data.Settings
import com.riftrecall.app.logic.Srs
import kotlin.math.roundToInt
import com.riftrecall.app.data.Card as GameCard

// ---------------------------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------------------------

@Composable
fun SummaryScreen(vm: AppViewModel, onHome: () -> Unit, onAgain: () -> Unit) {
    val session by vm.session.collectAsState()
    val settings by vm.settings.collectAsState()
    val s = session
    Scaffold { pad ->
        if (s == null) {
            Column(Modifier.padding(pad)) { CenterMessage("Session finished.") }
            return@Scaffold
        }
        val pct = if (s.answered == 0) 0 else (100f * s.perfect / s.answered).roundToInt()
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 16.dp,
                bottom = pad.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Session complete", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("${s.answered} cards - ${s.perfect} right first time ($pct%)")
                }
            }
            if (s.missed.isNotEmpty()) {
                item { Text("Review these - they'll come back soon", style = MaterialTheme.typography.titleMedium) }
                items(s.missed, key = { it.id }) { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardArt(c, settings.cropFor(c), Modifier.width(96.dp))
                        Column {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(c.supertype, c.type).joinToString(" ") +
                                    if (c.domains.isNotEmpty()) " - " + c.domains.joinToString(", ") else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item { Text("No misses - nicely done.") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onAgain) { Text("Another round") }
                    OutlinedButton(onClick = onHome) { Text("Home") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Flashcards: art on the front, everything on the back. No scoring - just exposure.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cs by vm.cards.collectAsState()
    val settings by vm.settings.collectAsState()
    val all = (cs as? CardsState.Ready)?.all.orEmpty()
    val pool = remember(all, settings.sets, settings.includeExtras) { Pool.filter(all, settings) }
    var shuffle by rememberSaveable { mutableStateOf(false) }
    var seed by rememberSaveable { mutableIntStateOf(1) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val deck = remember(pool, shuffle, seed) {
        if (shuffle) pool.shuffled(kotlin.random.Random(seed)) else pool
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (deck.isNotEmpty()) Text("${index.coerceIn(0, deck.lastIndex) + 1} / ${deck.size}")
                    IconButton(onClick = {
                        shuffle = true
                        seed += 1
                        index = 0
                        flipped = false
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Shuffle") }
                },
            )
        },
    ) { pad ->
        if (deck.isEmpty()) {
            Column(Modifier.padding(pad)) { CenterMessage("No cards loaded yet.") }
            return@Scaffold
        }
        val i = index.coerceIn(0, deck.lastIndex)
        val card = deck[i]
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !shuffle, onClick = { shuffle = false; index = 0; flipped = false }, label = { Text("In order") })
                FilterChip(selected = shuffle, onClick = { shuffle = true; index = 0; flipped = false }, label = { Text("Shuffled") })
            }
            if (!flipped) {
                CardArt(card, settings.cropFor(card), Modifier.fillMaxWidth().clickable { flipped = true })
                Text(
                    "Try to recall the name, cost and ability, then reveal.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CardFull(card, Modifier.fillMaxWidth().clickable { flipped = false })
                CardFacts(card)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { index = (i - 1).coerceAtLeast(0); flipped = false }, enabled = i > 0) { Text("Previous") }
                Button(onClick = { flipped = !flipped }) { Text(if (flipped) "Hide" else "Reveal") }
                OutlinedButton(
                    onClick = { index = (i + 1).coerceAtMost(deck.lastIndex); flipped = false },
                    enabled = i < deck.lastIndex,
                ) { Text("Next") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collection: reference list with mastery per card
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cs by vm.cards.collectAsState()
    val settings by vm.settings.collectAsState()
    val progress by vm.progress.collectAsState()
    val all = (cs as? CardsState.Ready)?.all.orEmpty()
    val pool = remember(all, settings.sets, settings.includeExtras) { Pool.filter(all, settings) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<GameCard?>(null) }
    val list = remember(pool, query) {
        if (query.isBlank()) pool else pool.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collection (${list.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open = c },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardArt(c, settings.cropFor(c), Modifier.width(88.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(c.supertype, c.type).joinToString(" ") +
                                    if (c.domains.isNotEmpty()) " - " + c.domains.joinToString(", ") else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            MasteryBar(Srs.cardMastery(c, progress))
                        }
                    }
                }
            }
        }
    }

    open?.let { card ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardFull(card, Modifier.fillMaxWidth())
                    CardFacts(card)
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Settings (including the visual art-crop calibration)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val cs by vm.cards.collectAsState()
    val all = (cs as? CardsState.Ready)?.all.orEmpty()
    var landscape by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val sample = remember(all, landscape) { all.firstOrNull { it.landscape == landscape } }
    val crop = if (landscape) s.landscape else s.portrait

    fun setCrop(c: Crop) = vm.update { if (landscape) it.copy(landscape = c) else it.copy(portrait = c) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Studying", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            SliderRow("New cards per session", s.newPerSession, 1, 30) { v -> vm.update { it.copy(newPerSession = v) } }
            SliderRow("Max cards per session", s.sessionSize, 5, 60) { v -> vm.update { it.copy(sessionSize = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include runes and tokens")
                    Text(
                        "Usually have no text or stats, so they're off by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.includeExtras, onCheckedChange = { v -> vm.update { it.copy(includeExtras = v) } })
            }

            Text("Art crop", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Quizzes show only the area inside the outline. Drag the sliders until the name, cost and rules text are all outside it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("Portrait cards") })
                FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("Landscape cards") })
            }
            if (sample == null) {
                Text("No ${if (landscape) "landscape" else "portrait"} card available to preview.")
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CropPreview(sample, crop)
                }
            }
            CropSlider("Left", crop.l, 0f, 0.4f) { setCrop(crop.copy(l = it)) }
            CropSlider("Top", crop.t, 0f, 0.5f) { setCrop(crop.copy(t = it)) }
            CropSlider("Right", crop.r, 0.6f, 1f) { setCrop(crop.copy(r = it)) }
            CropSlider("Bottom", crop.b, 0.3f, 1f) { setCrop(crop.copy(b = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.update { it.copy(cropReviewed = true) } }) { Text("Looks good") }
                OutlinedButton(onClick = {
                    setCrop(if (landscape) Settings.DEFAULT_LANDSCAPE else Settings.DEFAULT_PORTRAIT)
                }) { Text("Reset crop") }
            }

            Text("Data", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            val info by vm.dataInfo.collectAsState()
            Text("${all.size} cards stored on this device.")
            Text(
                "Sets loaded: " + all.map { it.setId }.filter { it.isNotBlank() }.distinct().sorted().joinToString(", ").ifEmpty { "none" },
            )
            if (info.isNotBlank()) {
                Text("Source: $info", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refresh(); onBack() }) { Text("Re-download cards") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("Reset progress") }
            }
            Text(
                "Card data from the RiftHunt API (fallback: Riftcodex); art is streamed from the image host and " +
                    "cached on your device. Rift Recall is an unofficial fan project, not affiliated with Riot Games.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("Every card goes back to \"new\". This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.resetProgress(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Int, lo: Int, hi: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = lo.toFloat()..hi.toFloat(),
            steps = hi - lo - 1,
        )
    }
}

@Composable
private fun CropSlider(label: String, value: Float, lo: Float, hi: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        Slider(
            value = value.coerceIn(lo, hi),
            onValueChange = { onChange((it * 100).roundToInt() / 100f) },
            valueRange = lo..hi,
            modifier = Modifier.weight(1f),
        )
        Text("${(value * 100).roundToInt()}%", Modifier.width(48.dp))
    }
}

/** The full card with everything outside the crop dimmed, so the cut-off is obvious. */
@Composable
private fun CropPreview(card: GameCard, crop: Crop) {
    val ratio = if (card.landscape) 1039f / 744f else 744f / 1039f
    Box(Modifier.fillMaxWidth(if (card.landscape) 1f else 0.65f).aspectRatio(ratio)) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val dim = Color(0xB3000000)
            val x0 = crop.l * w
            val y0 = crop.t * h
            val x1 = crop.r * w
            val y1 = crop.b * h
            if (x1 > x0 && y1 > y0) {
                drawRect(dim, Offset(0f, 0f), Size(w, y0))
                drawRect(dim, Offset(0f, y1), Size(w, h - y1))
                drawRect(dim, Offset(0f, y0), Size(x0, y1 - y0))
                drawRect(dim, Offset(x1, y0), Size(w - x1, y1 - y0))
                drawRect(Gold, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(width = 3f))
            }
        }
    }
}
