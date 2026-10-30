package com.riftrecall.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riftrecall.app.AppViewModel
import com.riftrecall.app.CardsState
import com.riftrecall.app.data.Crop
import com.riftrecall.app.logic.Answer
import com.riftrecall.app.logic.Field
import com.riftrecall.app.logic.Hints
import com.riftrecall.app.logic.Mode
import com.riftrecall.app.logic.Phase
import com.riftrecall.app.logic.SessionState
import com.riftrecall.app.logic.StepResult
import com.riftrecall.app.logic.Verdict
import com.riftrecall.app.data.Card as GameCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(vm: AppViewModel, onDone: () -> Unit, onExit: () -> Unit) {
    val session by vm.session.collectAsState()
    val settings by vm.settings.collectAsState()
    val cardsState by vm.cards.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }

    val state = session
    if (state == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }
    LaunchedEffect(state.phase) { if (state.phase == Phase.DONE) onDone() }
    BackHandler { confirmExit = true }

    val all = (cardsState as? CardsState.Ready)?.all.orEmpty()
    val typeOptions = remember(all) { all.map { it.type }.filter { it.isNotBlank() }.distinct().sorted() }
    val domainOptions = remember(all) { all.flatMap { it.domains }.filter { it.isNotBlank() }.distinct().sorted() }
    val head = state.queue.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.mode.title) },
                navigationIcon = {
                    IconButton(onClick = { confirmExit = true }) { Icon(Icons.Default.Close, contentDescription = "End session") }
                },
                actions = { Text("${state.queue.size} left", modifier = Modifier.padding(end = 16.dp)) },
            )
        },
    ) { pad ->
        if (head != null && state.phase != Phase.DONE) {
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.phase) {
                    Phase.INTRO -> IntroPane(head.card, onGotIt = vm::ackIntro)
                    Phase.ASK -> AskPane(
                        state = state,
                        card = head.card,
                        crop = settings.cropFor(head.card),
                        types = typeOptions,
                        domains = domainOptions,
                        vm = vm,
                        resetKey = "${head.card.id}-${head.tries}-${head.relearn}-${state.stepIndex}",
                    )
                    Phase.FEEDBACK -> FeedbackPane(state, head.card, vm)
                    Phase.DONE -> {}
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("End this session?") },
            text = { Text("Answers you've already given are saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.endSession()
                    onExit()
                }) { Text("End session") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// INTRO: a new card is shown in full first (learn), then quizzed a few cards later (recall).
// ---------------------------------------------------------------------------------------------

@Composable
private fun IntroPane(card: GameCard, onGotIt: () -> Unit) {
    Pill("New card - study it, then you'll be quizzed")
    CardFull(card, Modifier.fillMaxWidth())
    CardFacts(card)
    Button(onClick = onGotIt, modifier = Modifier.fillMaxWidth()) { Text("Got it - quiz me") }
}

// ---------------------------------------------------------------------------------------------
// ASK
// ---------------------------------------------------------------------------------------------

private fun prompt(field: Field) = when (field) {
    Field.NAME -> "What is this card called?"
    Field.ABILITY -> "What does this card do?"
    Field.COST -> "What is its energy cost?"
    Field.STATS -> "What are its Might and Power?"
    Field.TYPE_DOMAIN -> "What type of card is it, and which domain(s)?"
}

@Composable
private fun AskPane(
    state: SessionState,
    card: GameCard,
    crop: Crop,
    types: List<String>,
    domains: List<String>,
    vm: AppViewModel,
    resetKey: String,
) {
    val field = state.steps[state.stepIndex]
    CardArt(card, crop, Modifier.fillMaxWidth())

    if (state.steps.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Step ${state.stepIndex + 1} of ${state.steps.size}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { (state.stepIndex + 1f) / state.steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Text(prompt(field), style = MaterialTheme.typography.titleMedium)

    key(resetKey) {
        AnswerForm(
            field = field,
            hints = state.hints,
            hintText = if (state.hints > 0) Hints.forField(card, field, state.hints) else null,
            types = types,
            domains = domains,
            onHint = vm::hint,
            onSubmit = vm::submit,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerForm(
    field: Field,
    hints: Int,
    hintText: String?,
    types: List<String>,
    domains: List<String>,
    onHint: () -> Unit,
    onSubmit: (Answer) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (field != Field.TYPE_DOMAIN) runCatching { focus.requestFocus() }
    }

    fun submit() = onSubmit(Answer(text = text, a = a, b = b, type = type, domains = picked))

    when (field) {
        Field.NAME -> OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Card name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Field.ABILITY -> OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("What it does") },
            supportingText = { Text("Reminder text in (brackets) isn't needed. Key words matter most.") },
            minLines = 3,
        )
        Field.COST -> OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(2) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Energy cost") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Field.STATS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = a,
                onValueChange = { a = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f).focusRequester(focus),
                label = { Text("Might") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = b,
                onValueChange = { b = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f),
                label = { Text("Power") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        Field.TYPE_DOMAIN -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in types) FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
            }
            Text("Domain (pick all that apply, none if it has no domain)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in domains) {
                    FilterChip(
                        selected = d in picked,
                        onClick = { picked = if (d in picked) picked - d else picked + d },
                        label = { Text(d) },
                    )
                }
            }
        }
    }

    if (field == Field.STATS) {
        Text("Leave a box empty if the card has none.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (hintText != null) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(hintText, Modifier.padding(12.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onHint, enabled = hints < Hints.MAX) { Text("Hint ($hints/${Hints.MAX})") }
        Spacer(Modifier.weight(1f))
        Button(onClick = { submit() }) { Text("Check") }
    }
}

// ---------------------------------------------------------------------------------------------
// FEEDBACK
// ---------------------------------------------------------------------------------------------

@Composable
private fun FeedbackPane(state: SessionState, card: GameCard, vm: AppViewModel) {
    val allPass = state.results.all { it.passed }
    Text(
        if (allPass) "Correct!" else "Not quite - here's the card",
        style = MaterialTheme.typography.headlineSmall,
        color = if (allPass) Good else Bad,
    )
    state.results.forEachIndexed { i, r -> ResultRow(r, onOverride = { vm.override(i) }) }
    CardFull(card, Modifier.fillMaxWidth())
    CardFacts(card)
    Button(onClick = vm::advance, modifier = Modifier.fillMaxWidth()) { Text("Next") }
}

@Composable
private fun ResultRow(r: StepResult, onOverride: () -> Unit) {
    val color = if (r.passed) Good else if (r.grade.verdict == Verdict.CLOSE) Gold else Bad
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        r.overridden -> "✔"
                        r.grade.verdict == Verdict.CORRECT -> "✔"
                        r.grade.verdict == Verdict.CLOSE -> "~"
                        else -> "✖"
                    },
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(r.field.label, style = MaterialTheme.typography.titleMedium)
                if (r.hints > 0) Pill("${r.hints} hint${if (r.hints > 1) "s" else ""}")
            }
            if (!(r.grade.verdict == Verdict.CORRECT && r.grade.note == null)) {
                Text("You: ${r.given.ifEmpty { "-" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.grade.verdict != Verdict.CORRECT) Text("Answer: ${r.expected}")
            r.grade.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (r.grade.verdict != Verdict.CORRECT && r.grade.missing.isNotEmpty()) {
                Text(
                    "Missing: ${r.grade.missing.joinToString(", ")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (r.grade.verdict != Verdict.CORRECT) {
                TextButton(onClick = onOverride) {
                    Text(if (r.overridden) "Undo - I didn't know it" else "I actually knew this")
                }
            }
        }
    }
}
