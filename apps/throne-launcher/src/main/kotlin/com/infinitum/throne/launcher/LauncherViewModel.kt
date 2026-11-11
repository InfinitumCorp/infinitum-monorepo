package com.infinitum.throne.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinitum.throne.core.catalog.ProductCatalog
import com.infinitum.throne.core.catalog.ShelfArranger
import com.infinitum.throne.core.catalog.ShelfKind
import com.infinitum.throne.core.home.Edition
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.core.home.HomeCatalog
import com.infinitum.throne.core.home.HomePage
import com.infinitum.throne.core.home.Launchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EntryKind { NATIVE, WEB, PLACEHOLDER }

data class AppEntry(
    val key: String,
    val label: String,
    val page: HomePage,
    val icon: ImageBitmap?,
    val colorIcon: Boolean = false,
    val kind: EntryKind,
    val packageName: String?,
    val className: String?,
    val productId: String?,
)

data class MessageDock(
    val packageName: String,
    val icon: ImageBitmap?,
)

private data class LoadedHome(
    val infinitum: List<AppEntry>,
    val others: List<AppEntry>,
    val messages: MessageDock?,
)

data class LauncherUiState(
    val edition: Edition,
    val query: String = "",
    val loading: Boolean = true,
    val showAbout: Boolean = false,
    val infinitum: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
    val messages: MessageDock? = null,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    var state = androidx.compose.runtime.mutableStateOf(
        LauncherUiState(edition = EditionPolicy.parse(BuildConfig.EDITION)),
    )
        private set

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) { load() }
            state.value = state.value.copy(
                loading = false,
                infinitum = loaded.infinitum,
                others = loaded.others,
                messages = loaded.messages,
            )
        }
    }

    fun onQuery(value: String) {
        state.value = state.value.copy(query = value.take(80))
    }

    fun openAbout() {
        state.value = state.value.copy(showAbout = true)
    }

    fun closeAbout() {
        state.value = state.value.copy(showAbout = false)
    }

    @Suppress("DEPRECATION")
    private fun load(): LoadedHome {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val glyphs = HashMap<String, ImageBitmap?>()
        val colorIcons = HashMap<String, ImageBitmap?>()
        val launchables = resolved.map { info ->
            val packageName = info.activityInfo.packageName
            val className = info.activityInfo.name
            val rawLabel = info.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
            val label = shortLabel(packageName, null, rawLabel)
            val key = "$packageName/$className"
            val drawable = try {
                info.loadIcon(pm)
            } catch (_: Throwable) {
                null
            }
            glyphs[key] = drawable?.let { IconGlyphs.toGlyph(it, 256) }
            colorIcons[key] = drawable?.let { IconGlyphs.foreground(it, 256) }
            Launchable(packageName, className, label)
        }
        val products = loadCatalog()
        if (products.isEmpty()) {
            val arranged = HomeCatalog.arrange(
                apps = launchables,
                edition = state.value.edition,
                selfPackage = EditionPolicy.LAUNCHER_PACKAGE,
            )
            fun entries(
                apps: List<Launchable>,
                page: HomePage,
                ink: Map<String, ImageBitmap?>,
                color: Map<String, ImageBitmap?>,
            ) = apps.map { app ->
                val key = "${app.packageName}/${app.className}"
                val others = page == HomePage.OTHERS
                AppEntry(
                    key = key,
                    label = app.label,
                    page = page,
                    icon = if (others) color[key] else ink[key],
                    colorIcon = others,
                    kind = EntryKind.NATIVE,
                    packageName = app.packageName,
                    className = app.className,
                    productId = null,
                )
            }
            return LoadedHome(
                entries(arranged.infinitum, HomePage.INFINITUM, glyphs, colorIcons),
                entries(arranged.others, HomePage.OTHERS, glyphs, colorIcons),
                resolveMessages(pm),
            )
        }
        val shelf = ShelfArranger.arrange(
            products = products,
            apps = launchables,
            edition = state.value.edition,
            selfPackage = EditionPolicy.LAUNCHER_PACKAGE,
        )
        val infinitum = shelf.infinitum.map { item ->
            val nativeKey = if (item.packageName != null && item.className != null) {
                "${item.packageName}/${item.className}"
            } else {
                null
            }
            AppEntry(
                key = "product:${item.productId}",
                label = shortLabel(item.packageName, item.productId, item.name),
                page = HomePage.INFINITUM,
                icon = nativeKey?.let { glyphs[it] },
                kind = when (item.kind) {
                    ShelfKind.NATIVE -> EntryKind.NATIVE
                    ShelfKind.WEB -> EntryKind.WEB
                    ShelfKind.PLACEHOLDER -> EntryKind.PLACEHOLDER
                },
                packageName = item.packageName,
                className = item.className,
                productId = item.productId,
            )
        }
        val others = shelf.others.map { app ->
            val key = "${app.packageName}/${app.className}"
            AppEntry(
                key = key,
                label = app.label,
                page = HomePage.OTHERS,
                icon = colorIcons[key],
                colorIcon = true,
                kind = EntryKind.NATIVE,
                packageName = app.packageName,
                className = app.className,
                productId = null,
            )
        }
        return LoadedHome(infinitum, others, resolveMessages(pm))
    }

    private fun resolveMessages(pm: PackageManager): MessageDock? {
        val packages = LinkedHashSet<String>()
        Telephony.Sms.getDefaultSmsPackage(getApplication())?.trim()?.takeIf { it.isNotEmpty() }?.let { packages += it }
        packages += "com.google.android.apps.messaging"
        val sendTo = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        pm.queryIntentActivities(sendTo, PackageManager.MATCH_ALL).forEach { info ->
            packages += info.activityInfo.packageName
        }
        for (pkg in packages) {
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            val icon = try {
                pm.getApplicationIcon(pkg).let { IconGlyphs.foreground(it, 256) }
            } catch (_: Throwable) {
                null
            }
            return MessageDock(pkg, icon)
        }
        return null
    }

    private fun loadCatalog() = try {
        val json = getApplication<Application>().assets.open("products.json").bufferedReader().use { it.readText() }
        ProductCatalog.parse(json)
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        val SHORT_LABELS = mapOf(
            "com.infinitum.throne.launcher" to "THRONE",
            "com.infinitum.throne.guard" to "Guard",
            "com.infinitum.throne.vault" to "Vault",
            "com.infinitum.throne.suite" to "Suite",
            "com.infinitum.eidetic.cam" to "EIDETIC",
            "com.infinitum.hrvst.cam" to "HRVST",
        )
        val SHORT_BY_PRODUCT = mapOf(
            "eidetic" to "EIDETIC",
            "hrvst" to "HRVST",
        )

        fun shortLabel(packageName: String?, productId: String?, fallback: String): String {
            SHORT_BY_PRODUCT[productId]?.let { return it }
            SHORT_LABELS[packageName]?.let { return it }
            return fallback
        }
    }
}
