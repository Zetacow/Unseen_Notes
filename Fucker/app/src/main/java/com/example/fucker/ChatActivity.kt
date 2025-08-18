package com.example.fucker

import android.os.Bundle
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.fucker.ChatSession
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import android.os.Handler
import android.os.Looper
import javax.crypto.spec.SecretKeySpec

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
            val cipher = javax.crypto.Cipher.getInstance("AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey)
            val encrypted = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decryptMessage(encrypted: String): String {
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey)
            val decrypted = cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT))
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
