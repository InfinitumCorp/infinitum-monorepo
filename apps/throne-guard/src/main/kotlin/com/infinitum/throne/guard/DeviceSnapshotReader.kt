package com.infinitum.throne.guard

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import com.infinitum.throne.core.audit.Audit
import com.infinitum.throne.core.audit.DeviceSnapshot
import com.infinitum.throne.core.audit.InstalledApp

class DeviceSnapshotReader(private val context: Context) {
    fun read(): DeviceSnapshot {
        val apps = readApps()
        val messages = packageInfo(Audit.MESSAGES_PACKAGE)
        val (alwaysReadable, alwaysPackage) = alwaysOnPackage()
        val (lockReadable, lockdown) = lockdown()
        val (dnsReadable, dnsMode, dnsHost) = privateDns()
        return DeviceSnapshot(
            apps = apps,
            accessibilityServices = accessibility(),
            deviceAdmins = admins(),
            privateDnsReadable = dnsReadable,
            privateDnsMode = dnsMode,
            privateDnsSpecifier = dnsHost,
            deviceSecure = deviceSecure(),
            encryptionStatus = encryption(),
            developerOptions = globalFlag(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            usbDebugging = globalFlag(Settings.Global.ADB_ENABLED),
            veilInstalled = apps.any { it.packageName == Audit.VEIL_PACKAGE } || installed(Audit.VEIL_PACKAGE),
            alwaysOnVpnReadable = alwaysReadable,
            alwaysOnVpnPackage = alwaysPackage,
            alwaysOnLockdownReadable = lockReadable,
            alwaysOnLockdown = lockdown,
            activeVpnPackages = activeVpnPackages(),
            defaultSmsPackage = defaultSms(),
            messagesInstalled = messages != null,
            messagesVersionName = messages?.second,
            lockScreenNotificationsShown = secureFlag("lock_screen_show_notifications"),
            lockScreenPrivateContentAllowed = secureFlag("lock_screen_allow_private_notifications"),
        )
    }

    private fun readApps(): List<InstalledApp> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
        return packages.mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val requested = info.requestedPermissions ?: emptyArray()
            val flags = info.requestedPermissionsFlags ?: IntArray(0)
            val granted = requested.mapIndexedNotNull { index, permission ->
                val flag = flags.getOrNull(index) ?: 0
                if (flag and PackageManager.PERMISSION_GRANTED != 0 && permission in DANGEROUS) {
                    permission
                } else {
                    null
                }
            }
            val system = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            InstalledApp(
                packageName = info.packageName,
                label = appInfo.loadLabel(pm)?.toString().orEmpty().ifBlank { info.packageName },
                system = system,
                grantedDangerous = granted,
                declaresInstallPackages = "android.permission.REQUEST_INSTALL_PACKAGES" in requested,
                unknownInstallAllowed = if ("android.permission.REQUEST_INSTALL_PACKAGES" in requested) {
                    installAllowed(info.packageName, appInfo.uid)
                } else {
                    false
                },
                installerPackage = installer(info.packageName),
            )
        }
    }

    private fun installAllowed(packageName: String, uid: Int): Boolean? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
            val mode = appOps.unsafeCheckOpNoThrow("android:request_install_packages", uid, packageName)
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun installer(packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun accessibility(): List<String>? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun admins(): List<String>? {
        return try {
            context.getSystemService(DevicePolicyManager::class.java)
                ?.activeAdmins
                ?.map { it.flattenToShortString() }
                ?: emptyList()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun privateDns(): Triple<Boolean, String?, String?> {
        if (Build.VERSION.SDK_INT < 28) return Triple(false, null, null)
        return try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            val host = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
            Triple(true, mode, host)
        } catch (_: SecurityException) {
            Triple(false, null, null)
        }
    }

    private fun deviceSecure(): Boolean? {
        return try {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure
        } catch (_: Exception) {
            null
        }
    }

    private fun encryption(): Int? {
        return try {
            context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus
        } catch (_: Exception) {
            null
        }
    }

    private fun globalFlag(name: String): Boolean? {
        return try {
            Settings.Global.getInt(context.contentResolver, name) == 1
        } catch (_: Settings.SettingNotFoundException) {
            false
        } catch (_: SecurityException) {
            null
        }
    }

    private fun secureFlag(name: String): Boolean? {
        return try {
            Settings.Secure.getInt(context.contentResolver, name) == 1
        } catch (_: Settings.SettingNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun alwaysOnPackage(): Pair<Boolean, String?> {
        return try {
            val value = Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
            true to value?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            false to null
        }
    }

    private fun lockdown(): Pair<Boolean, Boolean?> {
        return try {
            true to (Settings.Secure.getInt(context.contentResolver, "always_on_vpn_lockdown") == 1)
        } catch (_: Settings.SettingNotFoundException) {
            true to false
        } catch (_: SecurityException) {
            false to null
        }
    }

    private fun activeVpnPackages(): List<String>? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val names = linkedSetOf<String>()
            for (network in connectivity.allNetworks) {
                val caps = connectivity.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                val uid = caps.ownerUid
                if (uid == Process.INVALID_UID) continue
                context.packageManager.getPackagesForUid(uid)?.let { names += it }
            }
            names.toList()
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultSms(): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
    }

    private fun installed(packageName: String): Boolean = packageInfo(packageName) != null

    private fun packageInfo(packageName: String): Pair<String, String?>? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            info.packageName to info.versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        val DANGEROUS = setOf(
            Audit.CAMERA,
            Audit.MICROPHONE,
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
        )
    }
}
