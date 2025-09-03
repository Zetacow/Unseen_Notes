package com.example.fucker

import android.os.Bundle
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.fucker.ChatSession
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class ChatActivity : AppCompatActivity() {
    private lateinit var chatListView: ListView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private val chatMessages: MutableList<String> = mutableListOf()
    private lateinit var adapter: ArrayAdapter<String>
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var aesKey: SecretKeySpec? = null
    private var running = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatListView = findViewById(R.id.chatListView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages)
        chatListView.adapter = adapter

        socket = ChatSession.socket
        aesKey = ChatSession.aesKey
        if (socket != null) {
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream()), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            startReceivingMessages()
        }

        sendButton.setOnClickListener {
            val msg = messageInput.text.toString()
            if (msg.isNotBlank() && writer != null && aesKey != null) {
                val encryptedMsg = encryptMessage(msg)
                writer!!.println(encryptedMsg)
                chatMessages.add("Me: $msg")
                adapter.notifyDataSetChanged()
                messageInput.text.clear()
            }
        }
    }

    private fun startReceivingMessages() {
        Thread {
            try {
                while (running) {
                    val line = reader?.readLine()
                    if (line != null) {
                        val decryptedMsg = decryptMessage(line)
                        runOnUiThread {
                            chatMessages.add("Peer: $decryptedMsg")
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun encryptMessage(message: String): String {
        return try {
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)
            val ciphertext = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decryptMessage(encrypted: String): String {
        return try {
            val decoded = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
            if (decoded.size < 13) return ""
            val iv = decoded.copyOfRange(0, 12)
            val ciphertext = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try { socket?.close() } catch (_: Exception) {}
    }
}
