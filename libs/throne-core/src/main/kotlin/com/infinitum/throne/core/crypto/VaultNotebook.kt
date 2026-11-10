package com.infinitum.throne.core.crypto

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class VaultContact(val name: String, val rawPublic: ByteArray)

data class VaultNote(val id: String, val createdEpochMs: Long, val text: String)

data class SavedCipher(val id: String, val createdEpochMs: Long, val armor: String)

data class VaultNotebook(
    val contacts: List<VaultContact> = emptyList(),
    val notes: List<VaultNote> = emptyList(),
    val saved: List<SavedCipher> = emptyList(),
) {
    fun encode(): ByteArray {
        require(contacts.size <= 500 && notes.size <= 500 && saved.size <= 500)
        val out = ByteArrayOutputStream()
        out.write("TNV1".encodeToByteArray())
        writeU16(out, contacts.size)
        for (contact in contacts) {
            require(contact.name.isNotBlank() && contact.name.length <= 80)
            require(!contact.name.contains('\t') && !contact.name.contains('\n'))
            require(contact.rawPublic.size == 32)
            writeString16(out, contact.name)
            out.write(contact.rawPublic)
        }
        writeU16(out, notes.size)
        for (note in notes) {
            require(note.id.length in 1..80)
            require(note.text.encodeToByteArray().size <= VaultMessages.MAX_PLAINTEXT_BYTES)
            writeString16(out, note.id)
            writeI64(out, note.createdEpochMs)
            writeString32(out, note.text)
        }
        writeU16(out, saved.size)
        for (item in saved) {
            require(item.id.length in 1..80)
            require(item.armor.length <= VaultMessages.MAX_INPUT_CHARS)
            writeString16(out, item.id)
            writeI64(out, item.createdEpochMs)
            writeString32(out, item.armor)
        }
        return out.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): VaultNotebook {
            try {
                val buffer = ByteBuffer.wrap(bytes)
                val magic = ByteArray(4)
                buffer.get(magic)
                if (!magic.contentEquals("TNV1".encodeToByteArray())) throw NotebookFormatException()
                val contacts = List(readU16(buffer)) {
                    val name = readString16(buffer)
                    val raw = ByteArray(32)
                    buffer.get(raw)
                    VaultContact(name, raw)
                }
                val notes = List(readU16(buffer)) {
                    VaultNote(readString16(buffer), readI64(buffer), readString32(buffer))
                }
                val saved = List(readU16(buffer)) {
                    SavedCipher(readString16(buffer), readI64(buffer), readString32(buffer))
                }
                if (buffer.hasRemaining()) throw NotebookFormatException()
                return VaultNotebook(contacts, notes, saved)
            } catch (_: NotebookFormatException) {
                throw NotebookFormatException()
            } catch (_: Exception) {
                throw NotebookFormatException()
            }
        }

        private fun readU16(buffer: ByteBuffer): Int {
            val value = buffer.short.toInt() and 0xFFFF
            if (value > 500) throw NotebookFormatException()
            return value
        }

        private fun readI64(buffer: ByteBuffer): Long = buffer.long

        private fun readString16(buffer: ByteBuffer): String {
            val length = buffer.short.toInt() and 0xFFFF
            if (length > 4096) throw NotebookFormatException()
            val raw = ByteArray(length)
            buffer.get(raw)
            return decodeUtf8(raw)
        }

        private fun readString32(buffer: ByteBuffer): String {
            val length = buffer.int
            if (length < 0 || length > VaultMessages.MAX_INPUT_CHARS) throw NotebookFormatException()
            val raw = ByteArray(length)
            buffer.get(raw)
            return decodeUtf8(raw)
        }

        private fun decodeUtf8(raw: ByteArray): String {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(raw)).toString()
        }
    }
}

class NotebookFormatException : Exception("The notebook could not be read.")

private fun writeU16(out: ByteArrayOutputStream, value: Int) {
    out.write(value ushr 8)
    out.write(value)
}

private fun writeI64(out: ByteArrayOutputStream, value: Long) {
    for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) {
        out.write(((value ushr shift) and 0xFF).toInt())
    }
}

private fun writeString16(out: ByteArrayOutputStream, value: String) {
    val raw = value.encodeToByteArray()
    require(raw.size <= 4096)
    writeU16(out, raw.size)
    out.write(raw)
}

private fun writeString32(out: ByteArrayOutputStream, value: String) {
    val raw = value.encodeToByteArray()
    require(raw.size <= VaultMessages.MAX_INPUT_CHARS)
    out.write((raw.size ushr 24) and 0xFF)
    out.write((raw.size ushr 16) and 0xFF)
    out.write((raw.size ushr 8) and 0xFF)
    out.write(raw.size and 0xFF)
    out.write(raw)
}
