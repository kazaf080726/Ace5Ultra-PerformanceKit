package com.ace5ultra.perfkit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ace5ultra.perfkit.data.ModuleInfo
import com.ace5ultra.perfkit.data.PerfRepository
import com.ace5ultra.perfkit.data.PerfStatus
import com.ace5ultra.perfkit.data.Settings
import com.ace5ultra.perfkit.data.TuningItem
import com.ace5ultra.perfkit.root.RootBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the live dashboard state and drives the configurable 3-5 s refresh loop.
 * Never throws: every failure degrades to a N/A state.
 */
class PerfViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PerfRepository()
    val settings = Settings(app)

    sealed class RootUi {
        data object Checking : RootUi()
        data object NoRoot : RootUi()
        data class Ready(val module: ModuleInfo) : RootUi()
        data class MissingModule(val module: ModuleInfo) : RootUi()
    }

    private val _root = MutableStateFlow<RootUi>(RootUi.Checking)
    val root: StateFlow<RootUi> = _root.asStateFlow()

    private val _status = MutableStateFlow<PerfStatus?>(null)
    val status: StateFlow<PerfStatus?> = _status.asStateFlow()

    private val _tuning = MutableStateFlow<List<TuningItem>>(emptyList())
    val tuning: StateFlow<List<TuningItem>> = _tuning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Per-core utilization history, keyed by cpu id. */
    val history = MutableStateFlow<Map<Int, java.util.ArrayDeque<Float>>>(emptyMap())

    @Volatile private var polling = false

    init {
        refreshRoot()
    }

    fun refreshRoot() {
        viewModelScope.launch {
            _root.value = RootUi.Checking
            val hasRoot = RootBridge.hasRoot()
            if (!hasRoot) {
                _root.value = RootUi.NoRoot
                // Still seed a best-effort direct status so the dashboard isn't empty.
                _status.value = directFallbackStatus()
                return@launch
            }
            val mod = repo.detectModule()
            _root.value = if (mod.installed) RootUi.Ready(mod) else RootUi.MissingModule(mod)
            if (mod.installed) pollOnce()
        }
    }

    fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                pollOnce()
                delay(settings.refreshSeconds * 1000L)
            }
        }
    }

    fun stopPolling() { polling = false }

    fun pollOnce() {
        viewModelScope.launch {
            val s = repo.fetchStatus()
            if (s != null) {
                _status.value = s
                pushHistory(s)
            } else {
                // Root present but perfctl failed: fall back to direct reads.
                _status.value = directFallbackStatus()
                _lastError.value = "perfctl unreachable"
            }
        }
    }

    fun reloadTuning() {
        viewModelScope.launch { _tuning.value = repo.listTuning() }
    }

    fun setProfile(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.setProfile(name)
            if (ok) settings.lastProfile = name
            onDone(ok)
            pollOnce()
        }
    }

    fun setAdaptive(on: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.setAdaptive(on)
            onDone(ok)
            pollOnce()
        }
    }

    fun setTuning(key: String, value: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.setTuning(key, value)
            onDone(ok)
            reloadTuning()
        }
    }

    fun snapshot(onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(repo.snapshot()) }
    fun restore(onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(repo.restore()) }

    private fun pushHistory(s: PerfStatus) {
        val map = HashMap(history.value)
        s.cpu.cores.forEach { c ->
            val q = map.getOrPut(c.cpu) { java.util.ArrayDeque(80) }
            if (!c.util.isNaN()) {
                if (q.size >= 80) q.pollFirst()
                q.addLast(c.util)
            }
        }
        history.value = map
    }

    private fun directFallbackStatus(): PerfStatus {
        val mem = repo.directMemory()
        val uptime = repo.directUptimeSeconds()
        return PerfStatus(
            device = repo.directDevice(),
            memory = mem ?: PerfRepository().directMemory() ?: com.ace5ultra.perfkit.data.MemoryInfo(),
            uptimeSeconds = uptime ?: -1L,
        )
    }
}
