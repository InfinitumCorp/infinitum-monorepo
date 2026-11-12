package com.infinitum.throne.core.eidetic

import com.infinitum.throne.core.crypto.sha256Hex
import com.infinitum.throne.core.json.JsonException
import com.infinitum.throne.core.json.JsonValue
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

/**
 * Loopback client for the eidetic desk. The private eidetic repository was not
 * readable here, so these paths are the on-device contract:
 * POST /v1/policy/check, POST /v1/reviewer/queue, GET /v1/reviewer/queue/{id},
 * DELETE /v1/subjects/{id}.
 * A denied or unread policy check does not enqueue a frame. The submission
 * asks for human review and does not name an action.
 */
data class DeskUrl(val scheme: String, val host: String, val port: Int) {
    val origin: String = "$scheme://$host:$port"

    companion object {
        fun parse(raw: String): DeskUrl? {
            val text = raw.trim()
            val uri = try {
                URI(text)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (uri.userInfo != null) return null
            if (!uri.query.isNullOrEmpty() || uri.fragment != null) return null
            val path = uri.path ?: ""
            if (path.isNotEmpty() && path != "/") return null
            val host = when (val rawHost = uri.host?.lowercase()) {
                "localhost", "127.0.0.1" -> rawHost
                else -> return null
            }
            val port = uri.port
            if (port !in 1..65535) return null
            return DeskUrl(scheme, host, port)
        }
    }
}

data class PolicyDecision(val allowed: Boolean, val decisionId: String, val reason: String)

data class RemoteDelete(val confirmed: Boolean)

data class DeskResponse(val code: Int, val body: String)

class DeskException(message: String) : Exception(message)

class EideticDesk(
    val base: DeskUrl,
    private val exchange: (method: String, path: String, body: String?) -> DeskResponse = { method, path, body ->
        httpExchange(base, method, path, body)
    },
) {
    fun checkPolicy(subject: EnrolledSubject, purpose: String, imageSha256: String): PolicyDecision {
        val body = JsonValue.obj(
            "subjectId" to JsonValue.str(subject.id),
            "purpose" to JsonValue.str(purpose),
            "mode" to JsonValue.str(VERIFY_ENROLLED),
            "consentStatement" to JsonValue.str(subject.consent.statementVersion),
            "consentSha256" to JsonValue.str(subject.consent.statementSha256),
            "bystanderIdentification" to JsonValue.bool(false),
            "imageSha256" to JsonValue.str(imageSha256),
        ).render()
        val response = exchange("POST", "/v1/policy/check", body)
        if (response.code !in 200..299) {
            return PolicyDecision(false, "", "The policy check did not allow this purpose.")
        }
        return parseDecision(response.body)
    }

    fun enqueue(
        subject: EnrolledSubject,
        purpose: String,
        decision: PolicyDecision,
        jpeg: ByteArray,
        imageSha256: String,
        now: Long,
    ): LocalReceipt {
        if (!decision.allowed || !decision.decisionId.matches(SAFE_TOKEN)) {
            throw DeskException("The reviewer queue was not called.")
        }
        val body = submissionJson(subject.id, purpose, decision.decisionId, jpeg, imageSha256)
        val response = exchange("POST", "/v1/reviewer/queue", body)
        if (response.code !in 200..299) throw DeskException("The reviewer queue did not accept the frame.")
        return parseReceipt(response.body, subject.id, purpose, imageSha256, now)
            ?: throw DeskException("The reviewer queue response could not be read.")
    }

    fun deleteSubject(subjectId: String): RemoteDelete {
        if (!subjectId.matches(EideticLimits.ID)) return RemoteDelete(false)
        val response = try {
            exchange("DELETE", "/v1/subjects/$subjectId", null)
        } catch (_: Exception) {
            return RemoteDelete(false)
        }
        return RemoteDelete(response.code in 200..299)
    }

    fun queueStatus(queueId: String): String? {
        if (!queueId.matches(SAFE_TOKEN)) return null
        val response = exchange("GET", "/v1/reviewer/queue/$queueId", null)
        if (response.code !in 200..299) return null
        val root = try {
            JsonValue.parse(response.body)
        } catch (_: JsonException) {
            return null
        }
        return root.str("status")?.let(::cleanStatus)
    }
}

const val VERIFY_ENROLLED = "verify-enrolled"
const val HUMAN_REVIEW_ONLY = "human-review-only"

fun submissionJson(
    subjectId: String,
    purpose: String,
    decisionId: String,
    jpeg: ByteArray,
    imageSha256: String,
): String = JsonValue.obj(
    "decisionId" to JsonValue.str(decisionId),
    "subjectId" to JsonValue.str(subjectId),
    "purpose" to JsonValue.str(purpose),
    "mode" to JsonValue.str(VERIFY_ENROLLED),
    "imageSha256" to JsonValue.str(imageSha256),
    "imageJpegBase64" to JsonValue.str(Base64.getEncoder().encodeToString(jpeg)),
    "disposition" to JsonValue.str(HUMAN_REVIEW_ONLY),
).render()

fun parseDecision(body: String): PolicyDecision {
    val root = try {
        JsonValue.parse(body)
    } catch (_: JsonException) {
        return PolicyDecision(false, "", "The policy check could not be read.")
    }
    val decision = root.str("decision")?.trim()?.lowercase()
    val id = root.str("decisionId")?.trim().orEmpty()
    val reason = shortReason(root.str("reason"))
    if (decision == "allow" && id.matches(SAFE_TOKEN)) {
        return PolicyDecision(true, id, reason.ifBlank { "The policy check allowed this purpose." })
    }
    return PolicyDecision(false, "", reason.ifBlank { "The policy check denied this purpose." })
}

fun parseReceipt(body: String, subjectId: String, purpose: String, imageSha256: String, now: Long): LocalReceipt? {
    val root = try {
        JsonValue.parse(body)
    } catch (_: JsonException) {
        return null
    }
    val queueId = root.str("queueId")?.trim().orEmpty()
    if (!queueId.matches(SAFE_TOKEN)) return null
    val status = root.str("status")?.let(::cleanStatus) ?: "pending-human-review"
    return LocalReceipt(queueId, subjectId, purpose, imageSha256, now, status)
}

sealed class SubmitResult {
    data class Queued(val receipt: LocalReceipt) : SubmitResult()
    data class Stopped(val reason: String) : SubmitResult()
}

object CaptureFlow {
    fun submit(
        desk: EideticDesk,
        book: EnrollmentBook,
        subjectId: String,
        purpose: String,
        frameIsSubject: Boolean,
        jpeg: ByteArray,
        now: Long,
    ): SubmitResult {
        val gate = book.maySubmit(subjectId, purpose, frameIsSubject, now)
        if (gate is Gate.Closed) return SubmitResult.Stopped(gate.reason)
        if (jpeg.isEmpty() || jpeg.size > EideticLimits.MAX_JPEG_BYTES) {
            return SubmitResult.Stopped("The frame was not kept.")
        }
        val subject = book.get(subjectId) ?: return SubmitResult.Stopped("Choose an enrolled person.")
        val declared = cleanPurpose(purpose) ?: return SubmitResult.Stopped("Choose a purpose from the written consent.")
        val hash = sha256Hex(jpeg)
        val decision = try {
            desk.checkPolicy(subject, declared, hash)
        } catch (_: Exception) {
            return SubmitResult.Stopped("The desk could not be reached. The frame was not kept.")
        }
        if (!decision.allowed) {
            return SubmitResult.Stopped(decision.reason.ifBlank { "The policy check denied this purpose." })
        }
        val receipt = try {
            desk.enqueue(subject, declared, decision, jpeg, hash, now)
        } catch (_: Exception) {
            return SubmitResult.Stopped("The reviewer queue did not accept the frame. Nothing was kept.")
        }
        return SubmitResult.Queued(receipt)
    }
}

internal fun httpExchange(base: DeskUrl, method: String, path: String, body: String?): DeskResponse {
    require(path.startsWith("/") && !path.contains("..") && !path.contains('\\') && !path.contains("://"))
    require(path.none { it.isISOControl() || it.isWhitespace() })
    val connection = (URL(base.origin + path).openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Connection", "close")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.encodeToByteArray()) }
        }
        val code = connection.responseCode
        if (code in 300..399) return DeskResponse(code, "")
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { readLimited(it, 65_536) }.orEmpty()
        return DeskResponse(code, text)
    } finally {
        connection.disconnect()
    }
}

private fun readLimited(stream: InputStream, max: Int): String {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    var total = 0
    while (total < max) {
        val count = stream.read(chunk, 0, minOf(chunk.size, max - total))
        if (count < 0) break
        buffer.write(chunk, 0, count)
        total += count
    }
    if (total == max && stream.read() >= 0) throw DeskException("The desk response was too large.")
    return buffer.toString(Charsets.UTF_8)
}

private fun shortReason(raw: String?): String {
    if (raw == null) return ""
    val cleaned = raw.trim().replace(Regex("\\s+"), " ")
    if (cleaned.any { it.isISOControl() }) return ""
    return cleaned.take(200)
}
