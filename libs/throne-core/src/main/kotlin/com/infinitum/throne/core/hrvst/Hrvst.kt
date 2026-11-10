package com.infinitum.throne.core.hrvst

import com.infinitum.throne.core.crypto.sha256Hex
import com.infinitum.throne.core.json.JsonException
import com.infinitum.throne.core.json.JsonValue

/**
 * On-device Harvest Packet, Firmament provenance, and Personal Silo.
 * The private infinitum-nexus package `@infinitum/hrvst` was not readable from
 * this environment. These field names are the phone's contract until that
 * package can be diffed. Sharing is refused unless this packet has an explicit
 * grant for the named recipient.
 */
object Hrvst {
    const val PACKET_SCHEMA = "infinitum.hrvst.packet/v1"
    const val PROVENANCE_SCHEMA = "infinitum.firmament.provenance/v1"
    const val SILO_SCHEMA = "infinitum.hrvst.silo/v1"
    const val PRODUCER = "com.infinitum.hrvst.cam"
    const val EVENT = "camera.capture"
    const val MEDIA_TYPE = "image/jpeg"
    const val MIN_RETENTION_DAYS = 1
    const val MAX_RETENTION_DAYS = 90
    const val DEFAULT_RETENTION_DAYS = 30
    const val MAX_JPEG_BYTES = 4_000_000
    const val DAY_MS = 86_400_000L
    val ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    val SHA256 = Regex("^[0-9a-f]{64}$")
}

data class FirmamentProvenance(
    val producer: String,
    val producerVersion: String,
    val deviceLabel: String,
    val event: String,
    val contentSha256: String,
    val previousContentSha256: String?,
)

data class ShareGrant(
    val recipient: String,
    val grantedAtEpochMs: Long,
    val includeImage: Boolean,
)

data class HarvestPacket(
    val id: String,
    val mediaType: String,
    val byteCount: Int,
    val contentSha256: String,
    val capturedAtEpochMs: Long,
    val provenance: FirmamentProvenance,
    val grants: List<ShareGrant>,
)

data class SiloMeta(
    val id: String,
    val ownerLabel: String,
    val retentionDays: Int,
)

sealed class SharePermit {
    data class Allowed(val manifest: String, val includeImage: Boolean) : SharePermit()
    data class Refused(val reason: String) : SharePermit()
}

object HrvstRules {
    fun cleanOwner(raw: String): String? = cleanLabel(raw, 80)

    fun cleanRecipient(raw: String): String? {
        val label = cleanLabel(raw, 80) ?: return null
        if (label == "*") return null
        return label
    }

    fun cleanDevice(raw: String): String = cleanLabel(raw, 40) ?: "android"

    fun cleanVersion(raw: String): String = cleanLabel(raw, 40) ?: "0"

    fun retentionOrNull(days: Int): Int? = days.takeIf { it in Hrvst.MIN_RETENTION_DAYS..Hrvst.MAX_RETENTION_DAYS }

    fun previousHash(packets: List<HarvestPacket>): String? =
        packets.maxWithOrNull(compareBy<HarvestPacket>({ it.capturedAtEpochMs }, { it.id }))?.contentSha256

    fun packet(
        id: String,
        jpeg: ByteArray,
        capturedAtEpochMs: Long,
        producerVersion: String,
        deviceLabel: String,
        previousContentSha256: String?,
    ): HarvestPacket? {
        if (!id.matches(Hrvst.ID)) return null
        if (jpeg.isEmpty() || jpeg.size > Hrvst.MAX_JPEG_BYTES) return null
        if (previousContentSha256 != null && !previousContentSha256.matches(Hrvst.SHA256)) return null
        val hash = sha256Hex(jpeg)
        return HarvestPacket(
            id = id,
            mediaType = Hrvst.MEDIA_TYPE,
            byteCount = jpeg.size,
            contentSha256 = hash,
            capturedAtEpochMs = capturedAtEpochMs,
            provenance = FirmamentProvenance(
                producer = Hrvst.PRODUCER,
                producerVersion = cleanVersion(producerVersion),
                deviceLabel = cleanDevice(deviceLabel),
                event = Hrvst.EVENT,
                contentSha256 = hash,
                previousContentSha256 = previousContentSha256,
            ),
            grants = emptyList(),
        )
    }

    fun grant(packet: HarvestPacket, recipient: String, includeImage: Boolean, now: Long): HarvestPacket? {
        val name = cleanRecipient(recipient) ?: return null
        val grant = ShareGrant(name, now, includeImage)
        return packet.copy(grants = packet.grants.filterNot { it.recipient == name } + grant)
    }

    fun revokeAll(packet: HarvestPacket): HarvestPacket = packet.copy(grants = emptyList())

    fun permit(packet: HarvestPacket, recipient: String): SharePermit {
        val name = cleanRecipient(recipient) ?: return SharePermit.Refused("Name the recipient for this packet.")
        val grant = packet.grants.find { it.recipient == name }
            ?: return SharePermit.Refused("This packet has no permission for that recipient.")
        return SharePermit.Allowed(manifest(packet, grant), grant.includeImage)
    }

    fun expired(capturedAtEpochMs: Long, retentionDays: Int, now: Long): Boolean {
        if (retentionDays !in Hrvst.MIN_RETENTION_DAYS..Hrvst.MAX_RETENTION_DAYS) return true
        if (capturedAtEpochMs > now + 120_000L) return true
        val end = capturedAtEpochMs + retentionDays.toLong() * Hrvst.DAY_MS
        return now >= end
    }

    fun retain(packets: List<HarvestPacket>, retentionDays: Int, now: Long): List<HarvestPacket> =
        packets.filterNot { expired(it.capturedAtEpochMs, retentionDays, now) }

    fun manifest(packet: HarvestPacket, grant: ShareGrant): String = buildString {
        appendLine("THRONE][OS Harvest packet")
        appendLine("schema: ${Hrvst.PACKET_SCHEMA}")
        appendLine("provenance: ${Hrvst.PROVENANCE_SCHEMA}")
        appendLine("id: ${packet.id}")
        appendLine("sha256: ${packet.contentSha256}")
        appendLine("capturedAtEpochMs: ${packet.capturedAtEpochMs}")
        appendLine("producer: ${packet.provenance.producer}")
        appendLine("event: ${packet.provenance.event}")
        appendLine("device: ${packet.provenance.deviceLabel}")
        appendLine("previous: ${packet.provenance.previousContentSha256 ?: "none"}")
        appendLine("recipient: ${grant.recipient}")
        appendLine("image: ${if (grant.includeImage) "included" else "not included"}")
    }.trimEnd()
}

private fun cleanLabel(raw: String, max: Int): String? {
    if (raw.any { it.isISOControl() }) return null
    val label = raw.trim().replace(Regex("\\s+"), " ")
    if (label.length !in 1..max) return null
    return label
}

object SiloCodec {
    private const val META_VERSION = 1L
    private const val PACKET_VERSION = 1L

    fun encodeMeta(meta: SiloMeta): String = JsonValue.obj(
        "v" to JsonValue.num(META_VERSION),
        "schema" to JsonValue.str(Hrvst.SILO_SCHEMA),
        "id" to JsonValue.str(meta.id),
        "ownerLabel" to JsonValue.str(meta.ownerLabel),
        "retentionDays" to JsonValue.num(meta.retentionDays.toLong()),
    ).render()

    fun decodeMeta(text: String): SiloMeta? {
        val root = try {
            JsonValue.parse(text)
        } catch (_: JsonException) {
            return null
        }
        if (root.long("v") != META_VERSION || root.str("schema") != Hrvst.SILO_SCHEMA) return null
        val id = root.str("id") ?: return null
        if (!id.matches(Hrvst.ID)) return null
        val owner = root.str("ownerLabel")?.let(HrvstRules::cleanOwner) ?: return null
        val days = root.long("retentionDays")?.toInt() ?: return null
        if (HrvstRules.retentionOrNull(days) == null) return null
        return SiloMeta(id, owner, days)
    }

    fun encodePacket(packet: HarvestPacket): String = JsonValue.obj(
        "v" to JsonValue.num(PACKET_VERSION),
        "schema" to JsonValue.str(Hrvst.PACKET_SCHEMA),
        "id" to JsonValue.str(packet.id),
        "mediaType" to JsonValue.str(packet.mediaType),
        "byteCount" to JsonValue.num(packet.byteCount.toLong()),
        "contentSha256" to JsonValue.str(packet.contentSha256),
        "capturedAtEpochMs" to JsonValue.num(packet.capturedAtEpochMs),
        "provenance" to JsonValue.obj(
            "schema" to JsonValue.str(Hrvst.PROVENANCE_SCHEMA),
            "producer" to JsonValue.str(packet.provenance.producer),
            "producerVersion" to JsonValue.str(packet.provenance.producerVersion),
            "deviceLabel" to JsonValue.str(packet.provenance.deviceLabel),
            "event" to JsonValue.str(packet.provenance.event),
            "contentSha256" to JsonValue.str(packet.provenance.contentSha256),
            "previousContentSha256" to (
                packet.provenance.previousContentSha256?.let { JsonValue.str(it) } ?: JsonValue.Null
                ),
        ),
        "grants" to JsonValue.arr(
            packet.grants.map { grant ->
                JsonValue.obj(
                    "recipient" to JsonValue.str(grant.recipient),
                    "grantedAtEpochMs" to JsonValue.num(grant.grantedAtEpochMs),
                    "includeImage" to JsonValue.bool(grant.includeImage),
                )
            },
        ),
    ).render()

    fun decodePacket(text: String): HarvestPacket? {
        val root = try {
            JsonValue.parse(text)
        } catch (_: JsonException) {
            return null
        }
        if (root.long("v") != PACKET_VERSION || root.str("schema") != Hrvst.PACKET_SCHEMA) return null
        val id = root.str("id") ?: return null
        if (!id.matches(Hrvst.ID) || root.str("mediaType") != Hrvst.MEDIA_TYPE) return null
        val hash = root.str("contentSha256") ?: return null
        if (!hash.matches(Hrvst.SHA256)) return null
        val bytes = root.long("byteCount") ?: return null
        if (bytes !in 1..Hrvst.MAX_JPEG_BYTES.toLong()) return null
        val at = root.long("capturedAtEpochMs") ?: return null
        val provenance = root.field("provenance") ?: return null
        if (provenance.str("schema") != Hrvst.PROVENANCE_SCHEMA) return null
        if (provenance.str("producer") != Hrvst.PRODUCER || provenance.str("event") != Hrvst.EVENT) return null
        if (provenance.str("contentSha256") != hash) return null
        val previous = when (val value = provenance.field("previousContentSha256")) {
            null, JsonValue.Null -> null
            is JsonValue.Str -> value.value
            else -> return null
        }
        if (previous != null && !previous.matches(Hrvst.SHA256)) return null
        val grantsValue = root.field("grants") as? JsonValue.Arr ?: return null
        val grants = ArrayList<ShareGrant>()
        for (item in grantsValue.items) {
            val recipient = item.str("recipient")?.let(HrvstRules::cleanRecipient) ?: return null
            val grantedAt = item.long("grantedAtEpochMs") ?: return null
            val includeImage = item.bool("includeImage") ?: return null
            if (grants.any { it.recipient == recipient }) return null
            grants += ShareGrant(recipient, grantedAt, includeImage)
        }
        return HarvestPacket(
            id = id,
            mediaType = Hrvst.MEDIA_TYPE,
            byteCount = bytes.toInt(),
            contentSha256 = hash,
            capturedAtEpochMs = at,
            provenance = FirmamentProvenance(
                producer = Hrvst.PRODUCER,
                producerVersion = provenance.str("producerVersion")?.let(HrvstRules::cleanVersion) ?: return null,
                deviceLabel = provenance.str("deviceLabel")?.let(HrvstRules::cleanDevice) ?: return null,
                event = Hrvst.EVENT,
                contentSha256 = hash,
                previousContentSha256 = previous,
            ),
            grants = grants,
        )
    }
}
