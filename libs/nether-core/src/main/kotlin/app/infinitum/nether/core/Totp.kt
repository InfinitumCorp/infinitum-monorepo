package app.infinitum.nether.core

import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object Totp {
    fun code(
        secret: ByteArray,
        timeMillis: Long,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: String = "HmacSHA1",
    ): String {
        val counter = timeMillis / 1000 / periodSeconds
        val message = ByteArray(8)
        var value = counter
        for (index in 7 downTo 0) {
            message[index] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret, algorithm))
        val hash = mac.doFinal(message)
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val modulus = 10.0.pow(digits).toInt()
        return (binary % modulus).toString().padStart(digits, '0')
    }

    fun remainingSeconds(timeMillis: Long, periodSeconds: Int = 30): Int {
        val second = (timeMillis / 1000).toInt()
        return periodSeconds - (second % periodSeconds)
    }

    fun parseOtpAuth(uri: String): TotpSpec {
        val parsed = URI(uri.trim())
        require(parsed.scheme.equals("otpauth", ignoreCase = true)) { "Not an otpauth link." }
        val query = parsed.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val bits = part.split('=', limit = 2)
            if (bits.size == 2) bits[0] to java.net.URLDecoder.decode(bits[1], Charsets.UTF_8) else null
        }.toMap()
        val secret = query["secret"] ?: throw IllegalArgumentException("Missing secret.")
        val digits = query["digits"]?.toIntOrNull() ?: 6
        val period = query["period"]?.toIntOrNull() ?: 30
        val algo = when (query["algorithm"]?.uppercase()) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
        val label = parsed.path.trimStart('/').substringAfter(':').ifBlank { parsed.path.trimStart('/') }
        return TotpSpec(label, decodeBase32(secret), digits, period, algo, uri.trim())
    }

    fun decodeBase32(raw: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = raw.uppercase().filter { it in alphabet }
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (char in cleaned) {
            buffer = (buffer shl 5) or alphabet.indexOf(char)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out += ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return out.toByteArray()
    }

    data class TotpSpec(
        val label: String,
        val secret: ByteArray,
        val digits: Int,
        val period: Int,
        val algorithm: String,
        val uri: String,
    )
}
