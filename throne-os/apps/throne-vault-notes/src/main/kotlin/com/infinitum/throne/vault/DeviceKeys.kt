package com.infinitum.throne.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.infinitum.throne.core.crypto.VaultMessages
import com.infinitum.throne.core.crypto.VaultSealer
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

data class LoadedIdentity(
    val rawPublic: ByteArray,
    val privateKey: PrivateKey,
    val inKeystore: Boolean,
)

object DeviceKeys {
    private const val AES_ALIAS = "throne_vault_aes"
    private const val X25519_ALIAS = "throne_vault_x25519"
    private val IDENTITY_AAD = "THRONE][OS identity v1".encodeToByteArray()
    val NOTES_AAD = "THRONE][OS notes v1".encodeToByteArray()

    fun aesKey(): SecretKey {
        val store = keyStore()
        (store.getKey(AES_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            AES_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun loadOrCreateIdentity(directory: File, aes: SecretKey): LoadedIdentity {
        val file = File(directory, "identity.bin")
        if (file.exists() && file.length() > 0L) return readIdentity(file, aes)
        val created = createIdentity(aes)
        writeIdentity(file, created)
        return LoadedIdentity(created.rawPublic, created.privateKey, created.inKeystore)
    }

    private fun createIdentity(aes: SecretKey): StoredIdentity {
        if (Build.VERSION.SDK_INT >= 33) {
            val generated = generateKeystoreIdentity()
            if (generated != null) return generated
        }
        val pair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val pkcs8 = pair.private.encoded ?: error("The identity key could not be encoded.")
        val sealed = VaultSealer.seal(aes, pkcs8, IDENTITY_AAD)
        pkcs8.fill(0)
        return StoredIdentity(
            rawPublic = VaultMessages.rawPublicFrom(pair.public),
            privateKey = pair.private,
            inKeystore = false,
            sealedPrivate = sealed,
        )
    }

    private fun generateKeystoreIdentity(): StoredIdentity? {
        return try {
            val generator = KeyPairGenerator.getInstance("X25519", "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(X25519_ALIAS, KeyProperties.PURPOSE_AGREE_KEY).build(),
            )
            val pair = generator.generateKeyPair()
            val raw = VaultMessages.rawPublicFrom(pair.public)
            StoredIdentity(raw, pair.private, inKeystore = true, sealedPrivate = null)
        } catch (_: Exception) {
            null
        }
    }

    private fun readIdentity(file: File, aes: SecretKey): LoadedIdentity {
        val bytes = file.readBytes()
        if (bytes.size < 4 + 1 + 32 || !bytes.copyOfRange(0, 4).contentEquals("TVID".encodeToByteArray())) {
            error("The identity file could not be read. Nothing was deleted.")
        }
        val raw = bytes.copyOfRange(5, 37)
        return when (bytes[4].toInt().toChar()) {
            'K' -> {
                val privateKey = keyStore().getKey(X25519_ALIAS, null) as? PrivateKey
                    ?: error("The identity key is missing from the keystore. Nothing was deleted.")
                LoadedIdentity(raw, privateKey, inKeystore = true)
            }
            'S' -> {
                if (bytes.size < 37 + 4) error("The identity file could not be read. Nothing was deleted.")
                val length = intAt(bytes, 37)
                if (length <= 0 || bytes.size < 41 + length) {
                    error("The identity file could not be read. Nothing was deleted.")
                }
                val sealed = bytes.copyOfRange(41, 41 + length)
                val pkcs8 = VaultSealer.open(aes, sealed, IDENTITY_AAD)
                try {
                    LoadedIdentity(raw, VaultMessages.privateKeyFromPkcs8(pkcs8), inKeystore = false)
                } finally {
                    pkcs8.fill(0)
                }
            }
            else -> error("The identity file could not be read. Nothing was deleted.")
        }
    }

    private fun writeIdentity(file: File, identity: StoredIdentity) {
        val body = java.io.ByteArrayOutputStream()
        body.write("TVID".encodeToByteArray())
        if (identity.inKeystore) {
            body.write('K'.code)
            body.write(identity.rawPublic)
        } else {
            val sealed = identity.sealedPrivate ?: error("Missing wrapped identity.")
            body.write('S'.code)
            body.write(identity.rawPublic)
            body.write((sealed.size ushr 24) and 0xFF)
            body.write((sealed.size ushr 16) and 0xFF)
            body.write((sealed.size ushr 8) and 0xFF)
            body.write(sealed.size and 0xFF)
            body.write(sealed)
        }
        val tmp = File(file.parentFile, "identity.bin.tmp")
        tmp.writeBytes(body.toByteArray())
        if (!tmp.renameTo(file)) {
            file.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    fun selfTest(identity: LoadedIdentity): Boolean {
        return try {
            val secret = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val armored = VaultMessages.encryptX25519(secret, identity.rawPublic)
            val opened = VaultMessages.decryptX25519(armored, identity.privateKey)
            val ok = opened.contentEquals(secret)
            secret.fill(0)
            opened.fill(0)
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun intAt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}

private data class StoredIdentity(
    val rawPublic: ByteArray,
    val privateKey: PrivateKey,
    val inKeystore: Boolean,
    val sealedPrivate: ByteArray?,
)
