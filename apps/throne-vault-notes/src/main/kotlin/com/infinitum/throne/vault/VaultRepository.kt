package com.infinitum.throne.vault

import android.content.Context
import com.infinitum.throne.core.crypto.VaultMessages
import com.infinitum.throne.core.crypto.VaultNotebook
import com.infinitum.throne.core.crypto.VaultSealer
import java.io.File
import java.security.PrivateKey

class VaultRepository(context: Context) {
    private val directory = context.filesDir
    private val aes = DeviceKeys.aesKey()
    val identity: LoadedIdentity?
    val identityError: String?
    val publicPayload: String?
    val safetyNumber: String?

    init {
        val loaded = try {
            DeviceKeys.loadOrCreateIdentity(directory, aes)
        } catch (_: Exception) {
            null
        }
        if (loaded != null && DeviceKeys.selfTest(loaded)) {
            identity = loaded
            identityError = null
        } else if (loaded != null) {
            identity = null
            identityError = "The identity key failed a local round trip. Nothing was deleted. Passphrase messages still work."
        } else {
            identity = null
            identityError = "The identity key could not be opened. Nothing was deleted. Passphrase messages still work."
        }
        publicPayload = identity?.let { VaultMessages.publicPayload(it.rawPublic) }
        safetyNumber = identity?.let { VaultMessages.safetyNumber(it.rawPublic) }
    }

    fun privateKey(): PrivateKey? = identity?.privateKey

    fun loadNotebook(): VaultNotebook {
        val file = File(directory, "notebook.bin")
        if (!file.exists() || file.length() == 0L) return VaultNotebook()
        val plain = VaultSealer.open(aes, file.readBytes(), DeviceKeys.NOTES_AAD)
        return VaultNotebook.decode(plain)
    }

    fun saveNotebook(book: VaultNotebook) {
        val sealed = VaultSealer.seal(aes, book.encode(), DeviceKeys.NOTES_AAD)
        val tmp = File(directory, "notebook.bin.tmp")
        tmp.writeBytes(sealed)
        val dest = File(directory, "notebook.bin")
        if (!tmp.renameTo(dest)) {
            dest.writeBytes(sealed)
            tmp.delete()
        }
    }
}
