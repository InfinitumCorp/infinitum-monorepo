package com.infinitum.throne.core.eidetic

import com.infinitum.throne.core.crypto.sha256Hex
import com.infinitum.throne.core.json.JsonException
import com.infinitum.throne.core.json.JsonValue

/**
 * Consent gate for the Eidetic camera. A frame can be taken only for a person
 * whose written consent is on file, and only for a purpose named in that consent.
 * There is no open-set identification mode.
 */
object ConsentStatement {
    const val VERSION = "eidetic-written-consent-v1"
    const val ACKNOWLEDGMENT = "I acknowledge written consent"

    val TEXT: String = """
        Eidetic captures a camera frame of one enrolled person and sends it to the eidetic desk for a human reviewer. The frame is biometric personal data.

        The person on camera must already have given written consent. This screen records that the written consent is on file. It does not replace that writing.

        Russia Federal Law No. 152-FZ requires written consent before biometric personal data is processed. GDPR Article 9 requires explicit consent before biometric data is used for unique identification, unless another listed lawful basis applies. The Illinois Biometric Information Privacy Act requires a written release before a biometric identifier is collected.

        This app does not identify bystanders. A frame is sent only for the enrolled person named on this phone, only for a purpose written on that consent, and only after the desk's policy check allows it. A person reviews the result. This app does not unlock a door, send an alert, or take any other action from a match.

        The phone keeps the enrollment record and the queue receipt under an Android Keystore key. The frame itself is not written to disk. Receipts and enrollments are deleted when the retention window ends, or sooner from Delete my data. Remote deletion is confirmed only when the desk says it succeeded.

        A purpose that is not on the written consent cannot be used. A new purpose needs new written consent.
    """.trimIndent()

    fun sha256(): String = sha256Hex(TEXT.encodeToByteArray())
}

object EideticLimits {
    const val MIN_RETENTION_DAYS = 1
    const val MAX_RETENTION_DAYS = 30
    const val DEFAULT_RETENTION_DAYS = 7
    const val MAX_PURPOSES = 8
    const val MAX_JPEG_BYTES = 4_000_000
    const val DAY_MS = 86_400_000L
    val ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}

sealed class Gate {
    data object Open : Gate()
    data class Closed(val reason: String) : Gate()
}

data class EnrollmentDraft(
    val legalName: String,
    val purposes: List<String>,
    val retentionDays: Int,
    val writtenConsentOnFile: Boolean,
    val acknowledgment: String,
)

data class WrittenConsent(
    val statementVersion: String,
    val statementSha256: String,
    val acknowledgedAtEpochMs: Long,
    val retentionDays: Int,
    val writtenConsentOnFile: Boolean,
    val acknowledgment: String,
    val purposes: List<String>,
)

data class EnrolledSubject(
    val id: String,
    val legalName: String,
    val consent: WrittenConsent,
)

data class LocalReceipt(
    val queueId: String,
    val subjectId: String,
    val purpose: String,
    val imageSha256: String,
    val submittedAtEpochMs: Long,
    val status: String,
)

class EnrollmentBook(subjects: List<EnrolledSubject> = emptyList()) {
    private val lock = Any()
    private val items = LinkedHashMap<String, EnrolledSubject>()

    init {
        for (subject in subjects) {
            if (subject.id.matches(EideticLimits.ID)) items[subject.id] = subject
        }
    }

    fun snapshot(): List<EnrolledSubject> = synchronized(lock) {
        items.values.sortedWith(compareBy({ it.legalName.lowercase() }, { it.id }))
    }

    fun get(id: String): EnrolledSubject? = synchronized(lock) { items[id] }

    fun enroll(draft: EnrollmentDraft, now: Long, newId: String): Gate = synchronized(lock) {
        val name = cleanPersonName(draft.legalName)
            ?: return Gate.Closed("Enter the person's name.")
        if (!draft.writtenConsentOnFile) {
            return Gate.Closed("Confirm that written consent for this person is on file.")
        }
        if (draft.acknowledgment.trim() != ConsentStatement.ACKNOWLEDGMENT) {
            return Gate.Closed("Type the acknowledgment exactly.")
        }
        if (draft.retentionDays !in EideticLimits.MIN_RETENTION_DAYS..EideticLimits.MAX_RETENTION_DAYS) {
            return Gate.Closed("Retention has to be from 1 to 30 days.")
        }
        if (!newId.matches(EideticLimits.ID)) return Gate.Closed("The enrollment id is not valid.")
        val purposes = ArrayList<String>()
        for (raw in draft.purposes) {
            val purpose = cleanPurpose(raw) ?: return Gate.Closed("One purpose is not valid.")
            if (purpose !in purposes) purposes += purpose
        }
        if (purposes.isEmpty() || purposes.size > EideticLimits.MAX_PURPOSES) {
            return Gate.Closed("Enter between 1 and 8 purposes.")
        }
        items[newId] = EnrolledSubject(
            id = newId,
            legalName = name,
            consent = WrittenConsent(
                statementVersion = ConsentStatement.VERSION,
                statementSha256 = ConsentStatement.sha256(),
                acknowledgedAtEpochMs = now,
                retentionDays = draft.retentionDays,
                writtenConsentOnFile = true,
                acknowledgment = ConsentStatement.ACKNOWLEDGMENT,
                purposes = purposes,
            ),
        )
        Gate.Open
    }

    fun mayOpenCamera(id: String, now: Long): Gate = synchronized(lock) {
        consentGate(items[id], now)
    }

    fun maySubmit(id: String, purpose: String, frameIsSubject: Boolean, now: Long): Gate = synchronized(lock) {
        val subject = items[id] ?: return Gate.Closed("Choose an enrolled person.")
        val consent = consentGate(subject, now)
        if (consent is Gate.Closed) return consent
        if (!frameIsSubject) {
            return Gate.Closed("Confirm that the person in frame is the enrolled subject. This app does not identify anyone else.")
        }
        val declared = cleanPurpose(purpose) ?: return Gate.Closed("Choose a purpose from the written consent.")
        if (declared !in subject.consent.purposes) {
            return Gate.Closed("That purpose is not on the written consent.")
        }
        Gate.Open
    }

    fun sweep(now: Long): List<String> = synchronized(lock) {
        val removed = items.values.filter { isPastRetention(it.consent, now) }.map { it.id }
        removed.forEach { items.remove(it) }
        removed
    }

    fun delete(id: String) = synchronized(lock) { items.remove(id) }

    fun deleteAll() = synchronized(lock) { items.clear() }

    private fun consentGate(subject: EnrolledSubject?, now: Long): Gate {
        if (subject == null) return Gate.Closed("Choose an enrolled person.")
        val consent = subject.consent
        if (!consent.writtenConsentOnFile) {
            return Gate.Closed("Written consent is not on file for this person.")
        }
        if (consent.acknowledgment != ConsentStatement.ACKNOWLEDGMENT) {
            return Gate.Closed("The acknowledgment on file does not match.")
        }
        if (consent.statementVersion != ConsentStatement.VERSION || consent.statementSha256 != ConsentStatement.sha256()) {
            return Gate.Closed("The consent text has changed. Enroll this person again.")
        }
        if (consent.retentionDays !in EideticLimits.MIN_RETENTION_DAYS..EideticLimits.MAX_RETENTION_DAYS) {
            return Gate.Closed("The retention window on file is not valid.")
        }
        if (consent.purposes.isEmpty() || consent.purposes.any { cleanPurpose(it) == null }) {
            return Gate.Closed("The purposes on file are not valid.")
        }
        if (now < consent.acknowledgedAtEpochMs) {
            return Gate.Closed("The consent time is not valid.")
        }
        val end = consent.acknowledgedAtEpochMs + consent.retentionDays.toLong() * EideticLimits.DAY_MS
        if (now >= end) return Gate.Closed("The written-consent window for this person has ended.")
        return Gate.Open
    }

    private fun isPastRetention(consent: WrittenConsent, now: Long): Boolean {
        if (consent.retentionDays !in EideticLimits.MIN_RETENTION_DAYS..EideticLimits.MAX_RETENTION_DAYS) return true
        if (now < consent.acknowledgedAtEpochMs) return false
        val end = consent.acknowledgedAtEpochMs + consent.retentionDays.toLong() * EideticLimits.DAY_MS
        return now >= end
    }
}

fun cleanPersonName(raw: String): String? {
    if (raw.any { it.isISOControl() }) return null
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.length !in 1..120) return null
    return name
}

fun cleanPurpose(raw: String): String? {
    if (raw.any { it.isISOControl() }) return null
    val purpose = raw.trim().replace(Regex("\\s+"), " ")
    if (purpose.length !in 1..80) return null
    return purpose
}

object SubjectCodec {
    private const val VERSION = 1L

    fun encode(subject: EnrolledSubject): String = JsonValue.obj(
        "v" to JsonValue.num(VERSION),
        "id" to JsonValue.str(subject.id),
        "legalName" to JsonValue.str(subject.legalName),
        "consent" to JsonValue.obj(
            "statementVersion" to JsonValue.str(subject.consent.statementVersion),
            "statementSha256" to JsonValue.str(subject.consent.statementSha256),
            "acknowledgedAtEpochMs" to JsonValue.num(subject.consent.acknowledgedAtEpochMs),
            "retentionDays" to JsonValue.num(subject.consent.retentionDays.toLong()),
            "writtenConsentOnFile" to JsonValue.bool(subject.consent.writtenConsentOnFile),
            "acknowledgment" to JsonValue.str(subject.consent.acknowledgment),
            "purposes" to JsonValue.arr(subject.consent.purposes.map { JsonValue.str(it) }),
        ),
    ).render()

    fun decode(text: String): EnrolledSubject? {
        val root = try {
            JsonValue.parse(text)
        } catch (_: JsonException) {
            return null
        }
        if (root.long("v") != VERSION) return null
        val id = root.str("id") ?: return null
        if (!id.matches(EideticLimits.ID)) return null
        val name = root.str("legalName")?.let(::cleanPersonName) ?: return null
        val consent = root.field("consent") ?: return null
        val purposes = consent.strings("purposes") ?: return null
        if (purposes.any { cleanPurpose(it) == null }) return null
        val days = consent.long("retentionDays") ?: return null
        if (days !in EideticLimits.MIN_RETENTION_DAYS.toLong()..EideticLimits.MAX_RETENTION_DAYS.toLong()) return null
        val at = consent.long("acknowledgedAtEpochMs") ?: return null
        val onFile = consent.bool("writtenConsentOnFile") ?: return null
        return EnrolledSubject(
            id = id,
            legalName = name,
            consent = WrittenConsent(
                statementVersion = consent.str("statementVersion") ?: return null,
                statementSha256 = consent.str("statementSha256") ?: return null,
                acknowledgedAtEpochMs = at,
                retentionDays = days.toInt(),
                writtenConsentOnFile = onFile,
                acknowledgment = consent.str("acknowledgment") ?: return null,
                purposes = purposes,
            ),
        )
    }
}

object ReceiptCodec {
    private const val VERSION = 1L

    fun encode(receipt: LocalReceipt): String = JsonValue.obj(
        "v" to JsonValue.num(VERSION),
        "queueId" to JsonValue.str(receipt.queueId),
        "subjectId" to JsonValue.str(receipt.subjectId),
        "purpose" to JsonValue.str(receipt.purpose),
        "imageSha256" to JsonValue.str(receipt.imageSha256),
        "submittedAtEpochMs" to JsonValue.num(receipt.submittedAtEpochMs),
        "status" to JsonValue.str(receipt.status),
    ).render()

    fun decode(text: String): LocalReceipt? {
        val root = try {
            JsonValue.parse(text)
        } catch (_: JsonException) {
            return null
        }
        if (root.long("v") != VERSION) return null
        val queueId = root.str("queueId") ?: return null
        val subjectId = root.str("subjectId") ?: return null
        if (!queueId.matches(SAFE_TOKEN) || !subjectId.matches(EideticLimits.ID)) return null
        val purpose = root.str("purpose")?.let(::cleanPurpose) ?: return null
        val hash = root.str("imageSha256") ?: return null
        if (!hash.matches(SHA256)) return null
        val at = root.long("submittedAtEpochMs") ?: return null
        val status = root.str("status")?.let(::cleanStatus) ?: return null
        return LocalReceipt(queueId, subjectId, purpose, hash, at, status)
    }
}

internal val SAFE_TOKEN = Regex("^[A-Za-z0-9_-]{1,80}$")
internal val SHA256 = Regex("^[0-9a-f]{64}$")

internal fun cleanStatus(raw: String): String? {
    val status = raw.trim().lowercase()
    if (!status.matches(Regex("^[a-z0-9-]{1,40}$"))) return null
    return status
}
