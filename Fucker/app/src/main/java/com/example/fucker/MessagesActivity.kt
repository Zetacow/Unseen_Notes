package com.example.fucker

import android.os.Bundle
import android.widget.ListView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity

class MessagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        val messages = intent.getStringArrayListExtra("messages") ?: arrayListOf()
        val messagesListView = findViewById<ListView>(R.id.messagesListView)
        messagesListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messages)
    }
}
