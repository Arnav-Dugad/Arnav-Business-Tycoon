package com.arnavgames.tycoon.game

import android.util.Log
import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.core.time.OfflineCalculator
import com.arnavgames.tycoon.core.time.TimeFlag
import com.arnavgames.tycoon.data.save.SaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the running game (docs/ARCHITECTURE.md §4).
 *
 * - All state changes go through a single mutex on Dispatchers.Default: engine ticks and player commands never race.
 * - On every resume (cold start or returning from background) offline time is credited by [OfflineCalculator].
 * - While in the foreground the engine advances once per second using the monotonic clock.
 * - Autosaves every 30 s, after important commands, and when the app goes to the background.
 */
@Singleton
class GameSession @Inject constructor(
    private val contentLazy: dagger.Lazy<ContentLoader.Loaded>,
    private val saves: SaveRepository,
    private val clock: GameClock,
    @ApplicationScope private val scope: CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val app: android.content.Context,
) {
    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data class Recovery(val errors: List<String>) : Status
        data class NeedsUpdate(val schema: Int) : Status
        data class Failed(val message: String) : Status
    }

    data class OfflineReport(val earned: Money, val creditedMs: Long, val rawMs: Long, val flag: TimeFlag, val capMs: Long)

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val _offline = MutableStateFlow<OfflineReport?>(null)
    val offline: StateFlow<OfflineReport?> = _offline.asStateFlow()

    lateinit var engine: GameEngine
        private set

    private val mutex = Mutex()
    private var tickJob: Job? = null
    private var lastTickMono = 0L
    private var lastSaveMono = 0L
    private var started = false
    private var foreground = false

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.Default) {
            try {
                val loaded = contentLazy.get()
                if (loaded.issues.isNotEmpty()) Log.w(TAG, "content issues: ${loaded.issues}")
                engine = GameEngine(loaded.pack)
                when (val r = saves.load()) {
                    SaveRepository.LoadResult.Empty -> {
                        val seed = GameRandom.newWorldSeed(SecureRandom().nextLong())
                        val fresh = engine.newGame(seed, loaded.hash, clock)
                        _state.value = fresh
                        persist(fresh)
                        _status.value = Status.Ready
                    }
                    is SaveRepository.LoadResult.Loaded -> {
                        if (r.recoveredFromOlder > 0) Log.w(TAG, "recovered from older snapshot: ${r.errors}")
                        _state.value = r.save.state
                        _status.value = Status.Ready
                        if (foreground) resume()
                    }
                    is SaveRepository.LoadResult.Unrecoverable -> _status.value = Status.Recovery(r.errors)
                    is SaveRepository.LoadResult.NeedsUpdate -> _status.value = Status.NeedsUpdate(r.schema)
                }
                if (foreground && _status.value == Status.Ready) startTicking()
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                _status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Called when the app becomes visible. */
    fun onForeground() {
        foreground = true
        if (_status.value != Status.Ready) return
        scope.launch(Dispatchers.Default) {
            resume()
            startTicking()
        }
    }

    /** Called when the app is no longer visible: stop ticking and save. */
    fun onBackground() {
        foreground = false
        tickJob?.cancel()
        tickJob = null
        scope.launch(Dispatchers.Default) { saveNow() }
    }

    private suspend fun resume() = mutex.withLock {
        val s = _state.value ?: return@withLock
        val cap = engine.economy.offlineCapMs(s)
        val credit = OfflineCalculator.compute(s.clock, clock, cap)
        val before = s.cash
        val advanced = engine.advance(s, credit.creditMs)
        val ns = advanced.state.copy(clock = credit.newClock)
        _state.value = ns
        lastTickMono = clock.monotonicMillis()
        advanced.events.forEach { _events.tryEmit(it) }
        val earned = (ns.cash - before) + advanced.state.businesses.values.fold(Money.ZERO) { a, b -> a + b.stored } -
            s.businesses.values.fold(Money.ZERO) { a, b -> a + b.stored }
        if (credit.creditMs >= 60_000 || credit.flag == TimeFlag.ForwardJumpDetected || credit.flag == TimeFlag.ClockWentBackwards) {
            _offline.value = OfflineReport(earned.max(Money.ZERO), credit.creditMs, credit.rawWallMs, credit.flag, cap)
        }
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        lastTickMono = clock.monotonicMillis()
        tickJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private suspend fun tick() {
        mutex.withLock {
            val s = _state.value ?: return@withLock
            val now = clock.monotonicMillis()
            val dt = (now - lastTickMono).coerceIn(0, MAX_TICK_MS)
            lastTickMono = now
            val r = engine.advance(s, dt)
            _state.value = r.state.copy(clock = OfflineCalculator.touch(r.state.clock, clock))
            r.events.forEach { _events.tryEmit(it) }
        }
        if (clock.monotonicMillis() - lastSaveMono >= AUTOSAVE_MS) saveNow()
    }

    /** Applies a player command. Returns the engine's result so the UI can show errors. */
    suspend fun dispatch(cmd: Command): CommandResult = withContext(Dispatchers.Default) {
        val result = mutex.withLock {
            val s = _state.value ?: return@withLock null
            // Bring the world up to "now" first so commands always act on current numbers.
            val now = clock.monotonicMillis()
            val pre = if (_status.value == Status.Ready && foreground) {
                val dt = (now - lastTickMono).coerceIn(0, MAX_TICK_MS)
                lastTickMono = now
                engine.advance(s, dt).also { r -> r.events.forEach { _events.tryEmit(it) } }.state
            } else s
            val r = engine.apply(pre, cmd)
            when (r) {
                is CommandResult.Ok -> {
                    _state.value = r.state
                    r.events.forEach { _events.tryEmit(it) }
                }
                is CommandResult.Rejected -> _state.value = pre
            }
            r
        } ?: return@withContext CommandResult.Rejected(com.arnavgames.tycoon.core.engine.GameError.Invalid("Game not loaded"))
        if (result is CommandResult.Ok && cmd.isImportant()) saveNow()
        result
    }

    fun dismissOffline() { _offline.value = null }

    /**
     * DEBUG BUILDS ONLY: replaces the game with a scenario built from normal engine commands, so every screen
     * can be inspected with realistic data. Does nothing in release builds.
     */
    fun debugLoadScenario(name: String) {
        if (!com.arnavgames.tycoon.BuildConfig.DEBUG) return
        scope.launch(Dispatchers.Default) {
            _status.first { it == Status.Ready }
            mutex.withLock {
                // Always start from a fresh game, so a scenario looks the same however often it is loaded.
                _state.value ?: return@withLock
                val base = engine.newGame(GameRandom.newWorldSeed(SecureRandom().nextLong()), contentLazy.get().hash, clock)
                _state.value = DebugScenarios.build(engine, base, name) ?: return@withLock
            }
            saveNow()
        }
    }

    /** Museum credit lines keyed by item id, read once from assets/img/art/credits.json. */
    fun readArtCredits(): Map<String, String> = runCatching {
        val text = app.assets.open("img/art/credits.json").bufferedReader().use { it.readText() }
        kotlinx.serialization.json.Json.parseToJsonElement(text).let { el ->
            (el as kotlinx.serialization.json.JsonArray).associate { e ->
                val o = e as kotlinx.serialization.json.JsonObject
                fun f(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                f("id") to "${f("title")}, ${f("author")}${if (f("date").isNotBlank()) " (" + f("date") + ")" else ""}. ${f("source")}, ${f("license")}. ${f("creditLine")}".trim()
            }
        }
    }.getOrDefault(emptyMap())

    /** Manager ids that have a bundled portrait (assets/img/portraits/<id>.webp). */
    fun portraitIds(): Set<String> = runCatching {
        (app.assets.list("img/portraits") ?: emptyArray()).filter { it.endsWith(".webp") }.map { it.removeSuffix(".webp") }.toSet()
    }.getOrDefault(emptySet())

    /** Newest save bytes for "Export save" (saves first so the export is current). */
    suspend fun exportBytes(): ByteArray? {
        saveNow()
        return saves.exportLatest()
    }

    /**
     * Replaces the running game with an imported save after validating it completely.
     * The current game is saved first, so it remains in the snapshot history.
     */
    suspend fun importBytes(bytes: ByteArray) {
        saveNow()
        val decoded = saves.import(bytes) // throws SaveError with a readable message if invalid
        mutex.withLock {
            _state.value = decoded.state.copy(clock = OfflineCalculator.touch(decoded.state.clock, clock))
            lastTickMono = clock.monotonicMillis()
        }
    }

    /** Recovery: the player chose to start over after an unrecoverable save (the broken file stays in quarantine). */
    fun startNewGameAfterRecovery() {
        scope.launch(Dispatchers.Default) {
            val loaded = contentLazy.get()
            engine = GameEngine(loaded.pack)
            val fresh = engine.newGame(GameRandom.newWorldSeed(SecureRandom().nextLong()), loaded.hash, clock)
            _state.value = fresh
            persist(fresh)
            _status.value = Status.Ready
            if (foreground) startTicking()
        }
    }

    suspend fun saveNow() {
        val s = mutex.withLock { _state.value?.let { it.copy(clock = OfflineCalculator.touch(it.clock, clock)) } } ?: return
        if (_status.value != Status.Ready) return
        persist(s)
    }

    private suspend fun persist(s: GameState) {
        try {
            saves.save(s, engine.economy.netWorth(s).log10Dollars())
            lastSaveMono = clock.monotonicMillis()
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
        }
    }

    private fun Command.isImportant() = when (this) {
        is Command.OpenBusiness, is Command.HireManager, is Command.StartExpansion, is Command.RushTimer,
        is Command.UpgradeHq, is Command.ClaimMission, is Command.SetMothballed -> true
        else -> false
    }

    companion object {
        private const val TAG = "GameSession"
        const val TICK_MS = 1_000L
        const val AUTOSAVE_MS = 30_000L
        /** A single foreground tick never advances more than this (e.g. after the debugger paused the process). */
        const val MAX_TICK_MS = 10 * 60_000L
    }
}
