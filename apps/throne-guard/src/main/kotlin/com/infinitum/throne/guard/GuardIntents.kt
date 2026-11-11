package com.infinitum.throne.guard

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import com.infinitum.throne.core.audit.Audit

object GuardIntents {
    fun open(context: Context, destination: String) {
        if (destination == "vault") {
            context.packageManager.getLaunchIntentForPackage("com.infinitum.throne.vault")?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        for (intent in candidates(context, destination)) {
            val candidate = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (candidate.resolveActivity(context.packageManager) == null) continue
            try {
                context.startActivity(candidate)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
    }

    private fun candidates(context: Context, destination: String): List<Intent> {
        return when {
            destination == "security" -> listOf(Intent(Settings.ACTION_SECURITY_SETTINGS))
            destination == "password" -> listOf(
                Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
            )
            destination == "accessibility" -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            destination == "device_admin" -> listOf(
                Intent().setClassName("com.android.settings", "com.android.settings.DeviceAdminSettings"),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
            )
            destination == "development" -> listOf(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            destination == "vpn" -> listOf(Intent(Settings.ACTION_VPN_SETTINGS))
            destination == "messages" -> messages(context)
            destination == "default_sms" -> listOf(
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, Audit.MESSAGES_PACKAGE),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            )
            destination == "notification_settings" -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, Audit.MESSAGES_PACKAGE),
                Intent("android.settings.NOTIFICATION_SETTINGS"),
                Intent(Settings.ACTION_SETTINGS),
            )
            destination == "wireless" -> listOf(
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            destination.startsWith("app:") -> listOf(details(destination.removePrefix("app:")))
            destination.startsWith("unknown:") -> {
                val pkg = destination.removePrefix("unknown:")
                listOf(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$pkg")),
                    details(pkg),
                )
            }
            else -> emptyList()
        }
    }

    private fun messages(context: Context): List<Intent> {
        val explicit = Intent().setClassName(
            Audit.MESSAGES_PACKAGE,
            "com.google.android.apps.messaging.ui.appsettings.ApplicationSettingsActivity",
        )
        val launch = context.packageManager.getLaunchIntentForPackage(Audit.MESSAGES_PACKAGE)
        return listOfNotNull(explicit, launch, details(Audit.MESSAGES_PACKAGE))
    }

    private fun details(packageName: String): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
    }
}
