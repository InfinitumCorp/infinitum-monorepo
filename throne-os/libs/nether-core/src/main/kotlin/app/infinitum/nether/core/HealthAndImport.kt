package app.infinitum.nether.core

data class HealthReport(
    val weak: List<String>,
    val reused: List<String>,
    val old: List<String>,
)

object Health {
    private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

    fun report(document: VaultDocument, now: Long = System.currentTimeMillis()): HealthReport {
        val logins = document.entries.filter { it.type == EntryTypes.LOGIN && it.secret.isNotBlank() }
        val weak = logins.filter { PasswordGenerator.strength(it.secret) <= 1 }.map { it.title.ifBlank { it.id } }
        val reused = logins.groupBy { it.secret }.filter { it.value.size > 1 }.values.flatten().map { it.title.ifBlank { it.id } }
        val old = logins.filter { now - it.updatedAt > YEAR_MS }.map { it.title.ifBlank { it.id } }
        return HealthReport(weak, reused, old)
    }
}

object Importers {
    fun csv(text: String): List<NetherEntry> {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            fun col(name: String): String {
                val index = header.indexOf(name)
                return if (index >= 0 && index < row.size) row[index] else ""
            }
            when {
                "login_username" in header -> NetherEntry(
                    type = EntryTypes.LOGIN,
                    title = col("name"),
                    username = col("login_username"),
                    secret = col("login_password"),
                    notes = col("notes"),
                    url = col("login_uri"),
                    folder = col("folder"),
                    favorite = col("favorite").equals("true", true) || col("favorite") == "1",
                    totp = col("login_totp"),
                )
                "username" in header && "password" in header && "name" in header -> NetherEntry(
                    type = EntryTypes.LOGIN,
                    title = col("name"),
                    username = col("username"),
                    secret = col("password"),
                    url = col("url"),
                )
                "title" in header && "password" in header -> NetherEntry(
                    type = EntryTypes.LOGIN,
                    title = col("title"),
                    username = col("username"),
                    secret = col("password"),
                    notes = col("notes"),
                    url = col("url"),
                )
                else -> null
            }
        }
    }

    /**
     * Recognises a Throne Vault armor string. Nether does not open that app's files.
     * A decrypted export can be pasted as CSV instead.
     */
    fun throneVaultNote(text: String): String? {
        if (text.contains("THRONEVAULT1.")) {
            return "This is a locked Throne Vault message. Nether does not read Vault's private files. Paste a decrypted CSV export."
        }
        return null
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val char = text[i]
            when {
                quoted && char == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += cell.toString()
                    cell.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(char)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }
}

object EntryEdits {
    fun withNewSecret(entry: NetherEntry, secret: String, now: Long = System.currentTimeMillis()): NetherEntry {
        val history = if (entry.secret.isNotBlank() && entry.secret != secret) {
            (listOf(PasswordUse(entry.secret, entry.updatedAt)) + entry.history).take(10)
        } else {
            entry.history
        }
        return entry.copy(secret = secret, history = history, updatedAt = now)
    }
}
