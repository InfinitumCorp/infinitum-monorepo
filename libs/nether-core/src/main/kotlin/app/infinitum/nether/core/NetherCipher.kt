package app.infinitum.nether.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object NetherCipher {
    const val MAGIC = "NTHR"
    const val VERSION: Int = 1
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128

    fun seal(key: ByteArray, plaintext: ByteArray, salt: ByteArray, kdf: Int = NetherKdf.KDF_ARGON2ID): ByteArray {
        require(key.size == NetherKdf.KEY_LEN)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val body = aes(Cipher.ENCRYPT_MODE, key, nonce, plaintext)
        val header = ByteBuffer.allocate(47)
        header.put(MAGIC.toByteArray(Charsets.US_ASCII))
        header.putShort(VERSION.toShort())
        header.put(kdf.toByte())
        header.putInt(NetherKdf.MEMORY_KIB)
        header.putInt(NetherKdf.ITERATIONS)
        header.putInt(NetherKdf.PARALLELISM)
        header.put(salt)
        header.put(nonce)
        return header.array() + body
    }

    fun open(key: ByteArray, blob: ByteArray): ByteArray {
        val parsed = parse(blob)
        return aes(Cipher.DECRYPT_MODE, key, parsed.nonce, parsed.body)
    }

    fun parse(blob: ByteArray): Header {
        require(blob.size > 47) { "Vault file is too short." }
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Not a Nether vault." }
        val version = buf.short.toInt()
        require(version == VERSION) { "Unsupported Nether vault." }
        val kdf = buf.get().toInt() and 0xFF
        val memory = buf.int
        val iterations = buf.int
        val parallelism = buf.int
        val salt = ByteArray(NetherKdf.SALT_LEN).also { buf.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        return Header(kdf, memory, iterations, parallelism, salt, nonce, body)
    }

    fun passwordOpens(password: CharArray, blob: ByteArray): ByteArray? {
        val header = parse(blob)
        val key = NetherKdf.derive(password, header.salt, header.kdf)
        return try {
            open(key, blob)
            key
        } catch (_: AEADBadTagException) {
            key.fill(0)
            null
        }
    }

    private fun aes(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(input)
    }

    data class Header(
        val kdf: Int,
        val memoryKib: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val body: ByteArray,
    )
}
