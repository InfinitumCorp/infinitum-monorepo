package com.infinitum.throne.core

import com.infinitum.throne.core.audit.Audit
import com.infinitum.throne.core.audit.AuditEngine
import com.infinitum.throne.core.audit.DeviceSnapshot
import com.infinitum.throne.core.audit.FindingStatus
import com.infinitum.throne.core.audit.InstalledApp
import com.infinitum.throne.core.crypto.NotebookFormatException
import com.infinitum.throne.core.crypto.SavedCipher
import com.infinitum.throne.core.crypto.VaultContact
import com.infinitum.throne.core.crypto.VaultMessages
import com.infinitum.throne.core.crypto.VaultOpenException
import com.infinitum.throne.core.crypto.VaultNote
import com.infinitum.throne.core.crypto.VaultNotebook
import com.infinitum.throne.core.crypto.hkdfSha256
import com.infinitum.throne.core.crypto.pbkdf2HmacSha256
import com.infinitum.throne.core.home.Edition
import com.infinitum.throne.core.home.EditionPolicy
import com.infinitum.throne.core.home.GlyphPixels
import com.infinitum.throne.core.home.HomeCatalog
import com.infinitum.throne.core.home.HomePage
import com.infinitum.throne.core.home.Launchable
import com.infinitum.throne.core.net.PrivateDnsPolicy
import com.infinitum.throne.core.net.ProbeDecision
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class KdfTest {
    @Test
    fun pbkdf2Sha256Vectors() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            pbkdf2HmacSha256("password".encodeToByteArray(), "salt".encodeToByteArray(), 1, 32).toHex(),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            pbkdf2HmacSha256("password".encodeToByteArray(), "salt".encodeToByteArray(), 2, 32).toHex(),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            pbkdf2HmacSha256("password".encodeToByteArray(), "salt".encodeToByteArray(), 4096, 32).toHex(),
        )
    }

    @Test
    fun hkdfRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            hkdfSha256(ikm, salt, info, 42).toHex(),
        )
    }
}

class VaultMessagesTest {
    @Test
    fun passphraseRoundTripAndTamper() {
        val secret = "swordfish-secret".encodeToByteArray()
        val armored = VaultMessages.encryptPassphrase(secret, "correct horse".toCharArray())
        assertFalse(armored.contains("swordfish-secret"))
        assertFalse(armored.contains("correct horse"))
        assertArrayEquals(secret, VaultMessages.decryptPassphrase(VaultMessages.shareText(armored), "correct horse".toCharArray()))
        assertThrows(VaultOpenException::class.java) {
            VaultMessages.decryptPassphrase(armored, "wrong horse".toCharArray())
        }
        val broken = armored.dropLast(2) + "aa"
        assertThrows(VaultOpenException::class.java) {
            VaultMessages.decryptPassphrase(broken, "correct horse".toCharArray())
        }
    }

    @Test
    fun shortPassphraseIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultMessages.encryptPassphrase("hello".encodeToByteArray(), "short".toCharArray())
        }
    }

    @Test
    fun hugeIterationCountIsRejectedBeforeDerivation() {
        val payload = ByteArray(2 + 4 + 16 + 12 + 16)
        payload[0] = 1
        payload[1] = 1
        payload[2] = 0x3B.toByte()
        payload[3] = 0x9A.toByte()
        payload[4] = 0xCA.toByte()
        payload[5] = 0x00.toByte()
        val armored = "THRONEVAULT1.P." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        assertThrows(VaultOpenException::class.java) {
            VaultMessages.decryptPassphrase(armored, "correct horse".toCharArray())
        }
    }

    @Test
    fun x25519RoundTripWrongKeyAndSafetyNumber() {
        val alice = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val bob = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val bobRaw = VaultMessages.rawPublicFrom(bob.public)
        val armored = VaultMessages.encryptX25519("field note".encodeToByteArray(), bobRaw)
        assertFalse(armored.contains("field note"))
        assertArrayEquals(
            "field note".encodeToByteArray(),
            VaultMessages.decryptX25519(VaultMessages.shareText(armored), bob.private),
        )
        assertThrows(VaultOpenException::class.java) {
            VaultMessages.decryptX25519(armored, alice.private)
        }
        val payload = VaultMessages.publicPayload(bobRaw)
        assertArrayEquals(bobRaw, VaultMessages.parsePublicPayload(payload))
        assertEquals(VaultMessages.safetyNumber(bobRaw), VaultMessages.safetyNumber(VaultMessages.parsePublicPayload("  $payload  ")))
        assertThrows(IllegalArgumentException::class.java) {
            VaultMessages.parsePublicPayload(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32)))
        }
    }
}

class NotebookTest {
    @Test
    fun roundTrip() {
        val book = VaultNotebook(
            contacts = listOf(VaultContact("Ada", ByteArray(32) { 7 })),
            notes = listOf(VaultNote("n1", 10L, "café")),
            saved = listOf(SavedCipher("s1", 11L, "THRONEVAULT1.P.abc")),
        )
        val decoded = VaultNotebook.decode(book.encode())
        assertEquals("Ada", decoded.contacts.single().name)
        assertArrayEquals(ByteArray(32) { 7 }, decoded.contacts.single().rawPublic)
        assertEquals("café", decoded.notes.single().text)
        assertEquals("THRONEVAULT1.P.abc", decoded.saved.single().armor)
    }

    @Test
    fun badMagicFails() {
        assertThrows(NotebookFormatException::class.java) {
            VaultNotebook.decode("nope".encodeToByteArray())
        }
    }
}

class HomeRulesTest {
    @Test
    fun archangelHidesAetherAndKeepsOrder() {
        val apps = listOf(
            Launchable("com.android.settings", "Settings", "Settings"),
            Launchable("com.infinitum.aether", "Main", "ÆTHER MIL"),
            Launchable("com.conjure.app", "Main", "Conjure"),
            Launchable("com.infinitum.veilvpn", "Main", "VEIL"),
            Launchable(EditionPolicy.LAUNCHER_PACKAGE, "Main", "Launcher"),
            Launchable("com.infinitum.throne.vault", "Main", "Vault"),
        )
        val archangel = HomeCatalog.arrange(apps, Edition.ARCHANGEL, EditionPolicy.LAUNCHER_PACKAGE)
        assertEquals(listOf("com.infinitum.veilvpn", "com.conjure.app", "com.infinitum.throne.vault"), archangel.infinitum.map { it.packageName })
        assertEquals(listOf("com.android.settings"), archangel.others.map { it.packageName })
        assertTrue(archangel.infinitum.none { it.packageName == "com.infinitum.aether" })
        assertTrue(archangel.others.none { it.packageName == "com.infinitum.aether" })
        val keter = HomeCatalog.arrange(apps, Edition.KETER, EditionPolicy.LAUNCHER_PACKAGE)
        assertEquals("com.infinitum.aether", keter.infinitum[1].packageName)
    }

    @Test
    fun unknownEditionFailsClosed() {
        assertEquals(Edition.ARCHANGEL, EditionPolicy.parse("nope"))
        assertEquals(Edition.ARCHANGEL, EditionPolicy.parse("ARCH//ANGEL"))
        assertEquals(Edition.ARCHANGEL, EditionPolicy.parse("archangel"))
        assertEquals("ARCH//ANGEL", Edition.ARCHANGEL.label)
        assertEquals("^KETER", Edition.KETER.label)
        assertEquals(Edition.KETER, EditionPolicy.parse("^KETER"))
        assertEquals(Edition.KETER, EditionPolicy.parse("keter"))
        assertEquals(Edition.ARCHANGEL, EditionPolicy.parse(" master "))
    }

    @Test
    fun namedPackagesStayInDesignOrder() {
        assertEquals(
            listOf(
                "com.infinitum.veilvpn",
                "app.cumuluscloud.mail",
                "app.scalvs",
                "com.infinitum.fabric",
                "com.infinitum.aether",
                "com.infinitum.architect",
                "com.conjure.app",
                "app.infinitum.veil.browser",
                "com.infinitum.eidetic.cam",
                "com.infinitum.hrvst.cam",
            ),
            EditionPolicy.infinitumOrder.take(10),
        )
        assertEquals(setOf("com.infinitum.aether"), EditionPolicy.milPackages)
        assertEquals(HomePage.INFINITUM.name, "INFINITUM")
    }
}

class GlyphPixelsTest {
    @Test
    fun darkGlyphOnTransparentBecomesBlack() {
        val pixels = IntArray(4)
        pixels[1] = argb(255, 10, 10, 10)
        val out = GlyphPixels.desaturateThreshold(pixels, 2, 2)
        assertEquals(0, out[0])
        assertEquals(0xFF000000.toInt(), out[1])
    }

    @Test
    fun lightGlyphOnTransparentBecomesBlack() {
        val pixels = IntArray(4)
        pixels[0] = argb(255, 250, 250, 250)
        pixels[3] = argb(255, 240, 240, 240)
        val out = GlyphPixels.desaturateThreshold(pixels, 2, 2)
        assertEquals(0xFF000000.toInt(), out[0])
        assertEquals(0, out[1])
        assertEquals(0xFF000000.toInt(), out[3])
    }

    @Test
    fun darkGlyphOnWhiteSquareDropsTheBackground() {
        val pixels = IntArray(9) { argb(255, 255, 255, 255) }
        pixels[4] = argb(255, 0, 0, 0)
        val out = GlyphPixels.desaturateThreshold(pixels, 3, 3)
        assertEquals(0xFF000000.toInt(), out[4])
        assertEquals(0, out[0])
        assertEquals(0, out[8])
    }
}

class PrivateDnsPolicyTest {
    @Test
    fun allowsSuggestedAndConfiguredHostsOnly() {
        assertTrue(PrivateDnsPolicy.probeDecision("dns.adguard-dns.com", null) is ProbeDecision.Allow)
        assertTrue(PrivateDnsPolicy.probeDecision("DNS.QUAD9.NET.", null) is ProbeDecision.Allow)
        val configured = PrivateDnsPolicy.probeDecision("dns.example.net", "dns.example.net")
        assertEquals("dns.example.net", (configured as ProbeDecision.Allow).host)
        assertTrue(PrivateDnsPolicy.probeDecision("127.0.0.1", "127.0.0.1") is ProbeDecision.Refuse)
        assertTrue(PrivateDnsPolicy.probeDecision("localhost", null) is ProbeDecision.Refuse)
        assertTrue(PrivateDnsPolicy.probeDecision("printer.local", "printer.local") is ProbeDecision.Refuse)
        assertTrue(PrivateDnsPolicy.probeDecision("evil.example", null) is ProbeDecision.Refuse)
    }
}

class AuditEngineTest {
    @Test
    fun healthyPhoneIsClearWhereItCanBe() {
        val report = AuditEngine.assess(healthy())
        assertEquals(FindingStatus.CLEAR, status(report, "msg_default"))
        assertEquals(FindingStatus.CLEAR, status(report, "msg_version"))
        assertEquals(FindingStatus.NOTE, status(report, "msg_rcs"))
        assertEquals(FindingStatus.NOTE, status(report, "msg_sms_fallback"))
        assertEquals(FindingStatus.CLEAR, status(report, "msg_lockscreen"))
        assertEquals(FindingStatus.CLEAR, status(report, "veil"))
        assertEquals(FindingStatus.CLEAR, status(report, "screen_lock"))
        assertEquals(FindingStatus.CLEAR, status(report, "encryption"))
        assertEquals(FindingStatus.CLEAR, status(report, "private_dns"))
        assertEquals(FindingStatus.CLEAR, status(report, "usb_debugging"))
        assertEquals("com.camera.app", report.camera.single().packageName)
    }

    @Test
    fun messagingAndVeilGapsAreReview() {
        val report = AuditEngine.assess(
            healthy().copy(
                defaultSmsPackage = "com.other.sms",
                messagesVersionName = "20260101",
                lockScreenPrivateContentAllowed = true,
                alwaysOnVpnPackage = "",
                alwaysOnLockdown = false,
                activeVpnPackages = emptyList(),
                developerOptions = true,
                usbDebugging = true,
            ),
        )
        assertEquals(FindingStatus.REVIEW, status(report, "msg_default"))
        assertEquals(FindingStatus.REVIEW, status(report, "msg_version"))
        assertEquals(FindingStatus.REVIEW, status(report, "msg_lockscreen"))
        assertEquals(FindingStatus.REVIEW, status(report, "veil"))
        assertEquals(FindingStatus.REVIEW, status(report, "usb_debugging"))
        val veil = report.findings.first { it.id == "veil" }
        assertTrue(veil.summary.contains("Lockdown is off"))
    }

    @Test
    fun hiddenVeilSettingIsNotReportedAsLockedDown() {
        val report = AuditEngine.assess(
            healthy().copy(
                alwaysOnVpnReadable = false,
                alwaysOnVpnPackage = null,
                alwaysOnLockdownReadable = false,
                alwaysOnLockdown = null,
                activeVpnPackages = listOf(Audit.VEIL_PACKAGE),
            ),
        )
        val veil = report.findings.first { it.id == "veil" }
        assertEquals(FindingStatus.REVIEW, veil.status)
        assertFalse(veil.summary.contains("lockdown is on"))
    }

    @Test
    fun installersAndOemPackages() {
        val report = AuditEngine.assess(
            healthy().copy(
                apps = listOf(
                    app("com.transsion.trancare", system = true),
                    app("com.sideload.helper").copy(
                        declaresInstallPackages = true,
                        unknownInstallAllowed = true,
                    ),
                ),
            ),
        )
        assertEquals(FindingStatus.REVIEW, status(report, "oem_analytics"))
        assertEquals(FindingStatus.REVIEW, status(report, "unknown_installers"))
    }
}

private fun status(report: com.infinitum.throne.core.audit.AuditReport, id: String): FindingStatus {
    return report.findings.first { it.id == id }.status
}

private fun app(packageName: String, system: Boolean = false, vararg permissions: String) = InstalledApp(
    packageName = packageName,
    label = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
    system = system,
    grantedDangerous = permissions.toList(),
    declaresInstallPackages = false,
    unknownInstallAllowed = false,
    installerPackage = "com.android.vending",
)

private fun healthy() = DeviceSnapshot(
    apps = listOf(
        app("com.camera.app", permissions = arrayOf(Audit.CAMERA)),
        app(Audit.VEIL_PACKAGE),
        app(Audit.MESSAGES_PACKAGE),
    ),
    accessibilityServices = emptyList(),
    deviceAdmins = emptyList(),
    privateDnsReadable = true,
    privateDnsMode = "hostname",
    privateDnsSpecifier = PrivateDnsPolicy.RECOMMENDED_HOST,
    deviceSecure = true,
    encryptionStatus = Audit.ENCRYPTION_ACTIVE,
    developerOptions = false,
    usbDebugging = false,
    veilInstalled = true,
    alwaysOnVpnReadable = true,
    alwaysOnVpnPackage = Audit.VEIL_PACKAGE,
    alwaysOnLockdownReadable = true,
    alwaysOnLockdown = true,
    activeVpnPackages = listOf(Audit.VEIL_PACKAGE),
    defaultSmsPackage = Audit.MESSAGES_PACKAGE,
    messagesInstalled = true,
    messagesVersionName = "20261022_01",
    lockScreenNotificationsShown = true,
    lockScreenPrivateContentAllowed = false,
)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
