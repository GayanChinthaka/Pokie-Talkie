package com.pokerlanka.pokie_talkie.lan

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothTransportManager
import com.pokerlanka.pokie_talkie.wifip2p.WifiDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import android.net.wifi.WifiManager

enum class ActiveTransportMode {
    WIFI,
    WIFI_DIRECT,
    BLUETOOTH,
    OFFLINE
}

class ConnectivityMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val wifiDirectManager: WifiDirectManager,
    private val bluetoothTransportManager: BluetoothTransportManager
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val availableWifiNetworks = mutableSetOf<Network>()

    var onWifiLost: (() -> Unit)? = null
    var onBluetoothStateOn: (() -> Unit)? = null

    private val _activeTransport = MutableStateFlow(determineTransport())
    val activeTransport: StateFlow<ActiveTransportMode> = _activeTransport.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(availableWifiNetworks) {
                availableWifiNetworks.add(network)
            }
            updateTransport()
        }

        override fun onLost(network: Network) {
            val becameEmpty = synchronized(availableWifiNetworks) {
                availableWifiNetworks.remove(network)
                availableWifiNetworks.isEmpty()
            }
            updateTransport()
            if (becameEmpty) {
                onWifiLost?.invoke()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateTransport()
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    onBluetoothStateOn?.invoke()
                }
            }
            updateTransport()
        }
    }

    fun start() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            try {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            } catch (ignored: Exception) {}
        }

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                @Suppress("DEPRECATION")
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            context.registerReceiver(broadcastReceiver, filter)
        } catch (ignored: Exception) {}

        scope.launch {
            wifiDirectManager.isConnected.collect {
                updateTransport()
            }
        }

        updateTransport()
    }

    fun stop() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (ignored: Exception) {}
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (ignored: Exception) {}
    }

    private fun updateTransport() {
        _activeTransport.value = determineTransport()
    }

    private fun determineTransport(): ActiveTransportMode {
        if (wifiDirectManager.isConnected.value) {
            return ActiveTransportMode.WIFI_DIRECT
        }

        val hasWifiNetwork = synchronized(availableWifiNetworks) {
            availableWifiNetworks.isNotEmpty()
        }
        if (hasWifiNetwork) {
            return ActiveTransportMode.WIFI
        }

        val isWifiEnabled = try { wifiManager?.isWifiEnabled == true } catch (e: Exception) { false }
        if (isWifiEnabled) {
            val activeNet = connectivityManager?.activeNetwork
            if (activeNet != null) {
                val caps = connectivityManager?.getNetworkCapabilities(activeNet)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    return ActiveTransportMode.WIFI
                }
            }
            // Fallback for Android 9 / API 28 compatibility
            @Suppress("DEPRECATION")
            val netInfo = connectivityManager?.activeNetworkInfo
            @Suppress("DEPRECATION")
            if (netInfo?.isConnected == true && netInfo.type == ConnectivityManager.TYPE_WIFI) {
                return ActiveTransportMode.WIFI
            }
        }

        if (bluetoothTransportManager.isBluetoothEnabled) {
            return ActiveTransportMode.BLUETOOTH
        }

        return ActiveTransportMode.OFFLINE
    }
}
