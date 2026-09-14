package com.pokerlanka.pokie_talkie.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

class LanDiscoveryManager(
    private val context: Context,
    private val contactRepository: ContactRepository,
    private val scope: CoroutineScope
) {
    private val tag = "LanDiscoveryManager"
    private val serviceType = "_pokietalkie._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var registeredServiceName: String? = null
    private var myUserId: String = ""
    private var myUserName: String = ""
    private var myFcmToken: String = ""
    private var localPort: Int = 52220

    private val _discoveredPeers = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, Contact>> = _discoveredPeers.asStateFlow()

    fun initialize(userId: String, userName: String, port: Int, fcmToken: String = "") {
        this.myUserId = userId
        this.myUserName = userName
        this.localPort = port
        this.myFcmToken = fcmToken
    }

    fun startDiscoveryAndRegistration() {
        acquireMulticastLock()
        registerService()
        startDiscovery()
    }

    fun updateProfileName(newName: String) {
        this.myUserName = newName
        unregisterService()
        registerService()
    }

    fun updateFcmToken(newToken: String) {
        if (this.myFcmToken != newToken) {
            this.myFcmToken = newToken
            if (registrationListener != null) {
                unregisterService()
                registerService()
            }
        }
    }

    fun stop() {
        stopDiscovery()
        unregisterService()
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("pokie_multicast_lock").apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(tag, "MulticastLock acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error acquiring MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(tag, "MulticastLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing MulticastLock: ${e.message}")
        }
    }

    private fun registerService() {
        if (registrationListener != null) return

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val btAdapter = btManager?.adapter
        val btName = try { btAdapter?.name ?: "" } catch (e: Exception) { "" }
        val btAddress = try { btAdapter?.address ?: "" } catch (e: Exception) { "" }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Pokie_${myUserId}"
            serviceType = this@LanDiscoveryManager.serviceType
            port = localPort
            setAttribute("userId", myUserId)
            setAttribute("userName", myUserName)
            setAttribute("version", "1.0")
            if (btName.isNotBlank()) {
                setAttribute("btName", btName)
            }
            if (btAddress.isNotBlank() && btAddress != "02:00:00:00:00:00") {
                setAttribute("btAddress", btAddress)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                Log.i(tag, "Local service registered: ${info.serviceName} on port $localPort")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service registration failed: Error code $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(tag, "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service unregistration failed: Error code $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register NSD service: ${e.message}")
        }
    }

    private fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering service: ${e.message}")
            }
            registrationListener = null
        }
    }

    private fun startDiscovery() {
        if (discoveryListener != null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "NSD Service discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                if (service.serviceType.contains("pokietalkie") || service.serviceType == serviceType) {
                    if (service.serviceName == registeredServiceName) {
                        Log.d(tag, "Ignoring own service advertisement")
                        return
                    }
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
                handleServiceLost(service)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "NSD Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Start discovery failed with error: $errorCode")
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Stop discovery failed with error: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start discovering services: ${e.message}")
        }
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery: ${e.message}")
            }
            discoveryListener = null
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Resolve failed for ${service.serviceName}: Error $errorCode")
            }

            override fun onServiceResolved(service: NsdServiceInfo) {
                val host: InetAddress? = service.host
                val port = service.port
                val attributes = service.attributes

                val peerUserId = attributes["userId"]?.let { String(it) } ?: service.serviceName.removePrefix("Pokie_")
                val peerUserName = attributes["userName"]?.let { String(it) } ?: service.serviceName
                val peerBtAddress = attributes["btAddress"]?.let { String(it) } ?: ""

                if (peerUserId == myUserId) return

                val ipAddress = host?.hostAddress ?: return

                Log.i(tag, "Resolved peer $peerUserName ($peerUserId) at $ipAddress:$port [btAddress: $peerBtAddress]")

                scope.launch(Dispatchers.IO) {
                    val existing = contactRepository.getContact(peerUserId)
                    val resolvedBt = peerBtAddress.ifBlank { existing?.bluetoothAddress ?: "" }

                    val updatedPeer = Contact(
                        id = peerUserId,
                        name = peerUserName,
                        ipAddress = ipAddress,
                        port = port,
                        bluetoothAddress = resolvedBt,
                        isOnlineLan = true,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )

                    val currentMap = _discoveredPeers.value.toMutableMap()
                    currentMap[peerUserId] = updatedPeer
                    _discoveredPeers.value = currentMap

                    contactRepository.saveOrUpdateContact(updatedPeer)
                    contactRepository.updateLanStatus(peerUserId, true, ipAddress, port)
                    if (resolvedBt.isNotBlank()) {
                        contactRepository.updateBluetoothAddress(peerUserId, resolvedBt)
                    }
                }
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(tag, "Error initiating resolveService: ${e.message}")
        }
    }

    private fun handleServiceLost(service: NsdServiceInfo) {
        val peerUserId = service.serviceName.removePrefix("Pokie_")
        scope.launch(Dispatchers.IO) {
            val currentMap = _discoveredPeers.value.toMutableMap()
            currentMap.remove(peerUserId)
            _discoveredPeers.value = currentMap

            contactRepository.updateLanStatus(peerUserId, false)
        }
    }
}
