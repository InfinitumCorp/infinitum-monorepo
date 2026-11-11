package com.infinitum.throne.core.net

object PrivateDnsPolicy {
    const val RECOMMENDED_HOST = "dns.adguard-dns.com"
    val allowedHosts: Set<String> = setOf(RECOMMENDED_HOST, "dns.quad9.net")

    fun probeDecision(host: String, configuredSpecifier: String?): ProbeDecision {
        val normalized = normalize(host) ?: return ProbeDecision.Refuse("That hostname cannot be checked.")
        if (!isPublicHostname(normalized)) {
            return ProbeDecision.Refuse("That hostname cannot be checked.")
        }
        val configured = configuredSpecifier?.let(::normalize)
        val allowed = normalized in allowedHosts || (configured != null && configured == normalized)
        if (!allowed) {
            return ProbeDecision.Refuse(
                "This check only contacts the suggested resolvers, or the Private DNS hostname already set on the phone.",
            )
        }
        return ProbeDecision.Allow(normalized)
    }

    private fun normalize(host: String): String? {
        val trimmed = host.trim().trimEnd('.').lowercase()
        if (trimmed.isEmpty() || trimmed.length > 253) return null
        return trimmed
    }

    private fun isPublicHostname(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host.endsWith(".internal") || host.endsWith(".home") || host.endsWith(".lan")) return false
        if (':' in host || '/' in host || '\\' in host || '@' in host) return false
        if (IPV4.matches(host)) return false
        return HOST.matches(host)
    }

    private val HOST = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
}

sealed class ProbeDecision {
    data class Allow(val host: String) : ProbeDecision()
    data class Refuse(val reason: String) : ProbeDecision()
}
