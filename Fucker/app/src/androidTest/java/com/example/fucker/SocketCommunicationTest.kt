package com.example.fucker

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class SocketCommunicationTest {
    private fun getPrivateMethod(name: String, vararg params: Class<*>): java.lang.reflect.Method {
        val m = MainActivity::class.java.getDeclaredMethod(name, *params)
        m.isAccessible = true
        return m
    }

    private fun setAesKey(activity: MainActivity, key: SecretKeySpec) {
        val f = MainActivity::class.java.getDeclaredField("aesKey")
        f.isAccessible = true
        f.set(activity, key)
    }

    @Test
    fun socket_sendReceive_encrypted() {
        val keyBytes = ByteArray(32) { 3 }
        val key = SecretKeySpec(keyBytes, "AES")
        val serverActivity = MainActivity()
        val clientActivity = MainActivity()
        setAesKey(serverActivity, key)
        setAesKey(clientActivity, key)
        val encrypt = getPrivateMethod("encryptMessage", String::class.java)
        val decrypt = getPrivateMethod("decryptMessage", String::class.java)

        val server = ServerSocket(0)
        val port = server.localPort
        var serverReceived: String? = null
        val serverThread = Thread {
            val socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            val enc = reader.readLine()
            val msg = decrypt.invoke(serverActivity, enc) as String
            serverReceived = msg
            val replyEnc = encrypt.invoke(serverActivity, "ACK:$msg") as String
            writer.println(replyEnc)
            socket.close()
            server.close()
        }
        serverThread.start()

        val clientSocket = Socket("127.0.0.1", port)
        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        val writer = PrintWriter(OutputStreamWriter(clientSocket.getOutputStream()), true)
        val enc = encrypt.invoke(clientActivity, "Hello") as String
        writer.println(enc)
        val replyEnc = reader.readLine()
        val reply = decrypt.invoke(clientActivity, replyEnc) as String
        clientSocket.close()
        serverThread.join()

        assertEquals("Hello", serverReceived)
        assertEquals("ACK:Hello", reply)
    }
}

