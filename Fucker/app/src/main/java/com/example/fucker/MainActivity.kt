package com.example.fucker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import android.os.Handler
import android.os.Looper
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Button
import java.util.concurrent.Executors
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import android.widget.TextView

// Singleton to hold chat session info
object ChatSession {
    var socket: Socket? = null
    var isServer: Boolean = false
    var aesKey: SecretKeySpec? = null
}

// MainActivity handles P2P discovery, secure messaging, and migration to Wi-Fi
class MainActivity : AppCompatActivity() {
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isHost = false
    private var aesKey: SecretKeySpec? = null
    private var discoveryStatusText: TextView? = null
    private var peerListView: ListView? = null
    private var discoveredPeers: MutableList<WifiP2pDevice> = mutableListOf()

    // Called when the activity is created; sets up Wi-Fi P2P and starts peer discovery
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        discoveryStatusText = findViewById(R.id.discoveryStatusText)
        peerListView = findViewById(R.id.peerListView)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel, peerListListener)
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager.requestConnectionInfo(channel, connectionInfoListener)
                    }
                }
            }
        }

        discoverPeers()
    }

    // Initiates peer discovery using Wi-Fi Direct with a 1-minute timeout
    private fun discoverPeers() {
        runOnUiThread {
            discoveryStatusText?.text = "Peer discovery started..."
        }
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("MainActivity", "Discovery Started")
                    // Set a timeout to stop discovery after 1 minute
                    Handler(Looper.getMainLooper()).postDelayed({
                        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d("MainActivity", "Peer discovery stopped after timeout.")
                                runOnUiThread {
                                    discoveryStatusText?.text = "Peer discovery stopped."
                                }
                            }
                            override fun onFailure(reason: Int) {
                                Log.e("MainActivity", "Failed to stop peer discovery: $reason")
                                runOnUiThread {
                                    discoveryStatusText?.text = "Failed to stop peer discovery."
                                }
                            }
                        })
                    }, 60_000)
                }
                override fun onFailure(reason: Int) {
                    Log.e("MainActivity", "Discovery failed: $reason")
                    runOnUiThread {
                        discoveryStatusText?.text = "Peer discovery failed."
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "discoverPeers error: ${e.message}")
            Log.e("MainActivity", Log.getStackTraceString(e))
        }
    }

    // Listener for discovered peers; logs their info and updates UI
    private val peerListListener = PeerListListener { peers ->
        discoveredPeers.clear()
        discoveredPeers.addAll(peers.deviceList)
        val peerNames = discoveredPeers.map { it.deviceName + " (" + it.deviceAddress + ")" }
        runOnUiThread {
            peerListView?.adapter = ArrayAdapter(
                this,
                R.layout.peer_list_item,
                peerNames
            )
        }
        peerListView?.setOnItemClickListener { _, _, position, _ ->
            val selectedPeer = discoveredPeers[position]
            connectToPeer(selectedPeer)
        }
    }

    // Initiates connection to the selected peer
    private fun connectToPeer(peer: WifiP2pDevice) {
        val config = WifiP2pManager.WpsInfo().apply { setup = WifiP2pManager.WpsInfo.PBC }
        val connectionConfig = WifiP2pManager.WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            wps = config
        }
        manager.connect(channel, connectionConfig, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("MainActivity", "Connecting to peer: ${peer.deviceName}")
                runOnUiThread {
                    discoveryStatusText?.text = "Connecting to ${peer.deviceName}..."
                }
            }
            override fun onFailure(reason: Int) {
                Log.d("MainActivity", "Failed to connect to peer: $reason")
                runOnUiThread {
                    discoveryStatusText?.text = "Failed to connect to ${peer.deviceName}."
                }
            }
        })
    }

    // Listener for connection info; determines host/client and starts server/client
    private val connectionInfoListener = ConnectionInfoListener { info ->
        val groupOwnerAddress: InetAddress = info.groupOwnerAddress
        isHost = info.isGroupOwner
        if (info.groupFormed && isHost) {
            Log.d("MainActivity", "Host: Starting server socket")
            startServer()
        } else if (info.groupFormed) {
            Log.d("MainActivity", "Client: Connecting to host")
            startClient(groupOwnerAddress)
        }
    }

    // Starts a server socket for incoming P2P connections (host device)
    private fun startServer() {
        executor.execute {
            try {
                serverSocket = ServerSocket(8888)
                Log.d("MainActivity", "Server: Waiting for client...")
                val socket = serverSocket!!.accept()
                Log.d("MainActivity", "Server: Client connected")
                handleSocket(socket, isServer = true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Server error: ${e.message}")
                Log.e("MainActivity", Log.getStackTraceString(e))
            }
        }
    }

    // Connects to the host device as a client
    private fun startClient(hostAddress: InetAddress) {
        executor.execute {
            try {
                clientSocket = Socket(hostAddress, 8888)
                Log.d("MainActivity", "Client: Connected to server")
                handleSocket(clientSocket!!, isServer = false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Client error: ${e.message}")
                Log.e("MainActivity", Log.getStackTraceString(e))
            }
        }
    }

    // Launches the live chat UI and manages socket communication
    private fun launchLiveChat(socket: Socket, isServer: Boolean) {
        runOnUiThread {
            val intent = Intent(this, ChatActivity::class.java)
            // Pass socket info via a singleton or service (not via intent extras)
            ChatSession.socket = socket
            ChatSession.isServer = isServer
            ChatSession.aesKey = aesKey
            startActivity(intent)
        }
    }

    /**
     * Handles socket communication for both server and client.
     * Performs secure chat, migration, and fallback logic.
     */
    private fun handleSocket(socket: Socket, isServer: Boolean) {
        // After handshake and key exchange, launch live chat UI
        // Diffie-Hellman key exchange
        val keyPairGen = KeyPairGenerator.getInstance("DH")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        val keyAgreement = KeyAgreement.getInstance("DH")
        keyAgreement.init(keyPair.private)

        // Send public key
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val myPubKeyEncoded = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)
        writer.println(myPubKeyEncoded)

        // Receive peer's public key
        val peerPubKeyEncoded = reader.readLine()
        val peerPubKeyBytes = Base64.decode(peerPubKeyEncoded, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("DH")
        val peerPubKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubKeyBytes))

        // Generate shared secret
        keyAgreement.doPhase(peerPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        aesKey = SecretKeySpec(sharedSecret.copyOf(32), "AES") // Use first 32 bytes for AES-256

        // Exchange local IP addresses to check shared network
        val myIp = socket.localAddress.hostAddress
        val encryptedIp = encryptMessage(myIp)
        writer.println(encryptedIp)

        val peerEncryptedIp = reader.readLine()
        val peerIp = decryptMessage(peerEncryptedIp)
        val sharedNetwork = isSameSubnet(myIp, peerIp)
        Log.d("MainActivity", "My IP: $myIp, Peer IP: $peerIp, Shared Network: $sharedNetwork")

        if (sharedNetwork) {
            Log.d("MainActivity", "Migrating session to Wi-Fi network...")
            migrateSessionToWifi(peerIp)
            socket.close() // Close the P2P socket after migration
            return
        }

        // Launch live chat UI
        launchLiveChat(socket, isServer)
    }

    // Migrates session to Wi-Fi network using peer's IP
    /**
     * Migrates the session to a regular Wi-Fi connection using the peer's IP.
     * Monitors the Wi-Fi socket for disconnection and falls back to P2P if lost.
     */
    private fun migrateSessionToWifi(peerIp: String) {
        executor.execute {
            var wifiSocket: Socket? = null
            try {
                wifiSocket = Socket(peerIp, 8888)
                Log.d("MainActivity", "Connected over Wi-Fi to $peerIp")
                // Example: Monitor Wi-Fi socket for disconnection
                val reader = BufferedReader(InputStreamReader(wifiSocket.getInputStream()))
                val writer = PrintWriter(OutputStreamWriter(wifiSocket.getOutputStream()), true)
                writer.println(encryptMessage("Hello from Wi-Fi!"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val decryptedMsg = decryptMessage(line)
                    Log.d("MainActivity", "Wi-Fi Received: $decryptedMsg")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Wi-Fi connection lost, falling back to P2P: ${e.message}")
                Log.e("MainActivity", Log.getStackTraceString(e))
                // Fallback to P2P
                fallbackToP2P()
            } finally {
                try { wifiSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    // Attempts to fallback to P2P connection
    // Attempts to fallback to P2P connection if Wi-Fi fails
    private fun fallbackToP2P() {
        executor.execute {
            try {
                Log.d("MainActivity", "Attempting to fallback to P2P connection...")
                // Re-initiate peer discovery and connection
                discoverPeers()
            } catch (e: Exception) {
                Log.e("MainActivity", "Fallback to P2P error: ${e.message}")
                Log.e("MainActivity", Log.getStackTraceString(e))
            }
        }
    }
    /**
     * Encrypts a message using AES-256.
     * Returns an empty string if encryption is not established.
     */
    private fun encryptMessage(message: String): String {
        if (aesKey == null) {
            Log.e("MainActivity", "Encryption not established. Message not sent.")
            return ""
        }
        return try {
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val encrypted = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("MainActivity", "Encryption error: ${e.message}")
            Log.e("MainActivity", Log.getStackTraceString(e))
            ""
        }
    }

    /**
     * Decrypts a message using AES-256.
     * Returns an empty string if decryption fails.
     */
    private fun decryptMessage(encrypted: String): String {
        return try {
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, aesKey)
            val decrypted = cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("MainActivity", "Decryption error: ${e.message}")
            Log.e("MainActivity", Log.getStackTraceString(e))
            ""
        }
    }

    /**
     * Generates or retrieves a DH key pair from the Android Keystore
     */
    private fun getOrCreateDHKeyPair(alias: String): java.security.KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setUserAuthenticationRequired(false)
                .build()
            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
        val publicKey = keyStore.getCertificate(alias).publicKey
        return java.security.KeyPair(publicKey, privateKey)
    }

    // Registers the broadcast receiver when the activity resumes
    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    // Unregisters the broadcast receiver when the activity pauses
    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    // Shuts down the executor to clean up background threads when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    // Shows a notification for incoming messages
    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "chat_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Chat Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val intent = Intent(context, ChatActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
