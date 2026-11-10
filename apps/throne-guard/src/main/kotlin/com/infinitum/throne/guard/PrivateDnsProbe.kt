package com.infinitum.throne.guard

import com.infinitum.throne.core.net.PrivateDnsPolicy
import com.infinitum.throne.core.net.ProbeDecision
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens TCP port 853 only after [PrivateDnsPolicy] allows the host.
 * Nothing in this process calls it unless the user taps the check.
 */
class PrivateDnsProbe {
    fun check(host: String, configuredSpecifier: String?, timeoutMs: Int = 4000): String {
        return when (val decision = PrivateDnsPolicy.probeDecision(host, configuredSpecifier)) {
            is ProbeDecision.Refuse -> decision.reason
            is ProbeDecision.Allow -> connect(decision.host, timeoutMs)
        }
    }

    private fun connect(host: String, timeoutMs: Int): String {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 853), timeoutMs)
            }
            "TCP port 853 on $host accepted a connection. This does not change Private DNS and does not prove the resolver blocks trackers."
        } catch (_: Exception) {
            "Could not open TCP port 853 on $host."
        }
    }
}
