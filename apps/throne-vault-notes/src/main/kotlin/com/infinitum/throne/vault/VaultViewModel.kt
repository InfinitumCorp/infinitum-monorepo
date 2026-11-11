package com.infinitum.throne.vault

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinitum.throne.core.audit.Audit
import com.infinitum.throne.core.crypto.SavedCipher
import com.infinitum.throne.core.crypto.VaultContact
import com.infinitum.throne.core.crypto.VaultMessages
import com.infinitum.throne.core.crypto.VaultNote
import com.infinitum.throne.core.crypto.VaultNotebook
import com.infinitum.throne.core.home.EditionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class VaultScreen { Home, Compose, Open, Identity, Notes }

enum class ComposeMode { Passphrase, Contact }

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    val edition = EditionPolicy.parse(BuildConfig.EDITION)
    var screen by mutableStateOf(VaultScreen.Home)
        private set
    var busy by mutableStateOf(false)
        private set
    var banner by mutableStateOf<String?>(null)
        private set
    var identityReady by mutableStateOf(false)
        private set
    var publicPayload by mutableStateOf<String?>(null)
        private set
    var safetyNumber by mutableStateOf<String?>(null)
        private set
    var contacts by mutableStateOf<List<VaultContact>>(emptyList())
        private set
    var notes by mutableStateOf<List<VaultNote>>(emptyList())
        private set
    var composeBody by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var passphraseAgain by mutableStateOf("")
    var mode by mutableStateOf(ComposeMode.Passphrase)
    var selectedContact by mutableStateOf<String?>(null)
    var pastedKey by mutableStateOf("")
    var ciphertext by mutableStateOf<String?>(null)
        private set
    var keepCopy by mutableStateOf(false)
    var openInput by mutableStateOf("")
    var openPassphrase by mutableStateOf("")
    var plaintext by mutableStateOf<String?>(null)
        private set
    var noteDraft by mutableStateOf("")
    var contactName by mutableStateOf("")
    var contactKey by mutableStateOf("")

    @Volatile private var repository: VaultRepository? = null
    @Volatile private var notebookWritable = false
    private var notebook = VaultNotebook()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = VaultRepository(getApplication())
                val book = try {
                    repo.loadNotebook().also { notebookWritable = true }
                } catch (_: Exception) {
                    notebookWritable = false
                    notebookError()
                    VaultNotebook()
                }
                repository = repo
                notebook = book
                withContext(Dispatchers.Main) {
                    identityReady = repo.identity != null
                    publicPayload = repo.publicPayload
                    safetyNumber = repo.safetyNumber
                    contacts = book.contacts
                    notes = book.notes
                    banner = repo.identityError
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    banner = "The device keystore could not be opened. Passphrase messages still work. Notes on this device do not."
                }
            }
        }
    }

    fun show(screen: VaultScreen) {
        ciphertext = null
        plaintext = null
        this.screen = screen
    }

    fun acceptShare(text: String?) {
        val value = text?.take(VaultMessages.MAX_INPUT_CHARS) ?: return
        if (value.isBlank()) return
        if (VaultMessages.containsToken(value)) {
            openInput = value
            plaintext = null
            screen = VaultScreen.Open
        } else {
            composeBody = value.take(VaultMessages.MAX_PLAINTEXT_BYTES)
            ciphertext = null
            screen = VaultScreen.Compose
        }
    }

    fun encrypt() {
        val body = composeBody.encodeToByteArray()
        if (body.isEmpty()) {
            banner = "Write a message first."
            return
        }
        val currentMode = mode
        val pass = passphrase
        val again = passphraseAgain
        val contact = selectedContact
        val paste = pastedKey
        val known = contacts
        viewModelScope.launch {
            busy = true
            banner = null
            val result = withContext(Dispatchers.Default) {
                runCatching { seal(body, currentMode, pass, again, contact, paste, known) }
            }
            body.fill(0)
            busy = false
            result.onSuccess { armored ->
                ciphertext = armored
                composeBody = ""
                passphrase = ""
                passphraseAgain = ""
                if (keepCopy) storeCipher(armored)
            }.onFailure { error ->
                banner = error.message ?: "Could not encrypt this message."
            }
        }
    }

    fun openMessage() {
        val text = openInput
        val tokenIsX = text.replace(Regex("\\s+"), "").contains("THRONEVAULT1.X.")
        viewModelScope.launch {
            busy = true
            banner = null
            val chars = openPassphrase.toCharArray()
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    if (tokenIsX) {
                        val key = repository?.privateKey() ?: error("The identity key is not available on this phone.")
                        VaultMessages.decryptX25519(text, key)
                    } else {
                        VaultMessages.decryptPassphrase(text, chars)
                    }
                }
            }
            chars.fill('\u0000')
            busy = false
            result.onSuccess { bytes ->
                plaintext = bytes.decodeToString()
                bytes.fill(0)
                openPassphrase = ""
            }.onFailure {
                plaintext = null
                banner = "Could not open this message."
            }
        }
    }

    fun clearPlaintext() {
        plaintext = null
    }

    fun copyCiphertext(context: Context) {
        val value = ciphertext ?: return
        copySensitive(context, VaultMessages.shareText(value))
        banner = "Ciphertext copied. The clipboard may still be visible to the app you paste into."
    }

    fun copyPublicKey(context: Context) {
        val value = publicPayload ?: return
        copySensitive(context, value)
        banner = "Public key copied."
    }

    fun shareToMessages(context: Context) {
        val value = ciphertext ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, VaultMessages.shareText(value))
            setPackage(Audit.MESSAGES_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Share ciphertext"))
        }
    }

    fun saveContact() {
        val name = contactName.trim()
        if (name.isEmpty() || name.length > 80) {
            banner = "Give the contact a short name."
            return
        }
        val raw = try {
            VaultMessages.parsePublicPayload(contactKey)
        } catch (_: IllegalArgumentException) {
            banner = "That is not an X25519 public key."
            return
        }
        val next = contacts.filterNot { it.name.equals(name, ignoreCase = true) } + VaultContact(name, raw)
        contacts = next
        notebook = notebook.copy(contacts = next)
        contactName = ""
        contactKey = ""
        selectedContact = name
        persist()
    }

    fun removeContact(name: String) {
        contacts = contacts.filterNot { it.name == name }
        notebook = notebook.copy(contacts = contacts)
        if (selectedContact == name) selectedContact = null
        persist()
    }

    fun saveNote() {
        val text = noteDraft.trim()
        if (text.isEmpty()) {
            banner = "Write a note first."
            return
        }
        val note = VaultNote(UUID.randomUUID().toString(), System.currentTimeMillis(), text)
        notes = listOf(note) + notes
        notebook = notebook.copy(notes = notes)
        noteDraft = ""
        persist()
        banner = "Sealed on this device. A factory reset destroys it."
    }

    fun deleteNote(id: String) {
        notes = notes.filterNot { it.id == id }
        notebook = notebook.copy(notes = notes)
        persist()
    }

    private fun seal(
        body: ByteArray,
        currentMode: ComposeMode,
        pass: String,
        again: String,
        contact: String?,
        paste: String,
        known: List<VaultContact>,
    ): String {
        return when (currentMode) {
            ComposeMode.Passphrase -> {
                if (pass != again) error("The passphrases do not match.")
                val chars = pass.toCharArray()
                try {
                    VaultMessages.encryptPassphrase(body, chars)
                } finally {
                    chars.fill('\u0000')
                }
            }
            ComposeMode.Contact -> {
                val raw = contact?.let { name -> known.firstOrNull { it.name == name }?.rawPublic }
                    ?: VaultMessages.parsePublicPayload(paste)
                VaultMessages.encryptX25519(body, raw)
            }
        }
    }

    private fun storeCipher(armored: String) {
        val item = SavedCipher(UUID.randomUUID().toString(), System.currentTimeMillis(), armored)
        notebook = notebook.copy(saved = listOf(item) + notebook.saved)
        persist()
    }

    private fun persist() {
        val repo = repository ?: run {
            banner = "Notes stay in memory only until the keystore is available."
            return
        }
        if (!notebookWritable) {
            banner = "The existing notebook could not be opened, so nothing was written over it."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.saveNotebook(notebook)
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    banner = "The sealed notebook could not be written."
                }
            }
        }
    }

    private suspend fun notebookError() {
        withContext(Dispatchers.Main) {
            banner = "The notebook on this device could not be opened. Nothing was overwritten."
        }
    }

    private fun copySensitive(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("THRONE][OS", text)
        if (Build.VERSION.SDK_INT >= 33) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        clipboard.setPrimaryClip(clip)
    }
}
