package com.infinitum.hrvst.cam

import android.content.Context
import com.infinitum.throne.core.hrvst.HarvestPacket
import com.infinitum.throne.core.hrvst.Hrvst
import com.infinitum.throne.core.hrvst.SiloCodec
import com.infinitum.throne.core.hrvst.SiloMeta
import java.io.File
import java.util.UUID

internal class HrvstStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "hrvst")
    private val alias = "hrvst_cam_aes"

    init {
        KeystoreAes.key(alias)
        root.mkdirs()
    }

    fun load(): SiloSnapshot = synchronized(this) {
        val meta = readMeta()
        val packets = ArrayList<HarvestPacket>()
        var unreadable = 0
        val packetDir = File(root, "packets")
        for (file in packetDir.listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".bin")) continue
            val id = file.name.removeSuffix(".bin")
            val packet = readPacket(file, id)
            if (packet == null) unreadable++ else packets += packet
        }
        val ids = packets.map { it.id }.toSet()
        for (file in File(root, "blobs").listFiles().orEmpty()) {
            val id = file.name.removeSuffix(".bin")
            if (id !in ids) file.delete()
        }
        SiloSnapshot(meta, packets.sortedByDescending { it.capturedAtEpochMs }, unreadable)
    }

    fun saveMeta(meta: SiloMeta) = synchronized(this) {
        val plain = SiloCodec.encodeMeta(meta).encodeToByteArray()
        try {
            writeAtomic(File(root, "meta.bin"), KeystoreAes.seal(alias, plain, META_AAD))
        } finally {
            plain.fill(0)
        }
    }

    fun saveNew(packet: HarvestPacket, jpeg: ByteArray) = synchronized(this) {
        val blob = KeystoreAes.seal(alias, jpeg, blobAad(packet.id))
        val plain = SiloCodec.encodePacket(packet).encodeToByteArray()
        try {
            writeAtomic(File(root, "blobs/${packet.id}.bin"), blob)
            writeAtomic(File(root, "packets/${packet.id}.bin"), KeystoreAes.seal(alias, plain, packetAad(packet.id)))
        } finally {
            plain.fill(0)
        }
    }

    fun savePacket(packet: HarvestPacket) = synchronized(this) {
        val plain = SiloCodec.encodePacket(packet).encodeToByteArray()
        try {
            writeAtomic(File(root, "packets/${packet.id}.bin"), KeystoreAes.seal(alias, plain, packetAad(packet.id)))
        } finally {
            plain.fill(0)
        }
    }

    fun readBlob(id: String): ByteArray? = synchronized(this) {
        if (!id.matches(Hrvst.ID)) return null
        val file = File(root, "blobs/$id.bin")
        if (!file.isFile) return null
        return try {
            KeystoreAes.open(alias, file.readBytes(), blobAad(id))
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: String) {
        synchronized(this) {
            if (!id.matches(Hrvst.ID)) return@synchronized
            File(root, "packets/$id.bin").delete()
            File(root, "blobs/$id.bin").delete()
        }
    }

    fun wipe() = synchronized(this) {
        root.deleteRecursively()
        root.mkdirs()
    }

    fun ensureMeta(owner: String, retentionDays: Int): SiloMeta = synchronized(this) {
        val existing = readMeta()
        val meta = SiloMeta(existing?.id ?: UUID.randomUUID().toString(), owner, retentionDays)
        saveMeta(meta)
        meta
    }

    private fun readMeta(): SiloMeta? {
        val file = File(root, "meta.bin")
        if (!file.isFile) return null
        return try {
            val plain = KeystoreAes.open(alias, file.readBytes(), META_AAD)
            try {
                SiloCodec.decodeMeta(plain.toString(Charsets.UTF_8))
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readPacket(file: File, id: String): HarvestPacket? {
        if (!id.matches(Hrvst.ID)) return null
        return try {
            val plain = KeystoreAes.open(alias, file.readBytes(), packetAad(id))
            try {
                SiloCodec.decodePacket(plain.toString(Charsets.UTF_8))?.takeIf { it.id == id }
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun packetAad(id: String) = "THRONE][OS hrvst packet v1:$id".encodeToByteArray()

    private fun blobAad(id: String) = "THRONE][OS hrvst blob v1:$id".encodeToByteArray()

    companion object {
        private val META_AAD = "THRONE][OS hrvst silo v1".encodeToByteArray()
    }
}

internal data class SiloSnapshot(
    val meta: SiloMeta?,
    val packets: List<HarvestPacket>,
    val unreadable: Int,
)
