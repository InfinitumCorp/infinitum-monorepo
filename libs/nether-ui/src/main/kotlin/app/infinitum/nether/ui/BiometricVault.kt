package app.infinitum.nether.ui

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricVault {
    private const val ALIAS = "nether-bio"
    private const val STORE = "AndroidKeyStore"

    fun available(activity: FragmentActivity): Boolean {
        val result = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enroll(activity: FragmentActivity, vaultKey: ByteArray, onDone: (ByteArray) -> Unit, onError: (String) -> Unit) {
        try {
            val store = KeyStore.getInstance(STORE).apply { load(null) }
            store.deleteEntry(ALIAS)
            val key = generate(strongBox = true) ?: generate(strongBox = false) ?: return onError("Keystore unavailable.")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            authenticate(activity, cipher, onError) { unlocked ->
                val packed = unlocked.iv + unlocked.doFinal(vaultKey)
                onDone(packed)
            }
        } catch (error: Exception) {
            onError(error.message ?: "Could not enroll biometrics.")
        }
    }

    fun unlock(activity: FragmentActivity, packed: ByteArray, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        try {
            val iv = packed.copyOfRange(0, 12)
            val body = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            authenticate(activity, cipher, onError) { unlocked ->
                onKey(unlocked.doFinal(body))
            }
        } catch (error: Exception) {
            onError(error.message ?: "Biometric unlock failed.")
        }
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        onError: (String) -> Unit,
        onSuccess: (Cipher) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked == null) onError("Biometric unlock failed.") else onSuccess(unlocked)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Nether")
                .setSubtitle("Unlock the vault")
                .setNegativeButtonText("Use password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        return store.getKey(ALIAS, null) as SecretKey
    }

    private fun generate(strongBox: Boolean): SecretKey? {
        return try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
            val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            if (strongBox && Build.VERSION.SDK_INT >= 28) {
                builder.setIsStrongBoxBacked(true)
            }
            generator.init(builder.build())
            generator.generateKey()
        } catch (_: Exception) {
            null
        }
    }
}
