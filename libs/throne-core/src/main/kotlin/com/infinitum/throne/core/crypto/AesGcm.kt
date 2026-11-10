package com.infinitum.throne.core.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore and software keys both use this. The nonce is chosen by the cipher. */
object VaultSealer {
    fun seal(key: javax.crypto.SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray =
        AesGcm.seal(key, plaintext, aad)

    fun open(key: javax.crypto.SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray =
        AesGcm.open(key, sealed, aad)
}

internal object AesGcm {
    fun encrypt(key: SecretKey, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decrypt(key: SecretKey, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Seal with a provider-chosen nonce. Android Keystore keys that require
     * randomized encryption use this path. The caller does not supply the nonce.
     * Layout: nonce length (1 byte), nonce, ciphertext-and-tag.
     */
    fun seal(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val body = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        require(nonce.size in 1..255)
        return byteArrayOf(nonce.size.toByte()) + nonce + body
    }

    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 2) throw VaultOpenException()
        val nonceLen = sealed[0].toInt() and 0xFF
        if (nonceLen == 0 || sealed.size < 1 + nonceLen + 16) throw VaultOpenException()
        val nonce = sealed.copyOfRange(1, 1 + nonceLen)
        val body = sealed.copyOfRange(1 + nonceLen, sealed.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(body)
        } catch (_: Exception) {
            throw VaultOpenException()
        }
    }
}
