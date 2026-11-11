package com.infinitum.throne.core.audit

import com.infinitum.throne.core.net.PrivateDnsPolicy

object Audit {
    const val VEIL_PACKAGE = "com.infinitum.veilvpn"
    const val MESSAGES_PACKAGE = "com.google.android.apps.messaging"
    const val MESSAGES_NAMED_VERSION = "20261022"
    const val CAMERA = "android.permission.CAMERA"
    const val MICROPHONE = "android.permission.RECORD_AUDIO"
    val LOCATION = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
    )
    val OEM_ANALYTICS = listOf(
        "com.transsion.trancare",
        "com.transsion.hamal",
        "com.transsion.statisticalsales",
        "com.transsion.tecnospot",
        "com.hoffnung",
    )

    const val ENCRYPTION_UNSUPPORTED = 0
    const val ENCRYPTION_INACTIVE = 1
    const val ENCRYPTION_ACTIVATING = 2
    const val ENCRYPTION_ACTIVE = 3
    const val ENCRYPTION_ACTIVE_DEFAULT_KEY = 4
    const val ENCRYPTION_ACTIVE_PER_USER = 5
}

enum class FindingStatus { CLEAR, REVIEW, UNKNOWN, NOTE }

data class FindingRow(
    val title: String,
    val detail: String,
    val destination: String? = null,
)

data class Finding(
    val id: String,
    val title: String,
    val status: FindingStatus,
    val summary: String,
    val actionLabel: String? = null,
    val actionDestination: String? = null,
    val rows: List<FindingRow> = emptyList(),
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val grantedDangerous: List<String>,
    val declaresInstallPackages: Boolean,
    val unknownInstallAllowed: Boolean?,
    val installerPackage: String?,
)

data class DeviceSnapshot(
    val apps: List<InstalledApp>,
    val accessibilityServices: List<String>?,
    val deviceAdmins: List<String>?,
    val privateDnsReadable: Boolean,
    val privateDnsMode: String?,
    val privateDnsSpecifier: String?,
    val deviceSecure: Boolean?,
    val encryptionStatus: Int?,
    val developerOptions: Boolean?,
    val usbDebugging: Boolean?,
    val veilInstalled: Boolean,
    val alwaysOnVpnReadable: Boolean,
    val alwaysOnVpnPackage: String?,
    val alwaysOnLockdownReadable: Boolean,
    val alwaysOnLockdown: Boolean?,
    val activeVpnPackages: List<String>?,
    val defaultSmsPackage: String?,
    val messagesInstalled: Boolean,
    val messagesVersionName: String?,
    val lockScreenNotificationsShown: Boolean?,
    val lockScreenPrivateContentAllowed: Boolean?,
)

data class AuditReport(
    val findings: List<Finding>,
    val camera: List<InstalledApp>,
    val microphone: List<InstalledApp>,
    val location: List<InstalledApp>,
    val permissionApps: List<InstalledApp>,
)

object AuditEngine {
    fun assess(snapshot: DeviceSnapshot): AuditReport {
        val findings = listOf(
            messagingDefault(snapshot),
            messagingVersion(snapshot),
            messagingRcs(),
            messagingSmsFallback(),
            messagingLockScreen(snapshot),
            messagingVault(),
            screenLock(snapshot),
            encryption(snapshot),
            privateDns(snapshot),
            developerOptions(snapshot),
            usbDebugging(snapshot),
            veil(snapshot),
            accessibility(snapshot),
            deviceAdmins(snapshot),
            unknownInstallers(snapshot),
            oemAnalytics(snapshot),
        )
        val byLabel = compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName }
        return AuditReport(
            findings = findings,
            camera = snapshot.apps.filter { Audit.CAMERA in it.grantedDangerous }.sortedWith(byLabel),
            microphone = snapshot.apps.filter { Audit.MICROPHONE in it.grantedDangerous }.sortedWith(byLabel),
            location = snapshot.apps.filter { app -> app.grantedDangerous.any { it in Audit.LOCATION } }.sortedWith(byLabel),
            permissionApps = snapshot.apps.sortedWith(byLabel),
        )
    }

    private fun messagingDefault(snapshot: DeviceSnapshot): Finding {
        val messages = Audit.MESSAGES_PACKAGE
        return when (snapshot.defaultSmsPackage) {
            messages -> finding(
                "msg_default",
                "Default SMS app",
                FindingStatus.CLEAR,
                "Google Messages is the default SMS app.",
            )
            null -> finding(
                "msg_default",
                "Default SMS app",
                FindingStatus.REVIEW,
                "No default SMS app was reported. Set it to Google Messages ($messages).",
                "Set Google Messages as default",
                "default_sms",
            )
            else -> finding(
                "msg_default",
                "Default SMS app",
                FindingStatus.REVIEW,
                "The default SMS app is ${snapshot.defaultSmsPackage}, not Google Messages ($messages).",
                "Set Google Messages as default",
                "default_sms",
            )
        }
    }

    private fun messagingVersion(snapshot: DeviceSnapshot): Finding {
        if (!snapshot.messagesInstalled) {
            return finding(
                "msg_version",
                "Google Messages",
                FindingStatus.REVIEW,
                "Google Messages (${Audit.MESSAGES_PACKAGE}) is not installed. The named build is ${Audit.MESSAGES_NAMED_VERSION}.",
                "App settings",
                "messages",
            )
        }
        val date = versionDate(snapshot.messagesVersionName)
        val shown = snapshot.messagesVersionName ?: "unreported"
        return when {
            date == null -> finding(
                "msg_version",
                "Google Messages version",
                FindingStatus.NOTE,
                "Installed version \"$shown\" has no date this check can compare. The named build is ${Audit.MESSAGES_NAMED_VERSION}.",
            )
            date < Audit.MESSAGES_NAMED_VERSION.toInt() -> finding(
                "msg_version",
                "Google Messages version",
                FindingStatus.REVIEW,
                "Installed version $shown is older than the named build ${Audit.MESSAGES_NAMED_VERSION}.",
                "Open Messages",
                "messages",
            )
            else -> finding(
                "msg_version",
                "Google Messages version",
                FindingStatus.CLEAR,
                "Installed version $shown is the named build ${Audit.MESSAGES_NAMED_VERSION} or newer.",
            )
        }
    }

    private fun messagingRcs(): Finding = finding(
        "msg_rcs",
        "RCS Chat features",
        FindingStatus.NOTE,
        "This app cannot read or turn on RCS inside Google Messages. " +
            "When Chat features are connected, Messages can end-to-end encrypt RCS chats with other RCS users. " +
            "Open Messages, then your profile, then Messages settings, then Chat features, and turn them on. " +
            "In a conversation, encryption is only real when that conversation shows it. A connected RCS status is not the same thing.",
        "Open Messages",
        "messages",
    )

    private fun messagingSmsFallback(): Finding = finding(
        "msg_sms_fallback",
        "SMS and MMS",
        FindingStatus.NOTE,
        "Plain SMS and MMS are not encrypted. Messages falls back to them when RCS is unavailable, and this app cannot stop that fallback. " +
            "A carrier can read SMS. RCS end-to-end encryption does not cover that fallback.",
    )

    private fun messagingLockScreen(snapshot: DeviceSnapshot): Finding {
        val shown = snapshot.lockScreenNotificationsShown
        val privateContent = snapshot.lockScreenPrivateContentAllowed
        val action = "Open notification settings" to "notification_settings"
        return when {
            shown == null && privateContent == null -> finding(
                "msg_lockscreen",
                "Lock-screen previews",
                FindingStatus.UNKNOWN,
                "The lock-screen notification flags could not be read. Hide message previews in notification settings. " +
                    "This app does not request notification access, so it cannot see each Messages channel.",
                action.first,
                action.second,
            )
            shown == false -> finding(
                "msg_lockscreen",
                "Lock-screen previews",
                FindingStatus.CLEAR,
                "Notifications are not shown on the lock screen. Per-channel settings inside Messages are still not visible to this app.",
                action.first,
                action.second,
            )
            privateContent == false -> finding(
                "msg_lockscreen",
                "Lock-screen previews",
                FindingStatus.CLEAR,
                "Sensitive notification content is hidden on the lock screen. This is the global flag. " +
                    "This app cannot see whether a single Messages channel still shows a preview.",
                action.first,
                action.second,
            )
            privateContent == true -> finding(
                "msg_lockscreen",
                "Lock-screen previews",
                FindingStatus.REVIEW,
                "The lock screen is allowed to show sensitive notification content, including message text. " +
                    "In notification settings, hide sensitive content, then check the Messages channels.",
                action.first,
                action.second,
            )
            else -> finding(
                "msg_lockscreen",
                "Lock-screen previews",
                FindingStatus.UNKNOWN,
                "Lock-screen preview state was only partly readable. Hide message previews in notification settings.",
                action.first,
                action.second,
            )
        }
    }

    private fun messagingVault(): Finding = finding(
        "msg_vault",
        "Encrypted vault",
        FindingStatus.NOTE,
        "THRONE][OS™ Vault encrypts a message on this phone and hands the ciphertext to Messages as ordinary text. " +
            "That does not change Google Messages and it is not RCS. " +
            "Compare X25519 safety numbers in person. A key that arrived by SMS can be replaced in transit.",
        "Open Vault",
        "vault",
    )

    private fun screenLock(snapshot: DeviceSnapshot): Finding = when (snapshot.deviceSecure) {
        true -> finding("screen_lock", "Screen lock", FindingStatus.CLEAR, "A PIN, pattern, or password is set.")
        false -> finding(
            "screen_lock",
            "Screen lock",
            FindingStatus.REVIEW,
            "No secure screen lock is set.",
            "Set a screen lock",
            "password",
        )
        null -> finding(
            "screen_lock",
            "Screen lock",
            FindingStatus.UNKNOWN,
            "Screen lock state could not be read.",
            "Open security settings",
            "security",
        )
    }

    private fun encryption(snapshot: DeviceSnapshot): Finding = when (snapshot.encryptionStatus) {
        Audit.ENCRYPTION_ACTIVE,
        Audit.ENCRYPTION_ACTIVE_DEFAULT_KEY,
        Audit.ENCRYPTION_ACTIVE_PER_USER,
        -> finding(
            "encryption",
            "Storage encryption",
            FindingStatus.CLEAR,
            "Android reports device storage encryption as active. This is not a FIPS validation.",
        )
        Audit.ENCRYPTION_ACTIVATING -> finding(
            "encryption",
            "Storage encryption",
            FindingStatus.REVIEW,
            "Android reports encryption as still activating.",
            "Open security settings",
            "security",
        )
        Audit.ENCRYPTION_INACTIVE, Audit.ENCRYPTION_UNSUPPORTED -> finding(
            "encryption",
            "Storage encryption",
            FindingStatus.REVIEW,
            "Android does not report active storage encryption.",
            "Open security settings",
            "security",
        )
        null -> finding(
            "encryption",
            "Storage encryption",
            FindingStatus.UNKNOWN,
            "Encryption status could not be read.",
            "Open security settings",
            "security",
        )
        else -> finding(
            "encryption",
            "Storage encryption",
            FindingStatus.UNKNOWN,
            "Android returned an unrecognized encryption status (${snapshot.encryptionStatus}).",
            "Open security settings",
            "security",
        )
    }

    private fun privateDns(snapshot: DeviceSnapshot): Finding {
        if (!snapshot.privateDnsReadable) {
            return finding(
                "private_dns",
                "Private DNS",
                FindingStatus.UNKNOWN,
                "Private DNS could not be read. Android does not let this app set it.",
                "Set Private DNS",
                "private_dns_guide",
            )
        }
        val mode = snapshot.privateDnsMode?.lowercase()
        val specifier = snapshot.privateDnsSpecifier?.trim().orEmpty()
        return when (mode) {
            "hostname" -> if (specifier.isEmpty()) {
                finding(
                    "private_dns",
                    "Private DNS",
                    FindingStatus.REVIEW,
                    "Private DNS is set to a hostname, but the hostname is empty.",
                    "Set Private DNS",
                    "private_dns_guide",
                )
            } else {
                val extra = if (specifier.equals(PrivateDnsPolicy.RECOMMENDED_HOST, ignoreCase = true)) {
                    " This is the suggested tracker-blocking resolver."
                } else {
                    " The suggested hostname is ${PrivateDnsPolicy.RECOMMENDED_HOST}."
                }
                finding(
                    "private_dns",
                    "Private DNS",
                    FindingStatus.CLEAR,
                    "Private DNS hostname is $specifier.$extra",
                    "Set Private DNS",
                    "private_dns_guide",
                )
            }
            "opportunistic" -> finding(
                "private_dns",
                "Private DNS",
                FindingStatus.REVIEW,
                "Private DNS is opportunistic. Choose a provider hostname for a known tracker-blocking resolver.",
                "Set Private DNS",
                "private_dns_guide",
            )
            "off", "", null -> finding(
                "private_dns",
                "Private DNS",
                FindingStatus.REVIEW,
                "Private DNS is off. This app cannot turn it on. The suggested hostname is ${PrivateDnsPolicy.RECOMMENDED_HOST}.",
                "Set Private DNS",
                "private_dns_guide",
            )
            else -> finding(
                "private_dns",
                "Private DNS",
                FindingStatus.UNKNOWN,
                "Private DNS mode is \"$mode\".",
                "Set Private DNS",
                "private_dns_guide",
            )
        }
    }

    private fun developerOptions(snapshot: DeviceSnapshot): Finding = when (snapshot.developerOptions) {
        false -> finding("developer_options", "Developer options", FindingStatus.CLEAR, "Developer options are off.")
        true -> finding(
            "developer_options",
            "Developer options",
            FindingStatus.REVIEW,
            "Developer options are on. Turn them off when you are finished installing.",
            "Open developer options",
            "development",
        )
        null -> finding(
            "developer_options",
            "Developer options",
            FindingStatus.UNKNOWN,
            "Developer options state could not be read.",
            "Open developer options",
            "development",
        )
    }

    private fun usbDebugging(snapshot: DeviceSnapshot): Finding = when (snapshot.usbDebugging) {
        false -> finding("usb_debugging", "USB debugging", FindingStatus.CLEAR, "USB debugging is off.")
        true -> finding(
            "usb_debugging",
            "USB debugging",
            FindingStatus.REVIEW,
            "USB debugging is on. Turn it off when you are finished installing.",
            "Open developer options",
            "development",
        )
        null -> finding(
            "usb_debugging",
            "USB debugging",
            FindingStatus.UNKNOWN,
            "USB debugging state could not be read.",
            "Open developer options",
            "development",
        )
    }

    private fun veil(snapshot: DeviceSnapshot): Finding {
        if (!snapshot.veilInstalled) {
            return finding(
                "veil",
                "VEIL VPN",
                FindingStatus.REVIEW,
                "VEIL (${Audit.VEIL_PACKAGE}) is not installed. Always-on VPN and lockdown cannot be confirmed.",
                "Open VPN settings",
                "vpn",
            )
        }
        val alwaysOn = if (snapshot.alwaysOnVpnReadable) snapshot.alwaysOnVpnPackage.orEmpty() else null
        val lockdown = if (snapshot.alwaysOnLockdownReadable) snapshot.alwaysOnLockdown == true else null
        val active = snapshot.activeVpnPackages
        val activeText = when {
            active == null -> "The active VPN owner could not be read."
            active.isEmpty() -> "No active VPN network was reported."
            else -> "Active VPN package: ${active.joinToString()}."
        }
        if (alwaysOn == Audit.VEIL_PACKAGE && lockdown == true) {
            return finding(
                "veil",
                "VEIL VPN",
                FindingStatus.CLEAR,
                "VEIL is the always-on VPN and lockdown is on. $activeText",
                "Open VPN settings",
                "vpn",
            )
        }
        val alwaysText = when (alwaysOn) {
            null -> "The always-on VPN setting could not be read. Without device owner, Android often hides it."
            "" -> "Always-on VPN is not set."
            Audit.VEIL_PACKAGE -> "VEIL is the always-on VPN."
            else -> "Always-on VPN is $alwaysOn, not VEIL."
        }
        val lockdownText = when (lockdown) {
            null -> "Lockdown could not be read."
            true -> "Lockdown is on."
            false -> "Lockdown is off, so traffic can leave the tunnel when VEIL drops."
        }
        return finding(
            "veil",
            "VEIL VPN",
            FindingStatus.REVIEW,
            "$alwaysText $lockdownText $activeText Confirm both switches in VPN settings. This app cannot set them.",
            "Open VPN settings",
            "vpn",
        )
    }

    private fun accessibility(snapshot: DeviceSnapshot): Finding {
        val services = snapshot.accessibilityServices
            ?: return finding(
                "accessibility",
                "Accessibility",
                FindingStatus.UNKNOWN,
                "Enabled accessibility services could not be read.",
                "Open accessibility settings",
                "accessibility",
            )
        if (services.isEmpty()) {
            return finding(
                "accessibility",
                "Accessibility",
                FindingStatus.CLEAR,
                "No accessibility service is enabled.",
            )
        }
        return finding(
            "accessibility",
            "Accessibility",
            FindingStatus.REVIEW,
            "Accessibility services can read the screen. Review each one.",
            "Open accessibility settings",
            "accessibility",
            services.map { component ->
                val pkg = component.substringBefore('/')
                FindingRow(component, "Enabled service", "app:$pkg")
            },
        )
    }

    private fun deviceAdmins(snapshot: DeviceSnapshot): Finding {
        val admins = snapshot.deviceAdmins
            ?: return finding(
                "device_admins",
                "Device admins",
                FindingStatus.UNKNOWN,
                "Device admin list could not be read.",
                "Open security settings",
                "security",
            )
        if (admins.isEmpty()) {
            return finding("device_admins", "Device admins", FindingStatus.CLEAR, "No device admin is active.")
        }
        return finding(
            "device_admins",
            "Device admins",
            FindingStatus.REVIEW,
            "Active device admins can enforce policy on this phone. This app is not a device admin.",
            "Review device admins",
            "device_admin",
            admins.map { FindingRow(it, "Active admin", "device_admin") },
        )
    }

    private fun unknownInstallers(snapshot: DeviceSnapshot): Finding {
        val candidates = snapshot.apps.filter { !it.system && it.declaresInstallPackages }
        val allowed = candidates.filter { it.unknownInstallAllowed == true }
        val unreadable = candidates.filter { it.unknownInstallAllowed == null }
        return when {
            allowed.isNotEmpty() -> finding(
                "unknown_installers",
                "Unknown-source installers",
                FindingStatus.REVIEW,
                buildString {
                    append("${allowed.size} non-system app")
                    if (allowed.size != 1) append("s")
                    append(" may install unknown apps.")
                    if (unreadable.isNotEmpty()) {
                        append(" ${unreadable.size} more declare the permission, and that allow state could not be read.")
                    }
                },
                "Review installers",
                "unknown:${allowed.first().packageName}",
                allowed.map {
                    FindingRow(it.label, it.packageName, "unknown:${it.packageName}")
                },
            )
            unreadable.isNotEmpty() -> finding(
                "unknown_installers",
                "Unknown-source installers",
                FindingStatus.UNKNOWN,
                "${unreadable.size} non-system apps declare REQUEST_INSTALL_PACKAGES. The allow state could not be read.",
                "Review installers",
                "unknown:${unreadable.first().packageName}",
                unreadable.map { FindingRow(it.label, it.packageName, "unknown:${it.packageName}") },
            )
            else -> finding(
                "unknown_installers",
                "Unknown-source installers",
                FindingStatus.CLEAR,
                "No non-system app is allowed to install unknown apps.",
            )
        }
    }

    private fun oemAnalytics(snapshot: DeviceSnapshot): Finding {
        val present = snapshot.apps.filter { it.packageName in Audit.OEM_ANALYTICS }
        if (present.isEmpty()) {
            return finding(
                "oem_analytics",
                "OEM analytics packages",
                FindingStatus.CLEAR,
                "None of the named Transsion analytics packages are installed.",
            )
        }
        return finding(
            "oem_analytics",
            "OEM analytics packages",
            FindingStatus.REVIEW,
            "Named OEM analytics packages are installed. This app cannot disable them without device owner or root. " +
                "An OEM update can restore a privileged package.",
            "Open app settings",
            "app:${present.first().packageName}",
            present.map { FindingRow(it.label, it.packageName, "app:${it.packageName}") },
        )
    }

    private fun versionDate(versionName: String?): Int? {
        if (versionName.isNullOrBlank()) return null
        return Regex("(\\d{8})").find(versionName)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun finding(
        id: String,
        title: String,
        status: FindingStatus,
        summary: String,
        actionLabel: String? = null,
        actionDestination: String? = null,
        rows: List<FindingRow> = emptyList(),
    ) = Finding(id, title, status, summary, actionLabel, actionDestination, rows)
}
