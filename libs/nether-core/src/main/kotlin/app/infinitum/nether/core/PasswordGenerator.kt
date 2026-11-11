package app.infinitum.nether.core

import java.security.SecureRandom
import kotlin.math.ln

object PasswordGenerator {
    private val random = SecureRandom()
    private val words = listOf(
        "able", "anchor", "apple", "arrow", "autumn", "baker", "beach", "birch", "bloom", "bridge",
        "cabin", "cedar", "cinder", "cloud", "coral", "crane", "daisy", "delta", "ember", "falcon",
        "field", "flint", "frost", "garden", "harbor", "hazel", "heron", "honey", "ivory", "jasper",
        "kettle", "lantern", "maple", "meadow", "north", "olive", "orchid", "otter", "pebble", "pine",
        "quartz", "raven", "river", "sail", "spruce", "stone", "summit", "thistle", "timber", "violet",
        "willow", "winter", "amber", "basin", "canyon", "cliff", "comet", "dawn", "dune", "eagle",
        "fern", "glade", "grove", "inlet", "island", "lagoon", "lark", "marble", "moss", "nest",
        "oak", "pearl", "ridge", "robin", "sable", "shore", "silk", "silver", "sparrow", "tide",
    )
    private val common = setOf(
        "password", "123456", "12345678", "qwerty", "letmein", "admin", "welcome", "iloveyou",
        "monkey", "dragon", "master", "login", "abc123", "trustno1", "passw0rd",
    )

    fun password(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        ambiguous: Boolean = false,
    ): String {
        val pool = StringBuilder()
        if (lower) pool.append(if (ambiguous) "abcdefghijkmnopqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz")
        if (upper) pool.append(if (ambiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (digits) pool.append(if (ambiguous) "23456789" else "0123456789")
        if (symbols) pool.append("!@#%^&*-_=+?")
        require(pool.isNotEmpty()) { "Pick at least one set." }
        val size = length.coerceIn(8, 128)
        val out = CharArray(size)
        val alphabet = pool.toString()
        for (i in out.indices) out[i] = alphabet[random.nextInt(alphabet.length)]
        return String(out)
    }

    fun passphrase(wordsCount: Int = 6, separator: String = "-"): String {
        val count = wordsCount.coerceIn(3, 12)
        return (1..count).joinToString(separator) { words[random.nextInt(words.size)] }
    }

    /** 0 very weak, 4 strong. Penalises common passwords, repeats, and sequences. */
    fun strength(password: String): Int {
        if (password.isEmpty()) return 0
        if (password.lowercase() in common) return 0
        var charset = 0
        if (password.any { it.isLowerCase() }) charset += 26
        if (password.any { it.isUpperCase() }) charset += 26
        if (password.any { it.isDigit() }) charset += 10
        if (password.any { !it.isLetterOrDigit() }) charset += 32
        var entropy = password.length * (ln(charset.coerceAtLeast(1).toDouble()) / ln(2.0))
        if (Regex("(.)\\1{2,}").containsMatchIn(password)) entropy -= 10
        if (Regex("abc|qwe|123|password", RegexOption.IGNORE_CASE).containsMatchIn(password)) entropy -= 12
        return when {
            entropy < 28 -> 0
            entropy < 36 -> 1
            entropy < 60 -> 2
            entropy < 80 -> 3
            else -> 4
        }
    }
}
