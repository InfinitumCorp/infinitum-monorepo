package com.infinitum.throne.core.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException

/**
 * Application-layer messages carried as text.
 *
 * Passphrase mode: PBKDF2-HMAC-SHA256, a random 16-byte salt, AES-256-GCM.
 * X25519 mode: an ephemeral key, HKDF-SHA256 over the shared secret, AES-256-GCM.
 * Neither mode is the Signal protocol, libsodium's crypto_box, or a FIPS module.
 * An X25519 public key that arrives over SMS is not authenticated until the
 * safety number is compared out of band.
 */
class VaultOpenException : Exception("Could not open this message.")

object VaultMessages {
    const val TOKEN_PREFIX = "THRONEVAULT1"
    const val PBKDF2_ITERATIONS = 310_000
    const val MAX_INPUT_CHARS = 100_000
    const val MAX_PLAINTEXT_BYTES = 8_000
    const val MIN_PASSPHRASE = 8

    private const val VERSION: Byte = 1
    private const val MODE_PASSPHRASE: Byte = 1
    private const val MODE_X25519: Byte = 2
    private const val MAX_ITERATIONS = 600_000
    private val X25519_INFO = "THRONE][OS vault x25519 v1".encodeToByteArray()
    private val SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00,
    )
    private val token = Regex("THRONEVAULT1\\.([PX])\\.([A-Za-z0-9_-]+)")

    fun containsToken(text: String): Boolean {
        if (text.length > MAX_INPUT_CHARS) return false
        return token.containsMatchIn(text.replace(Regex("\\s+"), ""))
    }

    fun shareText(armored: String): String = buildString {
        append("THRONE][OS™ encrypted message. This is ciphertext. ")
        append("Open it in THRONE][OS™ Vault. ")
        append("Plain SMS and MMS are not end-to-end encrypted.")
        append('\n')
        append(armored)
    }

    fun encryptPassphrase(
        plaintext: ByteArray,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(plaintext.isNotEmpty()) { "Write a message first." }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "That message is too long for this vault." }
        require(passphrase.size >= MIN_PASSPHRASE) { "Use at least $MIN_PASSPHRASE characters." }
        val password = utf8(passphrase)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val derived = pbkdf2HmacSha256(password, salt, PBKDF2_ITERATIONS, 32)
        password.fill(0)
        try {
            val key = SecretKeySpec(derived, "AES")
            val iterations = intBytes(PBKDF2_ITERATIONS)
            val aad = byteArrayOf(VERSION, MODE_PASSPHRASE) + iterations + salt
            val body = AesGcm.encrypt(key, nonce, aad, plaintext)
            val payload = byteArrayOf(VERSION, MODE_PASSPHRASE) + iterations + salt + nonce + body
            return armor('P', payload)
        } finally {
            derived.fill(0)
        }
    }

    fun decryptPassphrase(text: String, passphrase: CharArray): ByteArray {
        val payload = payload(text, 'P')
        if (payload.size < 2 + 4 + 16 + 12 + 16) throw VaultOpenException()
        if (payload[0] != VERSION || payload[1] != MODE_PASSPHRASE) throw VaultOpenException()
        val iterations = intAt(payload, 2)
        if (iterations < 100_000 || iterations > MAX_ITERATIONS) throw VaultOpenException()
        val salt = payload.copyOfRange(6, 22)
        val nonce = payload.copyOfRange(22, 34)
        val body = payload.copyOfRange(34, payload.size)
        val password = utf8(passphrase)
        val derived = pbkdf2HmacSha256(password, salt, iterations, 32)
        password.fill(0)
        try {
            val key: SecretKey = SecretKeySpec(derived, "AES")
            val aad = byteArrayOf(VERSION, MODE_PASSPHRASE) + intBytes(iterations) + salt
            return open(key, nonce, aad, body)
        } finally {
            derived.fill(0)
        }
    }

    fun encryptX25519(
        plaintext: ByteArray,
        recipientRawPublic: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(plaintext.isNotEmpty()) { "Write a message first." }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "That message is too long for this vault." }
        rejectPublic(recipientRawPublic)
        val ephemeral = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val ephemeralRaw = rawPublicFrom(ephemeral.public)
        val shared = agree(ephemeral.private, recipientRawPublic)
        val derived = hkdfSha256(shared, ephemeralRaw, X25519_INFO, 32)
        shared.fill(0)
        destroyQuietly(ephemeral.private)
        try {
            val key = SecretKeySpec(derived, "AES")
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val aad = byteArrayOf(VERSION, MODE_X25519) + ephemeralRaw
            val body = AesGcm.encrypt(key, nonce, aad, plaintext)
            val payload = byteArrayOf(VERSION, MODE_X25519) + ephemeralRaw + nonce + body
            return armor('X', payload)
        } finally {
            derived.fill(0)
        }
    }

    fun decryptX25519(text: String, recipientPrivate: PrivateKey): ByteArray {
        val payload = payload(text, 'X')
        if (payload.size < 2 + 32 + 12 + 16) throw VaultOpenException()
        if (payload[0] != VERSION || payload[1] != MODE_X25519) throw VaultOpenException()
        val ephemeralRaw = payload.copyOfRange(2, 34)
        val nonce = payload.copyOfRange(34, 46)
        val body = payload.copyOfRange(46, payload.size)
        val shared = try {
            agree(recipientPrivate, ephemeralRaw)
        } catch (_: VaultOpenException) {
            throw VaultOpenException()
        } catch (_: Exception) {
            throw VaultOpenException()
        }
        val derived = hkdfSha256(shared, ephemeralRaw, X25519_INFO, 32)
        shared.fill(0)
        try {
            val key = SecretKeySpec(derived, "AES")
            val aad = byteArrayOf(VERSION, MODE_X25519) + ephemeralRaw
            return open(key, nonce, aad, body)
        } finally {
            derived.fill(0)
        }
    }

    fun publicPayload(rawPublic: ByteArray): String {
        rejectPublic(rawPublic)
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublic)
        return "throne-vault:x25519:v1:$body"
    }

    fun parsePublicPayload(text: String): ByteArray {
        val trimmed = text.trim()
        val encoded = if (trimmed.startsWith("throne-vault:x25519:v1:")) {
            trimmed.substringAfter("throne-vault:x25519:v1:")
        } else {
            trimmed
        }.replace(Regex("\\s+"), "")
        val raw = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("That is not an X25519 public key.")
        }
        try {
            rejectPublic(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("That is not an X25519 public key.")
        }
        return raw
    }

    fun safetyNumber(rawPublic: ByteArray): String {
        rejectPublic(rawPublic)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(rawPublic)
        return digest.copyOf(16).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
    }

    fun rawPublicFrom(key: PublicKey): ByteArray {
        val encoded = key.encoded ?: throw IllegalArgumentException("Missing public key encoding.")
        if (encoded.size == 32) return encoded.copyOf()
        if (encoded.size == SPKI_PREFIX.size + 32 &&
            encoded.copyOfRange(0, SPKI_PREFIX.size).contentEquals(SPKI_PREFIX)
        ) {
            return encoded.copyOfRange(SPKI_PREFIX.size, encoded.size)
        }
        throw IllegalArgumentException("Unrecognized X25519 public key.")
    }

    fun publicKeyFromRaw(raw: ByteArray): PublicKey {
        rejectPublic(raw)
        val spki = SPKI_PREFIX + raw
        return KeyFactory.getInstance("X25519").generatePublic(X509EncodedKeySpec(spki))
    }

    fun privateKeyFromPkcs8(pkcs8: ByteArray): PrivateKey {
        return KeyFactory.getInstance("X25519").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    fun agree(privateKey: PrivateKey, peerRawPublic: ByteArray): ByteArray {
        val peer = publicKeyFromRaw(peerRawPublic)
        val keystoreBacked = privateKey.javaClass.name.contains("AndroidKeyStore")
        val agreement = if (keystoreBacked) {
            KeyAgreement.getInstance("X25519", "AndroidKeyStore")
        } else {
            KeyAgreement.getInstance("X25519")
        }
        agreement.init(privateKey)
        agreement.doPhase(peer, true)
        val secret = agreement.generateSecret()
        if (secret.size != 32 || secret.all { it == 0.toByte() }) {
            secret.fill(0)
            throw VaultOpenException()
        }
        return secret
    }

    private fun open(key: SecretKey, nonce: ByteArray, aad: ByteArray, body: ByteArray): ByteArray {
        return try {
            AesGcm.decrypt(key, nonce, aad, body)
        } catch (_: Exception) {
            throw VaultOpenException()
        }
    }

    private fun payload(text: String, mode: Char): ByteArray {
        if (text.length > MAX_INPUT_CHARS) throw VaultOpenException()
        val compact = text.replace(Regex("\\s+"), "")
        val match = token.find(compact) ?: throw VaultOpenException()
        if (match.groupValues[1][0] != mode) throw VaultOpenException()
        return try {
            Base64.getUrlDecoder().decode(match.groupValues[2])
        } catch (_: IllegalArgumentException) {
            throw VaultOpenException()
        }
    }

    private fun armor(mode: Char, payload: ByteArray): String {
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        return "$TOKEN_PREFIX.$mode.$body"
    }

    private fun rejectPublic(raw: ByteArray) {
        require(raw.size == 32) { "That is not an X25519 public key." }
        require(raw.any { it != 0.toByte() }) { "That is not an X25519 public key." }
    }

    private fun utf8(passphrase: CharArray): ByteArray {
        return String(passphrase).encodeToByteArray()
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun intAt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun destroyQuietly(key: PrivateKey) {
        val destroyable = key as? javax.security.auth.Destroyable ?: return
        try {
            destroyable.destroy()
        } catch (_: DestroyFailedException) {
            // The provider kept the copy. The ephemeral object is dropped.
        }
    }
}
