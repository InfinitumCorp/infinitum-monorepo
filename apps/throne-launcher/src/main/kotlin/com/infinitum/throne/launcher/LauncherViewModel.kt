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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    val hits: SearchHits = SearchHits(),
    val contactsDismissed: Boolean = false,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private var searchJob: Job? = null

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
            if (state.value.query.isNotBlank()) runSearch(state.value.query)
        }
    }

    fun onQuery(value: String) {
        val query = value.take(80)
        state.value = state.value.copy(query = query)
        runSearch(query)
    }

    fun dismissContactsPrompt() {
        state.value = state.value.copy(contactsDismissed = true, hits = state.value.hits.copy(askContacts = false))
    }

    fun onContactsGranted() {
        state.value = state.value.copy(contactsDismissed = false)
        runSearch(state.value.query)
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            state.value = state.value.copy(hits = SearchHits())
            return
        }
        val apps = state.value.infinitum + state.value.others
        val askContacts = !PhoneSearch.canReadContacts(getApplication()) && !state.value.contactsDismissed
        searchJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                SearchHits(
                    apps = apps
                        .filter { it.packageName != GUARD_PACKAGE && it.packageName != VAULT_PACKAGE }
                        .filter {
                            PhoneSearch.matches(it.label, trimmed) ||
                                (it.packageName != null && PhoneSearch.matches(it.packageName, trimmed))
                        }
                        .sortedBy { PhoneSearch.rank(it.label, trimmed) },
                    contacts = PhoneSearch.contacts(getApplication(), trimmed),
                    settings = PhoneSearch.settings.filter { PhoneSearch.matches(it.title, trimmed) },
                    files = PhoneSearch.files(getApplication(), trimmed),
                    askContacts = askContacts,
                )
            }
            if (isActive && state.value.query.trim() == trimmed) {
                state.value = state.value.copy(hits = hits)
            }
        }
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
            val placed = placeHome(
                entries(arranged.infinitum, HomePage.INFINITUM, glyphs, colorIcons),
                entries(arranged.others, HomePage.OTHERS, glyphs, colorIcons),
                launchables,
            )
            return LoadedHome(placed.first, placed.second, resolveMessages(pm))
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
        val placed = placeHome(infinitum, others, launchables)
        return LoadedHome(placed.first, placed.second, resolveMessages(pm))
    }

    private fun placeHome(
        infinitum: List<AppEntry>,
        others: List<AppEntry>,
        launchables: List<Launchable>,
    ): Pair<List<AppEntry>, List<AppEntry>> {
        val metatron = placeMetatron(infinitum, others, launchables)
        return placeNether(metatron.first, metatron.second, launchables)
    }

    private fun placeNether(
        infinitum: List<AppEntry>,
        others: List<AppEntry>,
        launchables: List<Launchable>,
    ): Pair<List<AppEntry>, List<AppEntry>> {
        val home = infinitum.toMutableList()
        val rest = others.toMutableList()
        val vaultAt = home.indexOfFirst { it.packageName == VAULT_PACKAGE }
        home.removeAll { it.packageName == VAULT_PACKAGE || it.packageName == NETHER_PACKAGE || it.productId == "nether" }
        rest.removeAll { it.packageName == VAULT_PACKAGE || it.packageName == NETHER_PACKAGE || it.productId == "nether" }
        val native = launchables.firstOrNull { it.packageName == NETHER_PACKAGE }
        val nether = AppEntry(
            key = "product:nether",
            label = "Nether",
            page = HomePage.INFINITUM,
            icon = null,
            colorIcon = false,
            kind = if (native != null) EntryKind.NATIVE else EntryKind.PLACEHOLDER,
            packageName = native?.packageName,
            className = native?.className,
            productId = "nether",
        )
        val index = if (vaultAt >= 0) vaultAt.coerceAtMost(home.size) else home.size
        home.add(index, nether)
        return home to rest
    }

    private fun placeMetatron(
        infinitum: List<AppEntry>,
        others: List<AppEntry>,
        launchables: List<Launchable>,
    ): Pair<List<AppEntry>, List<AppEntry>> {
        val home = infinitum.filterNot { it.productId == "metatron" }.toMutableList()
        val rest = others.filterNot { it.productId == "metatron" }.toMutableList()
        val native = launchables.firstOrNull { app ->
            val pkg = app.packageName.lowercase()
            pkg.contains("metatron") || pkg.contains("mttrn") || app.label.contains("METATRON", ignoreCase = true)
        }
        if (native != null) {
            rest.removeAll { it.packageName == native.packageName }
            home.removeAll { it.packageName == native.packageName }
        }
        val metatron = AppEntry(
            key = "product:metatron",
            label = "METATRON¹⁰⁰",
            page = HomePage.INFINITUM,
            icon = null,
            colorIcon = false,
            kind = if (native != null) EntryKind.NATIVE else EntryKind.WEB,
            packageName = native?.packageName,
            className = native?.className,
            productId = "metatron",
        )
        val guardAt = home.indexOfFirst { it.packageName == GUARD_PACKAGE }
        if (guardAt >= 0) {
            home.removeAt(guardAt)
            home.add(guardAt, metatron)
        } else {
            val vaultAt = home.indexOfFirst { it.packageName == VAULT_PACKAGE }
            home.add(if (vaultAt >= 0) vaultAt else home.size, metatron)
        }
        home.removeAll { it.packageName == GUARD_PACKAGE }
        rest.removeAll { it.packageName == GUARD_PACKAGE }
        return home.map(::markTextIcon) to rest.map(::markTextIcon)
    }

    private fun markTextIcon(entry: AppEntry): AppEntry {
        if (entry.packageName == HRVST_PACKAGE || entry.productId == "hrvst") {
            return entry.copy(productId = "hrvst", colorIcon = false)
        }
        if (entry.packageName == EIDETIC_PACKAGE || entry.productId == "eidetic") {
            return entry.copy(productId = "eidetic", colorIcon = false)
        }
        if (entry.packageName == CONJURE_PACKAGE || entry.productId == "conjure") {
            return entry.copy(productId = "conjure", colorIcon = false)
        }
        if (entry.packageName == NETHER_PACKAGE || entry.productId == "nether") {
            return entry.copy(productId = "nether", colorIcon = false)
        }
        return entry
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
            "app.infinitum.nether" to "Nether",
            "com.infinitum.throne.suite" to "Suite",
            "com.infinitum.eidetic.cam" to "EIDETIC",
            "com.infinitum.hrvst.cam" to "HRVST",
        )
        val SHORT_BY_PRODUCT = mapOf(
            "eidetic" to "EIDETIC",
            "hrvst" to "HRVST",
            "metatron" to "METATRON¹⁰⁰",
            "nether" to "Nether",
        )
        const val GUARD_PACKAGE = "com.infinitum.throne.guard"
        const val VAULT_PACKAGE = "com.infinitum.throne.vault"
        const val NETHER_PACKAGE = "app.infinitum.nether"
        const val HRVST_PACKAGE = "com.infinitum.hrvst.cam"
        const val EIDETIC_PACKAGE = "com.infinitum.eidetic.cam"
        const val CONJURE_PACKAGE = "com.conjure.app"

        fun shortLabel(packageName: String?, productId: String?, fallback: String): String {
            SHORT_BY_PRODUCT[productId]?.let { return it }
            SHORT_LABELS[packageName]?.let { return it }
            return fallback
        }
    }
}
