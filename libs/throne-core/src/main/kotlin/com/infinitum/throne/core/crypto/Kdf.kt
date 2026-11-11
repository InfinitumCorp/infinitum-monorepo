package com.infinitum.throne.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 over an explicit byte string.
 * The password is UTF-8 from the caller. Java's PBEKeySpec is not used, because
 * its character-to-byte conversion is not the same on every provider.
 */
internal fun pbkdf2HmacSha256(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    dkLen: Int,
): ByteArray {
    require(iterations > 0)
    require(dkLen in 1..32 * 255)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(password, "HmacSHA256"))
    val hLen = mac.macLength
    val blocks = (dkLen + hLen - 1) / hLen
    val out = ByteArray(blocks * hLen)
    var offset = 0
    for (block in 1..blocks) {
        mac.reset()
        mac.update(salt)
        mac.update(
            byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte(),
            ),
        )
        var u = mac.doFinal()
        val t = u.copyOf()
        for (round in 2..iterations) {
            u = mac.doFinal(u)
            for (i in t.indices) {
                t[i] = ((t[i].toInt() xor u[i].toInt()) and 0xFF).toByte()
            }
        }
        t.copyInto(out, offset)
        offset += hLen
        u.fill(0)
        t.fill(0)
    }
    return out.copyOf(dkLen).also { out.fill(0) }
}

/** HKDF-SHA256, RFC 5869. */
internal fun hkdfSha256(
    ikm: ByteArray,
    salt: ByteArray?,
    info: ByteArray,
    length: Int,
): ByteArray {
    val hashLen = 32
    require(length in 1..(255 * hashLen))
    val mac = Mac.getInstance("HmacSHA256")
    val realSalt = if (salt == null || salt.isEmpty()) ByteArray(hashLen) else salt
    mac.init(SecretKeySpec(realSalt, "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    try {
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val blocks = (length + hashLen - 1) / hashLen
        val out = ByteArray(blocks * hashLen)
        var previous = ByteArray(0)
        var offset = 0
        for (i in 1..blocks) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(i.toByte())
            previous = mac.doFinal()
            previous.copyInto(out, offset)
            offset += previous.size
        }
        return out.copyOf(length).also { out.fill(0) }
    } finally {
        prk.fill(0)
    }
}
