package com.infinitum.throne.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.URLEncoder

data class ContactHit(
    val id: Long,
    val name: String,
    val number: String?,
)

data class SettingHit(
    val title: String,
    val action: String,
)

data class FileHit(
    val uri: String,
    val name: String,
)

data class SearchHits(
    val apps: List<AppEntry> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
    val settings: List<SettingHit> = emptyList(),
    val files: List<FileHit> = emptyList(),
    val askContacts: Boolean = false,
)

object PhoneSearch {
    const val VEIL_PACKAGE = "app.infinitum.veil.browser"

    val settings: List<SettingHit> = listOf(
        SettingHit("Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
        SettingHit("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingHit("Display", Settings.ACTION_DISPLAY_SETTINGS),
        SettingHit("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        SettingHit("Apps", Settings.ACTION_APPLICATION_SETTINGS),
        SettingHit("Sound", Settings.ACTION_SOUND_SETTINGS),
        SettingHit("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingHit("Security", Settings.ACTION_SECURITY_SETTINGS),
        SettingHit("Notifications", "android.settings.NOTIFICATION_SETTINGS"),
        SettingHit("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingHit("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        SettingHit("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingHit("Date and time", Settings.ACTION_DATE_SETTINGS),
        SettingHit("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingHit("Data usage", Settings.ACTION_DATA_USAGE_SETTINGS),
        SettingHit("VPN", Settings.ACTION_VPN_SETTINGS),
        SettingHit("Language", Settings.ACTION_LOCALE_SETTINGS),
        SettingHit("Privacy", Settings.ACTION_PRIVACY_SETTINGS),
    )

    fun matches(text: String, query: String): Boolean {
        val haystack = text.lowercase()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return false
        if (haystack == needle || haystack.startsWith(needle) || haystack.contains(needle)) return true
        if (haystack.split(' ', '.', '/', '-').any { it.startsWith(needle) }) return true
        if (needle.length < 2) return false
        var index = 0
        for (char in haystack) {
            if (char == needle[index]) {
                index++
                if (index == needle.length) return true
            }
        }
        return false
    }

    fun rank(label: String, query: String): Int {
        val name = label.lowercase()
        val needle = query.trim().lowercase()
        return when {
            name == needle -> 0
            name.startsWith(needle) -> 1
            name.split(' ', '.', '/', '-').any { it.startsWith(needle) } -> 2
            name.contains(needle) -> 3
            else -> 4
        }
    }

    fun exactApp(apps: List<AppEntry>, query: String): AppEntry? {
        val needle = query.trim()
        return apps.firstOrNull { it.label.equals(needle, ignoreCase = true) }
    }

    fun veilUri(query: String): Uri {
        val trimmed = query.trim()
        if (looksLikeUrl(trimmed)) {
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            return Uri.parse(withScheme)
        }
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        return Uri.parse("https://duckduckgo.com/?q=$encoded")
    }

    fun looksLikeUrl(raw: String): Boolean {
        if (raw.any { it.isWhitespace() }) return false
        if (raw.startsWith("http://") || raw.startsWith("https://")) return true
        return raw.matches(Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}([/:].*)?$"))
    }

    fun canReadContacts(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun canReadMedia(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED)
    }

    fun contacts(context: Context, query: String): List<ContactHit> {
        if (!canReadContacts(context)) return emptyList()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val like = "%${query.trim()}%"
        val hits = ArrayList<ContactHit>()
        val seen = HashSet<Long>()
        try {
            context.contentResolver.query(uri, projection, selection, arrayOf(like, like), null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && hits.size < 12) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    if (!seen.add(id)) continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                    val number = if (numberCol >= 0) cursor.getString(numberCol) else null
                    if (matches(name, query) || (number != null && matches(number, query))) {
                        hits += ContactHit(id, name.ifBlank { number ?: "Contact" }, number)
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return hits
    }

    fun files(context: Context, query: String): List<FileHit> {
        if (!canReadMedia(context)) return emptyList()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val hits = ArrayList<FileHit>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf("%${query.trim()}%"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext() && hits.size < 8) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else continue
                    if (!matches(name, query)) continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    hits += FileHit(uri.toString(), name)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return hits
    }
}
