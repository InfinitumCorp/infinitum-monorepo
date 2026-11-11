package app.infinitum.nether.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object EntryTypes {
    const val LOGIN = "login"
    const val CARD = "card"
    const val NOTE = "note"
    const val IDENTITY = "identity"
    const val WIFI = "wifi"
    const val SEED = "seed"
    const val API_KEY = "apikey"
    val all = listOf(LOGIN, CARD, NOTE, IDENTITY, WIFI, SEED, API_KEY)
}

data class PasswordUse(
    val secret: String,
    val at: Long,
)

data class Attachment(
    val name: String,
    val mime: String,
    val data: ByteArray,
)

data class NetherEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String = EntryTypes.LOGIN,
    val title: String = "",
    val username: String = "",
    val secret: String = "",
    val notes: String = "",
    val url: String = "",
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val totp: String = "",
    val history: List<PasswordUse> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class VaultDocument(
    val entries: List<NetherEntry> = emptyList(),
    val folders: List<String> = emptyList(),
) {
    fun toJson(): ByteArray {
        val root = JSONObject()
        root.put("v", 1)
        root.put("folders", JSONArray(folders))
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("type", entry.type)
            obj.put("title", entry.title)
            obj.put("username", entry.username)
            obj.put("secret", entry.secret)
            obj.put("notes", entry.notes)
            obj.put("url", entry.url)
            obj.put("folder", entry.folder)
            obj.put("tags", JSONArray(entry.tags))
            obj.put("favorite", entry.favorite)
            obj.put("totp", entry.totp)
            obj.put("createdAt", entry.createdAt)
            obj.put("updatedAt", entry.updatedAt)
            val history = JSONArray()
            for (use in entry.history) {
                history.put(JSONObject().put("secret", use.secret).put("at", use.at))
            }
            obj.put("history", history)
            val files = JSONArray()
            for (file in entry.attachments) {
                files.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("mime", file.mime)
                        .put("data", java.util.Base64.getEncoder().encodeToString(file.data)),
                )
            }
            obj.put("attachments", files)
            array.put(obj)
        }
        root.put("entries", array)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJson(bytes: ByteArray): VaultDocument {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val folders = ArrayList<String>()
            val folderArray = root.optJSONArray("folders")
            if (folderArray != null) {
                for (i in 0 until folderArray.length()) folders += folderArray.optString(i)
            }
            val entries = ArrayList<NetherEntry>()
            val array = root.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tags = ArrayList<String>()
                val tagArray = obj.optJSONArray("tags")
                if (tagArray != null) {
                    for (t in 0 until tagArray.length()) tags += tagArray.optString(t)
                }
                val history = ArrayList<PasswordUse>()
                val historyArray = obj.optJSONArray("history")
                if (historyArray != null) {
                    for (h in 0 until historyArray.length()) {
                        val item = historyArray.getJSONObject(h)
                        history += PasswordUse(item.optString("secret"), item.optLong("at"))
                    }
                }
                val attachments = ArrayList<Attachment>()
                val files = obj.optJSONArray("attachments")
                if (files != null) {
                    for (f in 0 until files.length()) {
                        val file = files.getJSONObject(f)
                        attachments += Attachment(
                            file.optString("name"),
                            file.optString("mime"),
                            java.util.Base64.getDecoder().decode(file.optString("data")),
                        )
                    }
                }
                entries += NetherEntry(
                    id = obj.optString("id"),
                    type = obj.optString("type", EntryTypes.LOGIN),
                    title = obj.optString("title"),
                    username = obj.optString("username"),
                    secret = obj.optString("secret"),
                    notes = obj.optString("notes"),
                    url = obj.optString("url"),
                    folder = obj.optString("folder"),
                    tags = tags,
                    favorite = obj.optBoolean("favorite"),
                    totp = obj.optString("totp"),
                    history = history,
                    attachments = attachments,
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                )
            }
            return VaultDocument(entries, folders)
        }
    }
}

object NetherVault {
    fun create(password: CharArray, document: VaultDocument = VaultDocument()): ByteArray {
        val salt = NetherKdf.randomSalt()
        val key = NetherKdf.derive(password, salt)
        return try {
            NetherCipher.seal(key, document.toJson(), salt)
        } finally {
            key.fill(0)
        }
    }

    fun unlock(password: CharArray, blob: ByteArray): Unlock {
        val key = NetherCipher.passwordOpens(password, blob) ?: throw BadPasswordException()
        val document = try {
            VaultDocument.fromJson(NetherCipher.open(key, blob))
        } catch (error: Exception) {
            key.fill(0)
            throw error
        }
        return Unlock(document, key)
    }

    fun save(key: ByteArray, blob: ByteArray, document: VaultDocument): ByteArray {
        val header = NetherCipher.parse(blob)
        return NetherCipher.seal(key, document.toJson(), header.salt, header.kdf)
    }
}

class BadPasswordException : Exception("Wrong password.")

data class Unlock(val document: VaultDocument, val key: ByteArray)
