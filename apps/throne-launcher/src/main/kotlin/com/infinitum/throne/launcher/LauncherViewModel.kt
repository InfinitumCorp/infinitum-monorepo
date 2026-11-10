package com.infinitum.throne.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
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
    val kind: EntryKind,
    val packageName: String?,
    val className: String?,
    val productId: String?,
)

data class LauncherUiState(
    val edition: Edition,
    val query: String = "",
    val loading: Boolean = true,
    val infinitum: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
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
                infinitum = loaded.first,
                others = loaded.second,
            )
        }
    }

    fun onQuery(value: String) {
        state.value = state.value.copy(query = value.take(80))
    }

    @Suppress("DEPRECATION")
    private fun load(): Pair<List<AppEntry>, List<AppEntry>> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val glyphs = HashMap<String, ImageBitmap?>()
        val launchables = resolved.map { info ->
            val packageName = info.activityInfo.packageName
            val className = info.activityInfo.name
            val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
            val key = "$packageName/$className"
            glyphs[key] = try {
                info.loadIcon(pm)?.let { IconGlyphs.toGlyph(it, 144) }
            } catch (_: Throwable) {
                null
            }
            Launchable(packageName, className, label)
        }
        val products = loadCatalog()
        if (products.isEmpty()) {
            val arranged = HomeCatalog.arrange(
                apps = launchables,
                edition = state.value.edition,
                selfPackage = EditionPolicy.LAUNCHER_PACKAGE,
            )
            fun entries(apps: List<Launchable>, page: HomePage) = apps.map { app ->
                val key = "${app.packageName}/${app.className}"
                AppEntry(
                    key = key,
                    label = app.label,
                    page = page,
                    icon = glyphs[key],
                    kind = EntryKind.NATIVE,
                    packageName = app.packageName,
                    className = app.className,
                    productId = null,
                )
            }
            return entries(arranged.infinitum, HomePage.INFINITUM) to entries(arranged.others, HomePage.OTHERS)
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
                label = item.name,
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
                icon = glyphs[key],
                kind = EntryKind.NATIVE,
                packageName = app.packageName,
                className = app.className,
                productId = null,
            )
        }
        return infinitum to others
    }

    private fun loadCatalog() = try {
        val json = getApplication<Application>().assets.open("products.json").bufferedReader().use { it.readText() }
        ProductCatalog.parse(json)
    } catch (_: Exception) {
        emptyList()
    }
}
