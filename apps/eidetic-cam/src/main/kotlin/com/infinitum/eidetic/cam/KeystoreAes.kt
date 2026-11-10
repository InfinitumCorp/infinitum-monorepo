package com.infinitum.eidetic.cam

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.infinitum.throne.core.crypto.VaultSealer
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object KeystoreAes {
    fun seal(alias: String, plaintext: ByteArray, aad: ByteArray): ByteArray =
        VaultSealer.seal(key(alias), plaintext, aad)

    fun open(alias: String, sealed: ByteArray, aad: ByteArray): ByteArray =
        VaultSealer.open(key(alias), sealed, aad)

    fun key(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
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
}

internal fun writeAtomic(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(file)) {
        file.writeBytes(tmp.readBytes())
        tmp.delete()
    }
}
