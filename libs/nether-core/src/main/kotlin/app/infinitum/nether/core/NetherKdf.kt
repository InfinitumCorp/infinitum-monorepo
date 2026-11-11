package app.infinitum.nether.core

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * Argon2id with the OWASP minimum (19 MiB, 2 iterations, parallelism 1).
 * scrypt is only used if Argon2id cannot run. Both come from Bouncy Castle.
 */
object NetherKdf {
    const val KDF_ARGON2ID: Int = 1
    const val KDF_SCRYPT: Int = 2
    const val MEMORY_KIB: Int = 19_456
    const val ITERATIONS: Int = 2
    const val PARALLELISM: Int = 1
    const val SALT_LEN: Int = 16
    const val KEY_LEN: Int = 32

    fun randomSalt(): ByteArray = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }

    fun derive(password: CharArray, salt: ByteArray, kdf: Int = KDF_ARGON2ID): ByteArray {
        require(salt.size == SALT_LEN)
        return when (kdf) {
            KDF_ARGON2ID -> argon2id(password, salt)
            KDF_SCRYPT -> scrypt(password, salt)
            else -> throw IllegalArgumentException("Unknown KDF.")
        }
    }

    fun argon2id(password: CharArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ITERATIONS)
            .withMemoryAsKB(MEMORY_KIB)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(KEY_LEN)
        generator.generateBytes(password, out)
        return out
    }

    fun scrypt(password: CharArray, salt: ByteArray): ByteArray {
        val utf8 = Charsets.UTF_8.encode(CharBufferWrap.wrap(password)).let { buf ->
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            bytes
        }
        return try {
            SCrypt.generate(utf8, salt, 16_384, 8, 1, KEY_LEN)
        } finally {
            utf8.fill(0)
        }
    }

    /** Delay before the next guess. Caps at 30 seconds. */
    fun delayMillis(failures: Int): Long {
        if (failures <= 0) return 0L
        val shift = failures.coerceAtMost(8)
        return minOf(30_000L, 250L shl shift)
    }
}

private object CharBufferWrap {
    fun wrap(password: CharArray) = java.nio.CharBuffer.wrap(password)
}
