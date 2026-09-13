package com.pokerlanka.pokie_talkie.wifip2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Manages Wi-Fi Direct (Wi-Fi P2P) connections for zero-router high-bandwidth WebRTC HD calling & chat.
 */
class WifiDirectManager(
    private val context: Context,
    private val onPeerIpResolved: (peerIp: String, isGroupOwner: Boolean) -> Unit
) {
    private val tag = "PokieWifiDirect"

    private val p2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedIp = MutableStateFlow<String?>(null)
    val connectedIp: StateFlow<String?> = _connectedIp.asStateFlow()

    fun initialize() {
        if (p2pManager == null) {
            Log.w(tag, "Wi-Fi Direct not supported on this device")
            return
        }

        try {
            channel = p2pManager.initialize(context, Looper.getMainLooper(), null)
            registerReceiver()
            Log.i(tag, "WifiDirectManager initialized successfully")
        } catch (e: Exception) {
            Log.w(tag, "Failed to initialize WifiDirectManager: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onComplete: ((Boolean) -> Unit)? = null) {
        val ch = channel ?: return
        p2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(tag, "Wi-Fi Direct peer discovery started")
                onComplete?.invoke(true)
            }

            override fun onFailure(reason: Int) {
                Log.w(tag, "Wi-Fi Direct peer discovery failed: reason $reason")
                onComplete?.invoke(false)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onResult: ((Boolean) -> Unit)? = null) {
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            // Group owner intent: 0 (prefer client) to 15 (prefer owner). Balanced:
            this.groupOwnerIntent = 7
        }

        p2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(tag, "Initiated Wi-Fi Direct connection to $deviceAddress")
                onResult?.invoke(true)
            }

            override fun onFailure(reason: Int) {
                Log.e(tag, "Failed to connect Wi-Fi Direct to $deviceAddress: reason $reason")
                onResult?.invoke(false)
            }
        })
    }

    private fun registerReceiver() {
        if (receiver != null) return

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        Log.d(tag, "Wi-Fi P2P state changed: enabled=$enabled")
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val ch = channel ?: return
                        p2pManager?.requestPeers(ch) { peers: WifiP2pDeviceList? ->
                            val list = peers?.deviceList?.toList() ?: emptyList()
                            _discoveredPeers.value = list
                            Log.d(tag, "Wi-Fi Direct peers found: ${list.size}")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val networkInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }
                        if (networkInfo?.isConnected == true) {
                            val ch = channel ?: return
                            p2pManager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                                handleConnectionInfo(info)
                            }
                        } else {
                            _isConnected.value = false
                            _connectedIp.value = null
                            Log.d(tag, "Wi-Fi Direct disconnected")
                        }
                    }
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to register Wi-Fi Direct receiver: ${e.message}")
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return

        _isConnected.value = true
        val isOwner = info.isGroupOwner
        val ownerAddress: InetAddress? = info.groupOwnerAddress
        val ownerIp = ownerAddress?.hostAddress

        Log.i(tag, "Wi-Fi Direct group formed. isGroupOwner=$isOwner, ownerIp=$ownerIp")

        // In standard Wi-Fi Direct:
        // If we are NOT group owner, the peer is the group owner (ownerIp, usually 192.168.49.1)
        // If we ARE group owner, our IP is 192.168.49.1, and the client will connect to us or announce itself.
        if (!isOwner && !ownerIp.isNullOrBlank()) {
            _connectedIp.value = ownerIp
            onPeerIpResolved(ownerIp, false)
        } else if (isOwner) {
            // Group owner is self, client will connect to 192.168.49.1
            _connectedIp.value = "192.168.49.1"
            onPeerIpResolved("192.168.49.1", true)
        }
    }

    fun disconnect() {
        val ch = channel ?: return
        p2pManager?.removeGroup(ch, null)
        _isConnected.value = false
        _connectedIp.value = null
    }

    fun stop() {
        disconnect()
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (ignored: Exception) {}
        receiver = null
    }
}
