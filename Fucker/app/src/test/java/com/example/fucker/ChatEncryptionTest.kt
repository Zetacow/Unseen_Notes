package com.example.fucker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class ChatEncryptionTest {
    @Test
    fun encryptDecrypt_roundTrip() {
        val keyBytes = "0123456789abcdef".toByteArray(Charsets.UTF_8)
        val key = SecretKeySpec(keyBytes, "AES")
        val message = "Hello World"

        val encryptCipher = Cipher.getInstance("AES")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = encryptCipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(encrypted)

        val decryptCipher = Cipher.getInstance("AES")
        decryptCipher.init(Cipher.DECRYPT_MODE, key)
        val decrypted = decryptCipher.doFinal(Base64.getDecoder().decode(encoded))
        val result = String(decrypted, Charsets.UTF_8)

        assertEquals(message, result)
    }
}
