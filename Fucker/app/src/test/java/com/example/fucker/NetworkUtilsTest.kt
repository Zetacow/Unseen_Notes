package com.example.fucker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {
    @Test
    fun testIsSameSubnet() {
        assertTrue(isSameSubnet("192.168.1.5", "192.168.1.10"))
        assertFalse(isSameSubnet("192.168.1.5", "192.168.2.10"))
    }
}
