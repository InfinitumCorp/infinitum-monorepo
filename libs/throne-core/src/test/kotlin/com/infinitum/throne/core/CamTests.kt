package com.infinitum.throne.core

import com.infinitum.throne.core.cam.EditionTitle
import com.infinitum.throne.core.eidetic.CaptureFlow
import com.infinitum.throne.core.eidetic.ConsentStatement
import com.infinitum.throne.core.eidetic.DeskResponse
import com.infinitum.throne.core.eidetic.DeskUrl
import com.infinitum.throne.core.eidetic.EideticDesk
import com.infinitum.throne.core.eidetic.EnrollmentBook
import com.infinitum.throne.core.eidetic.EnrollmentDraft
import com.infinitum.throne.core.eidetic.Gate
import com.infinitum.throne.core.eidetic.ReceiptCodec
import com.infinitum.throne.core.eidetic.SubjectCodec
import com.infinitum.throne.core.eidetic.SubmitResult
import com.infinitum.throne.core.eidetic.VERIFY_ENROLLED
import com.infinitum.throne.core.eidetic.parseDecision
import com.infinitum.throne.core.eidetic.submissionJson
import com.infinitum.throne.core.hrvst.Hrvst
import com.infinitum.throne.core.hrvst.HrvstRules
import com.infinitum.throne.core.hrvst.SharePermit
import com.infinitum.throne.core.hrvst.SiloCodec
import com.infinitum.throne.core.json.JsonException
import com.infinitum.throne.core.json.JsonValue
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class EditionTitleTest {
    @Test
    fun keterAndArchangel() {
        assertEquals("^KETER", EditionTitle.title("keter"))
        assertEquals("^KETER", EditionTitle.title("KETER"))
        assertEquals("^KETER", EditionTitle.title("^KETER"))
        assertEquals("ARCH//ANGEL", EditionTitle.title("archangel"))
        assertEquals("ARCH//ANGEL", EditionTitle.title("ARCH//ANGEL"))
        assertEquals("ARCH//ANGEL", EditionTitle.title("SENTINEL"))
        assertEquals("ARCH//ANGEL", EditionTitle.title("MASTER"))
        assertEquals("ARCH//ANGEL", EditionTitle.title("nope"))
        assertEquals("ARCH//ANGEL", EditionTitle.title(""))
    }
}

class JsonValueTest {
    @Test
    fun roundTripAndRejects() {
        val rendered = JsonValue.obj(
            "name" to JsonValue.str("a\"b\\c"),
            "n" to JsonValue.num(7),
            "ok" to JsonValue.bool(true),
            "items" to JsonValue.arr(listOf(JsonValue.str("one"))),
            "empty" to JsonValue.Null,
        ).render()
        val parsed = JsonValue.parse(rendered)
        assertEquals("a\"b\\c", parsed.str("name"))
        assertEquals(7L, parsed.long("n"))
        assertEquals(true, parsed.bool("ok"))
        assertEquals(listOf("one"), parsed.strings("items"))
        assertTrue(parsed.field("empty") is JsonValue.Null)
        assertTrue(throws { JsonValue.parse("$rendered {}") })
        assertTrue(throws { JsonValue.parse("""{"a":1,"a":2}""") })
        assertTrue(throws { JsonValue.parse("{\"a\":\"\u0001\"}") })
    }
}

class EideticConsentTest {
    private val now = 1_700_000_000_000L

    @Test
    fun blocksCaptureUntilWrittenConsentAndPurposeMatch() {
        val book = EnrollmentBook()
        val id = "11111111-1111-1111-1111-111111111111"
        assertTrue(book.mayOpenCamera(id, now) is Gate.Closed)
        assertTrue(
            book.enroll(draft(onFile = false), now, id) is Gate.Closed,
        )
        assertTrue(book.enroll(draft(), now, id) is Gate.Open)
        assertTrue(book.mayOpenCamera(id, now) is Gate.Open)
        assertTrue(book.maySubmit(id, "other purpose", true, now) is Gate.Closed)
        assertTrue(book.maySubmit(id, "Desk review", false, now) is Gate.Closed)
        assertTrue(book.maySubmit(id, "Desk review", true, now) is Gate.Open)
        val encoded = SubjectCodec.encode(book.get(id)!!)
        val decoded = SubjectCodec.decode(encoded)
        assertEquals("Ada Lovelace", decoded?.legalName)
        val tampered = encoded.replace(ConsentStatement.sha256(), "0".repeat(64))
        val broken = SubjectCodec.decode(tampered)!!
        val held = EnrollmentBook(listOf(broken))
        assertTrue(held.maySubmit(id, "Desk review", true, now) is Gate.Closed)
    }

    @Test
    fun retentionRemovesTheSubjectAndNotAStranger() {
        val book = EnrollmentBook()
        val id = "22222222-2222-2222-2222-222222222222"
        book.enroll(draft(days = 1), now, id)
        assertTrue(book.sweep(now + 86_400_000L - 1).isEmpty())
        assertEquals(listOf(id), book.sweep(now + 86_400_000L))
        assertNull(book.get(id))
    }

    @Test
    fun deleteRemovesOnlyTheNamedSubject() {
        val book = EnrollmentBook()
        val first = "33333333-3333-3333-3333-333333333333"
        val second = "44444444-4444-4444-4444-444444444444"
        book.enroll(draft(), now, first)
        book.enroll(draft(name = "Grace Hopper"), now, second)
        book.delete(first)
        assertNull(book.get(first))
        assertNotNull(book.get(second))
    }

    private fun draft(
        name: String = "Ada Lovelace",
        onFile: Boolean = true,
        days: Int = 7,
    ) = EnrollmentDraft(
        legalName = name,
        purposes = listOf("Desk review"),
        retentionDays = days,
        writtenConsentOnFile = onFile,
        acknowledgment = ConsentStatement.ACKNOWLEDGMENT,
    )
}

class EideticDeskTest {
    private val now = 1_700_000_000_000L
    private val id = "55555555-5555-5555-5555-555555555555"

    @Test
    fun loopbackOnly() {
        assertNotNull(DeskUrl.parse("http://127.0.0.1:3000"))
        assertNotNull(DeskUrl.parse(" http://localhost:8787/ "))
        assertNotNull(DeskUrl.parse("https://localhost:8443"))
        listOf(
            "http://example.com:3000",
            "http://127.0.0.1.evil.com:3000",
            "http://localhost.evil:3000",
            "http://2130706433:3000",
            "http://127.0.0.1:3000/v1",
            "http://user@127.0.0.1:3000",
            "http://127.0.0.1:3000?x=1",
            "file:///etc/passwd",
            "http://127.0.0.1",
            "http://[::1]:3000",
        ).forEach { assertNull(it, DeskUrl.parse(it)) }
    }

    @Test
    fun closedGateDoesNotCallTheDesk() {
        val calls = AtomicInteger()
        val desk = desk { _, _, _ ->
            calls.incrementAndGet()
            DeskResponse(200, """{"decision":"allow","decisionId":"d1"}""")
        }
        val result = CaptureFlow.submit(desk, EnrollmentBook(), id, "Desk review", true, byteArrayOf(1), now)
        assertTrue(result is SubmitResult.Stopped)
        assertEquals(0, calls.get())
    }

    @Test
    fun denyDoesNotEnqueueAndSubmissionHasNoAction() {
        val book = enrolled()
        var queued = 0
        var policy = ""
        val desk = desk { _, path, body ->
            when (path) {
                "/v1/policy/check" -> {
                    policy = body.orEmpty()
                    DeskResponse(200, """{"decision":"deny","decisionId":"nope","reason":"purpose refused"}""")
                }
                else -> {
                    queued++
                    DeskResponse(200, """{"queueId":"q1"}""")
                }
            }
        }
        val result = CaptureFlow.submit(desk, book, id, "Desk review", true, byteArrayOf(1, 2, 3), now)
        assertTrue(result is SubmitResult.Stopped)
        assertEquals(0, queued)
        val parsed = JsonValue.parse(policy)
        assertEquals(VERIFY_ENROLLED, parsed.str("mode"))
        assertEquals(false, parsed.bool("bystanderIdentification"))
        assertFalse(parsed.field("action") != null)
        val keys = (JsonValue.parse(submissionJson(id, "Desk review", "d1", byteArrayOf(1), "ab")) as JsonValue.Obj).fields.keys
        assertEquals(
            setOf("decisionId", "subjectId", "purpose", "mode", "imageSha256", "imageJpegBase64", "disposition"),
            keys,
        )
        assertFalse(parseDecision("""{"decision":"allow"}""").allowed)
        assertFalse(parseDecision("""{"decision":"allow","decisionId":"../x"}""").allowed)
    }

    @Test
    fun allowQueuesForAPerson() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val posts = AtomicInteger()
        server.createContext("/v1/policy/check") { exchange ->
            exchange.requestBody.readBytes()
            val body = """{"decision":"allow","decisionId":"d1","reason":"on file"}""".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/v1/reviewer/queue") { exchange ->
            val request = String(exchange.requestBody.readBytes())
            posts.incrementAndGet()
            assertTrue(request.contains("\"disposition\":\"human-review-only\""))
            assertFalse(request.contains("\"action\""))
            val body = """{"queueId":"q1","status":"pending-human-review"}""".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = DeskUrl.parse("http://127.0.0.1:${server.address.port}")!!
            val result = CaptureFlow.submit(EideticDesk(base), enrolled(), id, "Desk review", true, byteArrayOf(9, 8, 7), now)
            val queued = result as SubmitResult.Queued
            assertEquals("q1", queued.receipt.queueId)
            assertEquals("pending-human-review", queued.receipt.status)
            assertEquals(1, posts.get())
            val encoded = ReceiptCodec.encode(queued.receipt)
            assertEquals("q1", ReceiptCodec.decode(encoded)?.queueId)
            assertNull(ReceiptCodec.decode(encoded.replace("pending-human-review", "APPLY NOW")))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun redirectIsNotFollowed() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/policy/check") { exchange ->
            exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Location", "http://example.com/collect")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/v1/reviewer/queue") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val base = DeskUrl.parse("http://127.0.0.1:${server.address.port}")!!
            val result = CaptureFlow.submit(EideticDesk(base), enrolled(), id, "Desk review", true, byteArrayOf(1), now)
            assertTrue(result is SubmitResult.Stopped)
        } finally {
            server.stop(0)
        }
    }

    private fun enrolled(): EnrollmentBook {
        val book = EnrollmentBook()
        assertTrue(book.enroll(EnrollmentDraft("Ada Lovelace", listOf("Desk review"), 7, true, ConsentStatement.ACKNOWLEDGMENT), now, id) is Gate.Open)
        return book
    }

    private fun desk(
        exchange: (String, String, String?) -> DeskResponse,
    ) = EideticDesk(DeskUrl.parse("http://127.0.0.1:9")!!, exchange)
}

class HrvstSiloTest {
    private val now = 1_700_000_000_000L

    @Test
    fun packetChainsAndShareIsPerPacket() {
        val first = HrvstRules.packet(id(1), byteArrayOf(1, 2), now, "0.1.0-keter", "KM4", null)!!
        val second = HrvstRules.packet(id(2), byteArrayOf(3, 4), now + 1, "0.1.0-keter", "KM4", first.contentSha256)!!
        assertEquals(first.contentSha256, second.provenance.previousContentSha256)
        assertEquals(Hrvst.EVENT, second.provenance.event)
        assertTrue(HrvstRules.permit(second, "Ada") is SharePermit.Refused)
        val granted = HrvstRules.grant(second, "Ada", includeImage = false, now)!!
        val other = HrvstRules.grant(first, "Ada", includeImage = true, now)!!
        val permit = HrvstRules.permit(granted, "Ada") as SharePermit.Allowed
        assertFalse(permit.includeImage)
        assertTrue(permit.manifest.contains(second.id))
        assertTrue(permit.manifest.contains("previous: ${first.contentSha256}"))
        assertFalse(permit.manifest.contains(first.id))
        assertTrue(HrvstRules.permit(other, "Grace") is SharePermit.Refused)
        assertNull(HrvstRules.grant(second, "*", true, now))
        assertNull(HrvstRules.grant(second, " ", true, now))
        val round = SiloCodec.decodePacket(SiloCodec.encodePacket(granted))
        assertEquals(second.contentSha256, round?.contentSha256)
        assertEquals("Ada", round?.grants?.single()?.recipient)
        val meta = SiloCodec.decodeMeta(SiloCodec.encodeMeta(com.infinitum.throne.core.hrvst.SiloMeta(id(3), "William Aaron Turner", 30)))
        assertEquals(30, meta?.retentionDays)
        assertEquals(listOf(second.id), HrvstRules.retain(listOf(first, second), 1, now + Hrvst.DAY_MS).map { it.id })
    }

    private fun id(n: Int) = "66666666-6666-6666-6666-66666666666$n"
}

private fun throws(block: () -> Unit): Boolean = try {
    block()
    false
} catch (_: JsonException) {
    true
}
