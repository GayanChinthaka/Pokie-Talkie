package com.pokerlanka.pokie_talkie.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

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

    private val knownServices = ConcurrentHashMap<String, NsdServiceInfo>()
    private val serviceNameToUserId = ConcurrentHashMap<String, String>()
    private val userIdToServiceName = ConcurrentHashMap<String, String>()
    private val resolveChannel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolveWorkerJob: Job? = null
    private var unregisterDeferred: CompletableDeferred<Unit>? = null

    private var registrationAttempts = 0
    private var discoveryAttempts = 0

    private val _discoveredPeers = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, Contact>> = _discoveredPeers.asStateFlow()

    fun initialize(userId: String, userName: String, port: Int, fcmToken: String = "") {
        this.myUserId = userId
        this.myUserName = userName
        this.localPort = port
        this.myFcmToken = fcmToken
        startResolveWorker()
    }

    private fun startResolveWorker() {
        if (resolveWorkerJob == null || resolveWorkerJob?.isActive == false) {
            resolveWorkerJob = scope.launch(Dispatchers.IO) {
                for (service in resolveChannel) {
                    resolveServiceWithRetry(service)
                }
            }
        }
    }

    fun startDiscoveryAndRegistration() {
        startResolveWorker()
        acquireMulticastLock()
        registerService()
        startDiscovery()
    }

    fun updateProfileName(newName: String) {
        this.myUserName = newName
        scope.launch(Dispatchers.IO) {
            val def = CompletableDeferred<Unit>()
            unregisterDeferred = def
            unregisterService()
            withTimeoutOrNull(3000) {
                def.await()
            }
            unregisterDeferred = null
            registerService()
        }
    }

    var onPeerDiscovered: ((Contact) -> Unit)? = null

    fun updateFcmToken(newToken: String) {
        if (this.myFcmToken != newToken) {
            this.myFcmToken = newToken
            scope.launch(Dispatchers.IO) {
                val def = CompletableDeferred<Unit>()
                unregisterDeferred = def
                unregisterService()
                withTimeoutOrNull(3000) {
                    def.await()
                }
                unregisterDeferred = null
                registerService()
            }
        }
    }

    fun reResolvePeer(userId: String) {
        val serviceName = userIdToServiceName[userId]
        if (serviceName != null) {
            val info = knownServices[serviceName]
            if (info != null) {
                Log.i(tag, "Re-resolving peer $userId with known service $serviceName")
                resolveChannel.trySend(info)
            }
        }
    }

    fun stop() {
        stopDiscovery()
        unregisterService()
        resolveWorkerJob?.cancel()
        resolveWorkerJob = null
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
            if (myFcmToken.isNotBlank()) {
                setAttribute("fcmToken", myFcmToken)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registrationAttempts = 0
                registeredServiceName = info.serviceName
                Log.i(tag, "Local service registered: ${info.serviceName} on port $localPort")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service registration failed: Error code $errorCode")
                registrationListener = null
                if (registrationAttempts < 5) {
                    registrationAttempts++
                    scope.launch(Dispatchers.IO) {
                        delay(1500L * registrationAttempts)
                        registerService()
                    }
                }
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(tag, "Service unregistered: ${info.serviceName}")
                unregisterDeferred?.complete(Unit)
                unregisterDeferred = null
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
                discoveryAttempts = 0
                Log.d(tag, "NSD Service discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                if (service.serviceType.contains("pokietalkie") || service.serviceType == serviceType) {
                    if (service.serviceName == registeredServiceName) {
                        Log.d(tag, "Ignoring own service advertisement")
                        return
                    }
                    knownServices[service.serviceName] = service
                    resolveChannel.trySend(service)
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
                if (discoveryAttempts < 5) {
                    discoveryAttempts++
                    scope.launch(Dispatchers.IO) {
                        delay(1500L * discoveryAttempts)
                        startDiscovery()
                    }
                }
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

    private suspend fun resolveServiceWithRetry(serviceInfo: NsdServiceInfo) {
        var attempt = 0
        var resolved = false
        while (attempt < 3 && !resolved) {
            attempt++
            val deferred = CompletableDeferred<Boolean>()
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "Resolve failed for ${service.serviceName} (error $errorCode, attempt $attempt)")
                    deferred.complete(false)
                }

                override fun onServiceResolved(service: NsdServiceInfo) {
                    handleResolvedService(service)
                    deferred.complete(true)
                }
            }

            try {
                nsdManager.resolveService(serviceInfo, resolveListener)
                // Some devices never deliver either callback; without a timeout this single
                // worker would block and no later peer would ever be resolved.
                val success = withTimeoutOrNull(10_000L) { deferred.await() }
                if (success == null) {
                    Log.w(tag, "Resolve timed out for ${serviceInfo.serviceName} (attempt $attempt)")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        try {
                            nsdManager.stopServiceResolution(resolveListener)
                        } catch (e: Exception) {
                            Log.w(tag, "stopServiceResolution failed: ${e.message}")
                        }
                    }
                }
                if (success == true) {
                    resolved = true
                } else {
                    delay(600L * attempt)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error initiating resolveService: ${e.message}")
                delay(500L)
            }
        }
    }

    private fun handleResolvedService(service: NsdServiceInfo) {
        val host: InetAddress? = service.host
        val port = service.port
        val attributes = service.attributes ?: emptyMap()
        fun getAttr(key: String): String? {
            return attributes[key]?.let { String(it) } ?: attributes[key.lowercase()]?.let { String(it) }
        }

        val peerUserId = getAttr("userId") ?: service.serviceName.removePrefix("Pokie_")
        val peerUserName = getAttr("userName") ?: service.serviceName
        val peerFcmToken = getAttr("fcmToken") ?: ""
        val peerBtAddress = getAttr("btAddress") ?: ""
        val peerBtName = getAttr("btName") ?: ""

        if (peerUserId == myUserId) return

        val ipAddress = host?.hostAddress ?: return

        serviceNameToUserId[service.serviceName] = peerUserId
        userIdToServiceName[peerUserId] = service.serviceName
        knownServices[service.serviceName] = service

        Log.i(tag, "Resolved peer $peerUserName ($peerUserId) at $ipAddress:$port (FCM: ${peerFcmToken.take(15)}..., btAddress: $peerBtAddress, btName: $peerBtName)")

        scope.launch(Dispatchers.IO) {
            val byLan = contactRepository.getContact("lan_${ipAddress.replace(".", "_")}")
            val existing = contactRepository.getContact(peerUserId) ?: byLan
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val pairedMatch = try {
                btManager?.adapter?.bondedDevices?.find { device ->
                    val dName = try { device.name ?: "" } catch (e: Exception) { "" }
                    dName.equals(peerUserName, ignoreCase = true) ||
                    (peerBtName.isNotBlank() && dName.equals(peerBtName, ignoreCase = true))
                }
            } catch (e: SecurityException) {
                null
            }
            val resolvedBt = peerBtAddress.ifBlank { pairedMatch?.address ?: existing?.bluetoothAddress ?: "" }

            val updatedPeer = Contact(
                id = peerUserId,
                name = peerUserName.ifBlank { existing?.name ?: service.serviceName },
                fcmToken = peerFcmToken.ifBlank { existing?.fcmToken ?: "" },
                ipAddress = ipAddress,
                port = port,
                bluetoothAddress = resolvedBt,
                isOnlineLan = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )

            if (byLan != null && byLan.id != peerUserId) {
                Log.i(tag, "Merging manual LAN contact ${byLan.id} into resolved peer $peerUserId")
                contactRepository.mergeContactAndMigrateMessages(byLan.id, updatedPeer)
            } else {
                contactRepository.saveOrUpdateContact(updatedPeer)
            }
            contactRepository.updateLanStatus(peerUserId, true, ipAddress, port)
            if (resolvedBt.isNotBlank()) {
                contactRepository.updateBluetoothAddress(peerUserId, resolvedBt)
            }

            val currentMap = _discoveredPeers.value.toMutableMap()
            currentMap[peerUserId] = updatedPeer
            _discoveredPeers.value = currentMap

            onPeerDiscovered?.invoke(updatedPeer)
        }
    }

    private fun handleServiceLost(service: NsdServiceInfo) {
        val peerUserId = serviceNameToUserId.remove(service.serviceName)
            ?: service.serviceName.removePrefix("Pokie_")
        userIdToServiceName.remove(peerUserId)
        knownServices.remove(service.serviceName)

        scope.launch(Dispatchers.IO) {
            val currentMap = _discoveredPeers.value.toMutableMap()
            currentMap.remove(peerUserId)
            _discoveredPeers.value = currentMap

            contactRepository.updateLanOnlineStatusOnly(peerUserId, false)
        }
    }
}
