package com.infinitum.throne.core.home

enum class Edition {
    KETER,
    ARCHANGEL,
    ;

    /** Exact product styling. Gradle flavor ids are keter and archangel. */
    val label: String
        get() = when (this) {
            KETER -> "^KETER"
            ARCHANGEL -> "ARCH//ANGEL"
        }
}

enum class HomePage {
    INFINITUM,
    OTHERS,
}

object EditionPolicy {
    const val LAUNCHER_PACKAGE = "com.infinitum.throne.launcher"

    /**
     * Order is the INFINITUM page. Packages that are not installed are skipped.
     * Cumulus ID is the civilian identity app named in the design.
     * Guard and Vault are this layer. They are not military apps.
     */
    val infinitumOrder: List<String> = listOf(
        "com.infinitum.veilvpn",
        "app.cumuluscloud.mail",
        "app.scalvs",
        "com.infinitum.fabric",
        "com.infinitum.aether",
        "com.infinitum.architect",
        "com.conjure.app",
        "app.infinitum.veil.browser",
        "com.infinitum.eidetic.cam",
        "com.infinitum.hrvst.cam",
        "id.cumuluscloud.app",
        "com.infinitum.throne.guard",
        "com.infinitum.throne.vault",
    )

    /** Confirmed military package. ARCH//ANGEL hides these on both pages. */
    val milPackages: Set<String> = setOf("com.infinitum.aether")

    fun parse(raw: String): Edition {
        val key = raw.trim().uppercase().removePrefix("^").replace("//", "").replace("-", "").replace(" ", "")
        return if (key == "KETER") Edition.KETER else Edition.ARCHANGEL
    }

    fun isMil(packageName: String): Boolean = packageName in milPackages

    fun visible(packageName: String, edition: Edition): Boolean {
        return !(edition == Edition.ARCHANGEL && isMil(packageName))
    }
}

data class Launchable(
    val packageName: String,
    val className: String,
    val label: String,
)

data class ArrangedHome(
    val infinitum: List<Launchable>,
    val others: List<Launchable>,
)

object HomeCatalog {
    fun arrange(apps: List<Launchable>, edition: Edition, selfPackage: String): ArrangedHome {
        val unique = LinkedHashMap<String, Launchable>()
        for (app in apps) {
            if (app.packageName == selfPackage) continue
            if (!EditionPolicy.visible(app.packageName, edition)) continue
            unique.putIfAbsent("${app.packageName}/${app.className}", app)
        }
        val rank = EditionPolicy.infinitumOrder.withIndex().associate { it.value to it.index }
        val infinitum = unique.values
            .filter { it.packageName in rank }
            .sortedWith(compareBy({ rank.getValue(it.packageName) }, { it.label.lowercase() }, { it.className }))
        val others = unique.values
            .filter { it.packageName !in rank }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }, { it.className }))
        return ArrangedHome(infinitum, others)
    }
}

object GlyphPixels {
    fun desaturateThreshold(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
        val out = IntArray(pixels.size)
        val corners = intArrayOf(0, width - 1, (height - 1) * width, pixels.lastIndex)
        val cornerLums = corners.map { luminance(pixels[it]) }
        val uniformBackground = corners.all { alpha(pixels[it]) > 220 } &&
            (cornerLums.max() - cornerLums.min() <= 18)
        if (uniformBackground) {
            val background = cornerLums.sum() / cornerLums.size
            for (i in pixels.indices) {
                if (alpha(pixels[i]) < 32) continue
                if (kotlin.math.abs(luminance(pixels[i]) - background) > 28) {
                    out[i] = BLACK
                }
            }
            return out
        }
        var sum = 0L
        var count = 0
        for (pixel in pixels) {
            if (alpha(pixel) > 32) {
                sum += luminance(pixel)
                count++
            }
        }
        if (count == 0) return out
        val lightIsInk = sum / count > 140
        for (i in pixels.indices) {
            if (alpha(pixels[i]) < 32) continue
            val light = luminance(pixels[i]) >= 128
            if (light == lightIsInk) out[i] = BLACK
        }
        return out
    }

    private fun alpha(pixel: Int): Int = pixel ushr 24

    private fun luminance(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private const val BLACK = 0xFF000000.toInt()
}
