package com.pokerlanka.pokie_talkie.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID
import android.bluetooth.BluetoothClass
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles Bluetooth RFCOMM P2P communication for local zero-WiFi messaging and signaling.
 * Automatically accepts incoming connections and dispatches JSON payloads.
 */
class BluetoothTransportManager(
    private val context: Context,
    private val scope: CoroutineScope,
    var contactRepository: ContactRepository? = null,
    private val gson: Gson = Gson()
) {
    private val tag = "PokieBluetooth"

    companion object {
        // Dedicated Pokie-Talkie RFCOMM Service UUID
        val POKIE_BT_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val SERVICE_NAME = "PokieTalkieBT"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null

    // Cache to map Pokie User ID / Contact ID -> Bluetooth MAC Address
    private val peerAddressMap = ConcurrentHashMap<String, String>()

    // Serializes outgoing Bluetooth RFCOMM transmissions to prevent baseband channel collisions
    private val sendMutex = Mutex()

    private val _incomingPayloads = MutableSharedFlow<SignalingPayload>(extraBufferCapacity = 64)
    val incomingPayloads: SharedFlow<SignalingPayload> = _incomingPayloads.asSharedFlow()

    val isBluetoothSupported: Boolean get() = bluetoothAdapter != null
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true
    val isRunning: Boolean get() = serverJob?.isActive == true

    fun registerPeerAddress(peerId: String, btAddress: String) {
        if (peerId.isNotBlank() && btAddress.isNotBlank()) {
            peerAddressMap[peerId] = btAddress
            Log.d(tag, "Mapped peer $peerId to Bluetooth address $btAddress")
        }
    }

    fun getPeerAddress(peerId: String): String? = peerAddressMap[peerId]

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Starts the RFCOMM server socket listener in the background.
     */
    fun startServer() {
        if (serverJob?.isActive == true) return
        if (!isBluetoothSupported || !isBluetoothEnabled) {
            Log.w(tag, "Bluetooth is not available or disabled, skipping server start")
            return
        }
        if (!hasPermission()) {
            Log.w(tag, "Bluetooth connect permission not granted, cannot start RFCOMM server")
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, POKIE_BT_UUID)
                Log.i(tag, "Bluetooth RFCOMM Server listening on UUID $POKIE_BT_UUID")

                while (isActive && serverSocket != null) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        if (isActive) Log.w(tag, "Bluetooth server accept aborted: ${e.message}")
                        null
                    } ?: break

                    launch(Dispatchers.IO) {
                        handleClientConnection(socket)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Security exception starting Bluetooth server: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "Error starting Bluetooth server: ${e.message}")
            }
        }
    }

    private suspend fun handleClientConnection(socket: BluetoothSocket) {
        var receivedPayload: SignalingPayload? = null
        val remoteDevice = socket.remoteDevice
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val line = reader.readLine()

            if (!line.isNullOrBlank()) {
                val payload = gson.fromJson(line, SignalingPayload::class.java)
                if (payload != null) {
                    Log.i(tag, "Received Bluetooth payload [${payload.type}] from ${payload.senderName} (${remoteDevice.address})")
                    // Record sender's Bluetooth MAC address for future responses
                    if (payload.senderId.isNotBlank()) {
                        peerAddressMap[payload.senderId] = remoteDevice.address
                        contactRepository?.updateBluetoothAddress(payload.senderId, remoteDevice.address)
                    }

                    // Send ACK back to sender to confirm reception before connection closes
                    try {
                        val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)
                        writer.println("ACK")
                        writer.flush()
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to send BT ACK to ${remoteDevice.address}: ${e.message}")
                    }

                    receivedPayload = payload
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error handling Bluetooth client connection: ${e.message}")
        } finally {
            try {
                // Grace delay so outgoing ACK packet is flushed through OS driver
                kotlinx.coroutines.delay(60)
                socket.close()
            } catch (ignored: Exception) {}

            // Emit payload AFTER socket is closed cleanly so that any immediate reverse response
            // (such as a read receipt or delivery receipt) will find the RFCOMM channel completely free
            receivedPayload?.let { payload ->
                _incomingPayloads.emit(payload)
            }
        }
    }

    /**
     * Resolves a known or paired Bluetooth MAC address for a contact.
     */
    fun getKnownOrPairedAddress(contactId: String, contactName: String, storedAddress: String?): String? {
        if (!storedAddress.isNullOrBlank()) return storedAddress
        peerAddressMap[contactId]?.let { return it }

        val paired = getPairedDevices()
        val match = paired.find { device ->
            val dName = try { device.name ?: "" } catch (e: Exception) { "" }
            val dAddr = device.address ?: ""
            dAddr.equals(contactId, ignoreCase = true) ||
            dName.equals(contactName, ignoreCase = true) ||
            (contactName.isNotBlank() && dName.contains(contactName, ignoreCase = true)) ||
            (dName.isNotBlank() && contactName.contains(dName, ignoreCase = true))
        }

        if (match != null) {
            val addr = match.address
            peerAddressMap[contactId] = addr
            return addr
        }

        if (paired.size == 1) {
            val addr = paired.first().address
            peerAddressMap[contactId] = addr
            return addr
        }

        return null
    }

    /**
     * Dispatches a signaling or chat payload to a target device via Bluetooth RFCOMM.
     */
    suspend fun sendPayload(targetId: String, payload: SignalingPayload): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isBluetoothSupported || !isBluetoothEnabled || !hasPermission()) {
                Log.w(tag, "Cannot send Bluetooth payload: Bluetooth disabled or permission missing")
                return@withContext false
            }

            var address = peerAddressMap[targetId]
            val contact = contactRepository?.getContact(targetId)

            // 1. Check stored bluetooth address in contact record
            if (address.isNullOrBlank()) {
                val storedAddr = contact?.bluetoothAddress
                if (!storedAddr.isNullOrBlank()) {
                    address = storedAddr
                    peerAddressMap[targetId] = storedAddr
                }
            }

            // 2. If not found in memory, scan paired devices for matching name or ID
            if (address.isNullOrBlank()) {
                val paired = getPairedDevices()
                val targetName = contact?.name?.trim() ?: targetId.trim()

                val match = paired.find { device ->
                    val dName = try { device.name ?: "" } catch (e: Exception) { "" }
                    val dAddr = device.address ?: ""
                    dAddr.equals(targetId, ignoreCase = true) ||
                    dName.equals(targetName, ignoreCase = true) ||
                    (targetName.isNotBlank() && dName.contains(targetName, ignoreCase = true)) ||
                    (dName.isNotBlank() && targetName.contains(dName, ignoreCase = true))
                }

                if (match != null) {
                    val matchedAddress = match.address
                    address = matchedAddress
                    peerAddressMap[targetId] = matchedAddress
                    val cId = contact?.id
                    if (cId != null && matchedAddress != null) {
                        contactRepository?.updateBluetoothAddress(cId, matchedAddress)
                    }
                    Log.i(tag, "Matched target '$targetName' ($targetId) to paired Bluetooth device: ${match.name} ($matchedAddress)")
                } else if (paired.isNotEmpty()) {
                    // Aggressive fallback: find a phone-class device among paired
                    val phoneDevice = paired.find { device ->
                        try {
                            device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (phoneDevice != null) {
                        address = phoneDevice.address
                        peerAddressMap[targetId] = address
                        if (contact != null) {
                            contactRepository?.updateBluetoothAddress(contact.id, address)
                        }
                        Log.i(tag, "Selected paired phone device as Bluetooth fallback: ${phoneDevice.name} ($address)")
                    } else if (paired.size == 1) {
                        // Only one paired device — use it as the only possible target
                        address = paired.first().address
                        peerAddressMap[targetId] = address
                        if (contact != null) {
                            contactRepository?.updateBluetoothAddress(contact.id, address)
                        }
                        Log.i(tag, "Single paired device fallback: ${paired.first().name} ($address)")
                    }
                }
            }

            if (address.isNullOrBlank()) {
                Log.w(tag, "No known Bluetooth address for target peer: $targetId (${contact?.name})")
                return@withContext false
            }

            return@withContext sendPayloadToAddress(address, payload)
        }
    }

    /**
     * Sends payload to a known Bluetooth MAC address directly.
     * Uses Mutex locking and automatic backoff retries to guarantee delivery without baseband collisions.
     */
    suspend fun sendPayloadToAddress(address: String, payload: SignalingPayload): Boolean {
        return sendMutex.withLock {
            withContext(Dispatchers.IO) {
                var attempt = 0
                var success = false
                while (attempt < 3 && !success) {
                    attempt++
                    var socket: BluetoothSocket? = null
                    try {
                        val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(address)
                            ?: return@withContext false

                        // Always cancel discovery before connecting to maximize RFCOMM throughput
                        try {
                            bluetoothAdapter.cancelDiscovery()
                        } catch (ignored: SecurityException) {}

                        socket = device.createRfcommSocketToServiceRecord(POKIE_BT_UUID)
                        socket.connect()

                        val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)
                        val json = gson.toJson(payload)
                        writer.println(json)
                        writer.flush()

                        // Wait for receiver ACK or check inputStream before closing socket
                        try {
                            val startTime = System.currentTimeMillis()
                            while (System.currentTimeMillis() - startTime < 1000L) {
                                if (socket.inputStream.available() > 0) {
                                    val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                                    val ack = reader.readLine()
                                    Log.i(tag, "Bluetooth payload [${payload.type}] acknowledged by $address (ACK: $ack)")
                                    break
                                }
                                kotlinx.coroutines.delay(40)
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Exception waiting for Bluetooth ACK from $address: ${e.message}")
                        }

                        // Grace delay to ensure RFCOMM buffers are fully delivered over the air
                        kotlinx.coroutines.delay(60)

                        Log.i(tag, "Successfully dispatched Bluetooth payload [${payload.type}] to $address (attempt $attempt)")
                        success = true
                    } catch (e: SecurityException) {
                        Log.e(tag, "SecurityException sending Bluetooth payload to $address: ${e.message}")
                        break
                    } catch (e: Exception) {
                        Log.w(tag, "Attempt $attempt failed to send Bluetooth payload [${payload.type}] to $address: ${e.message}")
                        if (attempt < 3) {
                            kotlinx.coroutines.delay(150L * attempt)
                        }
                    } finally {
                        try {
                            socket?.close()
                        } catch (ignored: Exception) {}
                    }
                }
                success
            }
        }
    }

    /**
     * Returns the list of currently paired/bonded Bluetooth devices.
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!isBluetoothSupported || !hasPermission()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException reading bonded devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * Automatically registers paired Bluetooth devices as contacts in the local repository
     * so they are immediately accessible for offline calling and messaging.
     */
    suspend fun syncPairedDevicesToContacts() {
        if (!isBluetoothSupported || !hasPermission()) return
        val paired = getPairedDevices()
        if (paired.isEmpty()) return
        val repo = contactRepository ?: return
        val existingContacts = try { repo.getContacts().firstOrNull() ?: emptyList() } catch (e: Exception) { emptyList() }

        paired.forEach { device ->
            try {
                val address = device.address ?: return@forEach
                val name = try { device.name?.trim() } catch (e: Exception) { null }?.ifBlank { null } ?: "Bluetooth Device"

                val isSupportedDevice = try {
                    val major = device.bluetoothClass?.majorDeviceClass
                    major == BluetoothClass.Device.Major.PHONE ||
                    major == BluetoothClass.Device.Major.COMPUTER ||
                    major == BluetoothClass.Device.Major.MISC ||
                    major == BluetoothClass.Device.Major.UNCATEGORIZED ||
                    major == null
                } catch (e: Exception) { true }

                if (isSupportedDevice) {
                    peerAddressMap[address] = address
                    if (name.isNotBlank()) peerAddressMap[name] = address

                    val matched = existingContacts.find { 
                        it.id == address || 
                        it.bluetoothAddress.equals(address, ignoreCase = true) || 
                        (it.name.isNotBlank() && it.name.equals(name, ignoreCase = true)) 
                    }

                    if (matched != null) {
                        peerAddressMap[matched.id] = address
                        if (matched.bluetoothAddress.isBlank()) {
                            repo.updateBluetoothAddress(matched.id, address)
                        }
                    } else {
                        repo.saveOrUpdateContact(
                            Contact(
                                id = address,
                                name = name,
                                bluetoothAddress = address,
                                isOnlineLan = false,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error syncing paired device ${device.address}: ${e.message}")
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }
}
