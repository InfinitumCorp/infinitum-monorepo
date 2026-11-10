package com.infinitum.throne.guard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinitum.throne.core.audit.AuditReport
import com.infinitum.throne.core.audit.AuditEngine
import com.infinitum.throne.core.audit.DeviceSnapshot
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.core.net.PrivateDnsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GuardScreen { Dashboard, DnsGuide }

class GuardViewModel(app: Application) : AndroidViewModel(app) {
    private val reader = DeviceSnapshotReader(app)
    private val probe = PrivateDnsProbe()
    val edition = EditionPolicy.parse(BuildConfig.EDITION)

    var screen by mutableStateOf(GuardScreen.Dashboard)
        private set
    var loading by mutableStateOf(true)
        private set
    var report by mutableStateOf<AuditReport?>(null)
        private set
    var snapshot by mutableStateOf<DeviceSnapshot?>(null)
        private set
    var query by mutableStateOf("")
    var probing by mutableStateOf(false)
        private set
    var probeMessage by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            loading = report == null
            val read = withContext(Dispatchers.Default) { reader.read() }
            snapshot = read
            report = AuditEngine.assess(read)
            loading = false
        }
    }

    fun showGuide() {
        probeMessage = null
        screen = GuardScreen.DnsGuide
    }

    fun showDashboard() {
        screen = GuardScreen.Dashboard
    }

    fun probeRecommended() = runProbe(PrivateDnsPolicy.RECOMMENDED_HOST)

    fun probeConfigured() {
        val host = snapshot?.privateDnsSpecifier ?: return
        runProbe(host)
    }

    private fun runProbe(host: String) {
        val configured = snapshot?.privateDnsSpecifier
        viewModelScope.launch {
            probing = true
            probeMessage = null
            val message = withContext(Dispatchers.IO) { probe.check(host, configured) }
            probeMessage = message
            probing = false
        }
    }
}
