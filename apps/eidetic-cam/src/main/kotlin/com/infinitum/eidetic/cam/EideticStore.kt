package com.infinitum.eidetic.cam

import android.content.Context
import com.infinitum.throne.core.eidetic.EideticLimits
import com.infinitum.throne.core.eidetic.EnrollmentBook
import com.infinitum.throne.core.eidetic.EnrolledSubject
import com.infinitum.throne.core.eidetic.LocalReceipt
import com.infinitum.throne.core.eidetic.ReceiptCodec
import com.infinitum.throne.core.eidetic.SubjectCodec
import java.io.File

internal class EideticStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "eidetic")
    private val prefs = context.getSharedPreferences("eidetic-desk", Context.MODE_PRIVATE)
    private val alias = "eidetic_cam_aes"

    init {
        KeystoreAes.key(alias)
        root.mkdirs()
    }

    fun deskUrl(): String = prefs.getString("url", DEFAULT_DESK) ?: DEFAULT_DESK

    fun saveDesk(url: String) {
        prefs.edit().putString("url", url).apply()
    }

    fun load(): Loaded = synchronized(this) {
        val subjects = ArrayList<EnrolledSubject>()
        var unreadable = 0
        for (file in File(root, "subjects").listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".bin")) continue
            val id = file.name.removeSuffix(".bin")
            val subject = readSubject(file, id)
            if (subject == null) unreadable++ else subjects += subject
        }
        Loaded(EnrollmentBook(subjects), unreadable)
    }

    fun save(book: EnrollmentBook) = synchronized(this) {
        val dir = File(root, "subjects")
        dir.mkdirs()
        val keep = HashSet<String>()
        for (subject in book.snapshot()) {
            keep += subject.id
            val plain = SubjectCodec.encode(subject).encodeToByteArray()
            try {
                writeAtomic(File(dir, "${subject.id}.bin"), KeystoreAes.seal(alias, plain, subjectAad(subject.id)))
            } finally {
                plain.fill(0)
            }
        }
        for (file in dir.listFiles().orEmpty()) {
            if (file.name.removeSuffix(".bin") !in keep) file.delete()
        }
    }

    fun loadReceipts(): List<LocalReceipt> = synchronized(this) {
        val out = ArrayList<LocalReceipt>()
        for (file in File(root, "receipts").listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".bin")) continue
            val id = file.name.removeSuffix(".bin")
            readReceipt(file, id)?.let { out += it }
        }
        out.sortedByDescending { it.submittedAtEpochMs }
    }

    fun saveReceipts(receipts: List<LocalReceipt>) = synchronized(this) {
        val dir = File(root, "receipts")
        dir.mkdirs()
        val keep = HashSet<String>()
        for (receipt in receipts) {
            if (!receipt.queueId.matches(TOKEN)) continue
            keep += receipt.queueId
            val plain = ReceiptCodec.encode(receipt).encodeToByteArray()
            try {
                writeAtomic(File(dir, "${receipt.queueId}.bin"), KeystoreAes.seal(alias, plain, receiptAad(receipt.queueId)))
            } finally {
                plain.fill(0)
            }
        }
        for (file in dir.listFiles().orEmpty()) {
            if (file.name.removeSuffix(".bin") !in keep) file.delete()
        }
    }

    fun wipe() = synchronized(this) {
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun readSubject(file: File, id: String): EnrolledSubject? {
        if (!id.matches(EideticLimits.ID)) return null
        return try {
            val plain = KeystoreAes.open(alias, file.readBytes(), subjectAad(id))
            try {
                SubjectCodec.decode(plain.toString(Charsets.UTF_8))?.takeIf { it.id == id }
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readReceipt(file: File, id: String): LocalReceipt? {
        if (!id.matches(TOKEN)) return null
        return try {
            val plain = KeystoreAes.open(alias, file.readBytes(), receiptAad(id))
            try {
                ReceiptCodec.decode(plain.toString(Charsets.UTF_8))?.takeIf { it.queueId == id }
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun subjectAad(id: String) = "THRONE][OS eidetic subject v1:$id".encodeToByteArray()

    private fun receiptAad(id: String) = "THRONE][OS eidetic receipt v1:$id".encodeToByteArray()

    companion object {
        const val DEFAULT_DESK = "http://127.0.0.1:3000"
        private val TOKEN = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}

internal data class Loaded(val book: EnrollmentBook, val unreadable: Int)
