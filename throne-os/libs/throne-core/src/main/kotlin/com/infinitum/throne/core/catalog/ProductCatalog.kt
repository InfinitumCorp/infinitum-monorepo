package com.infinitum.throne.core.catalog

import com.infinitum.throne.core.home.Edition
import com.infinitum.throne.core.home.Launchable
import org.json.JSONObject
import java.net.URI

data class Product(
    val id: String,
    val name: String,
    val summary: String,
    val url: String?,
    val hosts: List<String>,
    val packages: List<String>,
    val cumulusId: Boolean,
    val keterOnly: Boolean,
)

enum class ShelfKind { NATIVE, WEB, PLACEHOLDER }

data class ShelfItem(
    val productId: String,
    val name: String,
    val kind: ShelfKind,
    val packageName: String?,
    val className: String?,
)

data class ProductShelf(
    val infinitum: List<ShelfItem>,
    val others: List<Launchable>,
)

object ProductCatalog {
    const val AUTH_HOST = "id.cumuluscloud.app"
    const val SUITE_PACKAGE = "com.infinitum.throne.suite"
    const val SUITE_ACTIVITY = "com.infinitum.throne.suite.ShellActivity"
    const val EXTRA_PRODUCT_ID = "com.infinitum.throne.suite.PRODUCT_ID"

    fun parse(json: String): List<Product> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw CatalogFormatException("products.json is not valid JSON.")
        }
        val array = root.optJSONArray("products") ?: throw CatalogFormatException("products.json has no products.")
        val products = ArrayList<Product>(array.length())
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw CatalogFormatException("Product $index is not an object.")
            val id = item.optString("id").trim()
            if (!id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
                throw CatalogFormatException("Bad product id.")
            }
            if (!seen.add(id)) throw CatalogFormatException("Duplicate product id.")
            val name = item.optString("name").trim()
            if (name.isEmpty() || name.length > 80) throw CatalogFormatException("Bad product name.")
            val summary = if (item.isNull("summary")) "" else item.optString("summary").trim()
            // Android's built-in org.json turns a JSON null into the text "null" in optString().
            val url = if (item.isNull("url")) null else item.optString("url").trim().ifEmpty { null }
            if (url != null) requireHttps(url)
            val hosts = mutableListOf<String>()
            val hostArray = item.optJSONArray("hosts")
            if (hostArray != null) {
                for (hostIndex in 0 until hostArray.length()) {
                    hosts += normalizeHost(hostArray.optString(hostIndex))
                }
            }
            if (url != null) {
                val startHost = URI(url).host?.lowercase() ?: throw CatalogFormatException("Bad product URL.")
                if (startHost !in hosts) hosts += startHost
            }
            val packages = mutableListOf<String>()
            val packageArray = item.optJSONArray("packages")
            if (packageArray != null) {
                for (packageIndex in 0 until packageArray.length()) {
                    val pkg = packageArray.optString(packageIndex).trim()
                    if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
                        throw CatalogFormatException("Bad package name.")
                    }
                    packages += pkg
                }
            }
            val cumulusId = item.optBoolean("cumulusId", false)
            val keterOnly = when (item.optString("edition").trim().lowercase()) {
                "all" -> false
                "keter" -> true
                else -> throw CatalogFormatException("Bad edition.")
            }
            products += Product(id, name, summary, url, hosts, packages, cumulusId, keterOnly)
        }
        if (products.isEmpty()) throw CatalogFormatException("products.json is empty.")
        return products
    }

    fun visible(products: List<Product>, edition: Edition): List<Product> {
        return products.filter { edition == Edition.KETER || !it.keterOnly }
    }

    /**
     * Top-level https navigation may stay on the product hosts.
     * Cumulus://ID is allowed only when that product's site already offers it.
     * There is no JavaScript bridge and no extra analytics host.
     */
    fun allowsNavigation(product: Product, rawUrl: String): Boolean {
        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (product.cumulusId && host == AUTH_HOST) return true
        return host in product.hosts
    }

    private fun requireHttps(url: String) {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw CatalogFormatException("Bad product URL.")
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrEmpty()) {
            throw CatalogFormatException("Product URLs must be https.")
        }
    }

    private fun normalizeHost(raw: String): String {
        val host = raw.trim().lowercase().trimEnd('.')
        if (!host.matches(Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+"))) {
            throw CatalogFormatException("Bad product host.")
        }
        return host
    }
}

class CatalogFormatException(message: String) : Exception(message)

object ShelfArranger {
    fun arrange(
        products: List<Product>,
        apps: List<Launchable>,
        edition: Edition,
        selfPackage: String,
        suitePackage: String = ProductCatalog.SUITE_PACKAGE,
    ): ProductShelf {
        val byPackage = LinkedHashMap<String, Launchable>()
        for (app in apps) {
            byPackage.putIfAbsent(app.packageName, app)
        }
        val claimed = HashSet<String>()
        val infinitum = ArrayList<ShelfItem>()
        for (product in products) {
            claimed += product.packages
            if (product.keterOnly && edition != Edition.KETER) continue
            val native = product.packages.firstNotNullOfOrNull { byPackage[it] }
            infinitum += when {
                native != null -> ShelfItem(product.id, product.name, ShelfKind.NATIVE, native.packageName, native.className)
                product.url != null -> ShelfItem(product.id, product.name, ShelfKind.WEB, null, null)
                else -> ShelfItem(product.id, product.name, ShelfKind.PLACEHOLDER, null, null)
            }
        }
        val others = apps
            .filter { it.packageName != selfPackage && it.packageName != suitePackage && it.packageName !in claimed }
            .distinctBy { "${it.packageName}/${it.className}" }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }, { it.className }))
        return ProductShelf(infinitum, others)
    }
}
