package com.riftrecall.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riftrecall.app.data.Card
import com.riftrecall.app.data.CardRepository
import com.riftrecall.app.data.Entry
import com.riftrecall.app.data.Pool
import com.riftrecall.app.data.ProgressMigration
import com.riftrecall.app.data.ProgressStore
import com.riftrecall.app.data.Settings
import com.riftrecall.app.data.SettingsStore
import com.riftrecall.app.logic.Answer
import com.riftrecall.app.logic.Commit
import com.riftrecall.app.logic.Engine
import com.riftrecall.app.logic.Mode
import com.riftrecall.app.logic.SessionBuilder
import com.riftrecall.app.logic.SessionState
import com.riftrecall.app.logic.Srs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CardsState {
    data class Loading(val msg: String) : CardsState
    data class Ready(val all: List<Card>) : CardsState
    data class Error(val msg: String) : CardsState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CardRepository(app.filesDir)
    private val store = ProgressStore(app.filesDir)
    private val prefs = SettingsStore(app)

    private val _cards = MutableStateFlow<CardsState>(CardsState.Loading("Loading cards..."))
    val cards: StateFlow<CardsState> = _cards.asStateFlow()

    private val _settings = MutableStateFlow(prefs.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val progress: StateFlow<Map<String, Entry>> = _progress.asStateFlow()

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _progress.value = withContext(Dispatchers.IO) { store.load() }
            val cached = withContext(Dispatchers.IO) { repo.loadCache() }
            if (cached != null) _cards.value = CardsState.Ready(cached) else refresh()
        }
    }

    // ----- card data ---------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            _cards.value = CardsState.Loading("Downloading card data...")
            try {
                val legacy = withContext(Dispatchers.IO) { repo.loadLegacy() }
                val list = repo.download { page, pages ->
                    _cards.value = CardsState.Loading("Downloading card data... $page / $pages")
                }
                if (legacy != null) {
                    // Card ids changed with the new data source: carry study progress over.
                    _progress.value = ProgressMigration.remap(_progress.value, legacy, list)
                    flush()
                }
                _cards.value = CardsState.Ready(list)
                if (repo.usedFallback) {
                    _message.value = "RiftHunt was unreachable, so the older Riftcodex data was used. It may be missing new sets."
                }
            } catch (e: Exception) {
                val cached = withContext(Dispatchers.IO) { repo.loadCache() }
                if (cached != null) {
                    _cards.value = CardsState.Ready(cached)
                    _message.value = "Couldn't refresh cards (${e.message ?: "network error"}). Using saved data."
                } else {
                    _cards.value = CardsState.Error(e.message ?: "Network error")
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun pool(): List<Card> {
        val all = (_cards.value as? CardsState.Ready)?.all ?: return emptyList()
        return Pool.filter(all, _settings.value)
    }

    // ----- settings ----------------------------------------------------------------------

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.save(next)
    }

    fun resetProgress() {
        _progress.value = emptyMap()
        flush()
    }

    // ----- session -----------------------------------------------------------------------

    /** Returns false (and posts a message) when there is nothing to study. */
    fun startSession(mode: Mode): Boolean {
        val s = _settings.value
        val items = SessionBuilder.build(
            pool(), mode, _progress.value, System.currentTimeMillis(), s.newPerSession, s.sessionSize,
        )
        if (items.isEmpty()) {
            _message.value = "No cards available for this mode."
            return false
        }
        _session.value = Engine.begin(mode, items)
        return true
    }

    fun endSession() {
        _session.value = null
        flush()
    }

    fun ackIntro() = mutate { Engine.ackIntro(it) }
    fun hint() = mutate { Engine.useHint(it) }
    fun submit(answer: Answer) = mutate { Engine.submitStep(it, answer) }
    fun override(index: Int) = mutate { Engine.toggleOverride(it, index) }

    fun advance() {
        val s = _session.value ?: return
        val (next, commits) = Engine.advance(s)
        applyCommits(commits)
        _session.value = next
    }

    private fun mutate(f: (SessionState) -> SessionState) {
        _session.value = _session.value?.let(f)
    }

    private fun applyCommits(commits: List<Commit>) {
        if (commits.isEmpty()) return
        val now = System.currentTimeMillis()
        val map = _progress.value.toMutableMap()
        for (c in commits) {
            val k = Srs.key(c.cardId, c.field)
            map[k] = Srs.apply(map[k], c.outcome, now)
        }
        _progress.value = map
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            store.save(_progress.value)
        }
    }

    private fun flush() {
        saveJob?.cancel()
        val snapshot = _progress.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    override fun onCleared() {
        store.save(_progress.value)
        super.onCleared()
    }
}
