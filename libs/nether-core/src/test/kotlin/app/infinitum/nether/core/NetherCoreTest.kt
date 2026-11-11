package app.infinitum.nether.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetherCoreTest {
    @Test
    fun kdfParamsMatchOwaspMinimum() {
        assertEquals(1, NetherKdf.KDF_ARGON2ID)
        assertEquals(19_456, NetherKdf.MEMORY_KIB)
        assertEquals(2, NetherKdf.ITERATIONS)
        assertEquals(1, NetherKdf.PARALLELISM)
        assertEquals(16, NetherKdf.SALT_LEN)
        assertEquals(32, NetherKdf.KEY_LEN)
        assertEquals(0L, NetherKdf.delayMillis(0))
        assertTrue(NetherKdf.delayMillis(3) > NetherKdf.delayMillis(1))
        assertEquals(30_000L, NetherKdf.delayMillis(20))
    }

    @Test
    fun vaultRoundTripRejectsWrongPassword() {
        val password = "correct horse battery".toCharArray()
        val doc = VaultDocument(listOf(NetherEntry(title = "Mail", username = "ada", secret = "s3cret")))
        val blob = NetherVault.create(password, doc)
        val header = NetherCipher.parse(blob)
        assertEquals(NetherKdf.KDF_ARGON2ID, header.kdf)
        assertEquals(NetherKdf.MEMORY_KIB, header.memoryKib)
        val opened = NetherVault.unlock(password, blob)
        assertEquals("ada", opened.document.entries.single().username)
        val again = NetherVault.save(opened.key, blob, opened.document)
        assertEquals("ada", NetherVault.unlock(password, again).document.entries.single().username)
        try {
            NetherVault.unlock("nope".toCharArray(), blob)
            throw AssertionError("wrong password was accepted")
        } catch (_: BadPasswordException) {
            // expected
        }
        opened.key.fill(0)
    }

    @Test
    fun generatorHonorsLengthAndClasses() {
        val password = PasswordGenerator.password(length = 24, symbols = true, digits = true)
        assertEquals(24, password.length)
        assertTrue(password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() })
        val phrase = PasswordGenerator.passphrase(6, "-")
        assertEquals(6, phrase.split("-").size)
        assertEquals(0, PasswordGenerator.strength("password"))
        assertTrue(PasswordGenerator.strength(password) >= 2)
    }

    @Test
    fun totpMatchesRfc6238Vectors() {
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        assertEquals("287082", Totp.code(secret, 59_000L))
        assertEquals("081804", Totp.code(secret, 1_111_111_109_000L))
        assertEquals("050471", Totp.code(secret, 1_111_111_111_000L))
        assertEquals("005924", Totp.code(secret, 1_234_567_890_000L))
        assertEquals("279037", Totp.code(secret, 2_000_000_000_000L))
        val spec = Totp.parseOtpAuth("otpauth://totp/Mail:ada?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=6&period=30")
        assertEquals("ada", spec.label)
        assertEquals(20, spec.secret.size)
        assertNull(Importers.throneVaultNote("hello"))
        assertTrue(Importers.throneVaultNote("THRONEVAULT1.P.abc")!!.contains("does not read"))
    }

    @Test
    fun csvAndHealthAndHistory() {
        val chrome = "name,url,username,password\nMail,https://mail.example,ada,password\n"
        val bitwarden = "name,login_username,login_password,login_uri,login_totp,folder,favorite,notes\nBank,ada,unique-long-passphrase,https://bank,otpauth://totp/x?secret=MFRGG,Money,1,note\n"
        assertEquals("ada", Importers.csv(chrome).single().username)
        assertEquals("unique-long-passphrase", Importers.csv(bitwarden).single().secret)
        val updated = EntryEdits.withNewSecret(NetherEntry(secret = "old"), "new")
        assertEquals("old", updated.history.single().secret)
        val weak = NetherEntry(title = "A", secret = "password", updatedAt = 0L)
        val reusedA = NetherEntry(title = "B", secret = "same-secret-value")
        val reusedB = NetherEntry(title = "C", secret = "same-secret-value")
        val report = Health.report(VaultDocument(listOf(weak, reusedA, reusedB)), now = 400L * 24 * 60 * 60 * 1000)
        assertEquals(listOf("A"), report.weak)
        assertEquals(listOf("B", "C"), report.reused)
        assertEquals(listOf("A"), report.old)
        assertNotEquals(NetherKdf.derive("a".toCharArray(), NetherKdf.randomSalt()).size, 0)
    }
}
