package com.example.fucker

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class MainActivityCryptoTest {
    private fun getPrivateMethod(name: String, vararg params: Class<*>): java.lang.reflect.Method {
        val m = MainActivity::class.java.getDeclaredMethod(name, *params)
        m.isAccessible = true
        return m
    }

    private fun getAesKeyField(): java.lang.reflect.Field {
        val f = MainActivity::class.java.getDeclaredField("aesKey")
        f.isAccessible = true
        return f
    }

    @Test
    fun hkdf_producesExpectedKey() {
        val activity = MainActivity()
        val hkdf = getPrivateMethod("hkdf", ByteArray::class.java, Int::class.javaPrimitiveType)
        val secret = "supersecret".toByteArray(Charsets.UTF_8)
        val derived = hkdf.invoke(activity, secret, 32) as ByteArray
        val expected = byteArrayOf(
            0x78.toByte(),0xF2.toByte(),0xD7.toByte(),0xEA.toByte(),0xB7.toByte(),0x3D.toByte(),0x62.toByte(),0x9C.toByte(),
            0x04.toByte(),0x14.toByte(),0x26.toByte(),0x69.toByte(),0xD6.toByte(),0x71.toByte(),0x1A.toByte(),0xA2.toByte(),
            0x17.toByte(),0xEA.toByte(),0x8D.toByte(),0xA5.toByte(),0x16.toByte(),0x63.toByte(),0x69.toByte(),0x2E.toByte(),
            0x83.toByte(),0xBE.toByte(),0xA6.toByte(),0xAD.toByte(),0x5B.toByte(),0x52.toByte(),0x0F.toByte(),0x0A.toByte()
        )
        assertArrayEquals(expected, derived)
    }

    @Test
    fun encryptDecrypt_roundTrip() {
        val activity = MainActivity()
        val keyBytes = ByteArray(32) { 1 }
        val key = SecretKeySpec(keyBytes, "AES")
        getAesKeyField().set(activity, key)
        val encrypt = getPrivateMethod("encryptMessage", String::class.java)
        val decrypt = getPrivateMethod("decryptMessage", String::class.java)
        val message = "Hello World"
        val encrypted = encrypt.invoke(activity, message) as String
        assertNotEquals("", encrypted)
        val decrypted = decrypt.invoke(activity, encrypted) as String
        assertEquals(message, decrypted)
    }

    @Test
    fun encrypt_withoutKey_returnsEmpty() {
        val activity = MainActivity()
        val encrypt = getPrivateMethod("encryptMessage", String::class.java)
        val result = encrypt.invoke(activity, "test") as String
        assertEquals("", result)
    }

    @Test
    fun decrypt_invalidInput_returnsEmpty() {
        val activity = MainActivity()
        val keyBytes = ByteArray(32) { 2 }
        val key = SecretKeySpec(keyBytes, "AES")
        getAesKeyField().set(activity, key)
        val decrypt = getPrivateMethod("decryptMessage", String::class.java)
        val result = decrypt.invoke(activity, "not_base64") as String
        assertEquals("", result)
    }
}

