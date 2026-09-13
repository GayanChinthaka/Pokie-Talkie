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
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles Bluetooth RFCOMM P2P communication for local zero-WiFi messaging and signaling.
 * Automatically accepts incoming connections and dispatches JSON payloads.
 */
class BluetoothTransportManager(
    private val context: Context,
    private val scope: CoroutineScope,
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
        try {
            val remoteDevice = socket.remoteDevice
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val line = reader.readLine()

            if (!line.isNullOrBlank()) {
                val payload = gson.fromJson(line, SignalingPayload::class.java)
                if (payload != null) {
                    Log.i(tag, "Received Bluetooth payload [${payload.type}] from ${payload.senderName} (${remoteDevice.address})")
                    // Record sender's Bluetooth MAC address for future responses
                    if (payload.senderId.isNotBlank()) {
                        peerAddressMap[payload.senderId] = remoteDevice.address
                    }
                    _incomingPayloads.emit(payload)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error handling Bluetooth client connection: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
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

            // If not found in memory, scan paired devices for matching name or ID
            if (address.isNullOrBlank()) {
                val paired = getPairedDevices()
                val match = paired.find { it.name.equals(targetId, ignoreCase = true) || it.address.equals(targetId, ignoreCase = true) }
                if (match != null) {
                    address = match.address
                    peerAddressMap[targetId] = address
                } else if (paired.size == 1) {
                    // If exactly 1 paired device exists, use it as fallback target
                    address = paired.first().address
                    peerAddressMap[targetId] = address
                }
            }

            if (address.isNullOrBlank()) {
                Log.w(tag, "No known Bluetooth address for target peer: $targetId")
                return@withContext false
            }

            return@withContext sendPayloadToAddress(address, payload)
        }
    }

    /**
     * Sends payload to a known Bluetooth MAC address directly.
     */
    suspend fun sendPayloadToAddress(address: String, payload: SignalingPayload): Boolean {
        return withContext(Dispatchers.IO) {
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

                Log.i(tag, "Successfully dispatched Bluetooth payload [${payload.type}] to $address")
                true
            } catch (e: SecurityException) {
                Log.e(tag, "SecurityException sending Bluetooth payload to $address: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(tag, "Failed to send Bluetooth payload to $address: ${e.message}")
                false
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
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

    fun stopServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }
}
