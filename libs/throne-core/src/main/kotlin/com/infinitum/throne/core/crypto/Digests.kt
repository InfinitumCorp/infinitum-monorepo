package com.infinitum.throne.core.crypto

import java.security.MessageDigest

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val alphabet = "0123456789abcdef"
    val out = CharArray(digest.size * 2)
    for (index in digest.indices) {
        val value = digest[index].toInt() and 0xFF
        out[index * 2] = alphabet[value ushr 4]
        out[index * 2 + 1] = alphabet[value and 0x0F]
    }
    return String(out)
}
