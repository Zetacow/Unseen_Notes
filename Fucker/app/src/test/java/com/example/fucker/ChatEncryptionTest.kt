package com.example.fucker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class ChatEncryptionTest {
    @Test
    fun encryptDecrypt_roundTrip() {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "AES")
        val message = "Hello World"

        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = encryptCipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        val encoded = Base64.getEncoder().encodeToString(combined)

        val decoded = Base64.getDecoder().decode(encoded)
        val iv2 = decoded.copyOfRange(0, 12)
        val ct = decoded.copyOfRange(12, decoded.size)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv2))
        val decrypted = decryptCipher.doFinal(ct)
        val result = String(decrypted, Charsets.UTF_8)

        assertEquals(message, result)
    }
}
