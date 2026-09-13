package com.pokerlanka.pokie_talkie.lan

import android.util.Log
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class LanSocketServer(
    private val scope: CoroutineScope,
    private val gson: Gson = Gson()
) {
    private val tag = "LanSocketServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var serverPort: Int = 52220
        private set

    private val _incomingPayloads = MutableSharedFlow<SignalingPayload>(extraBufferCapacity = 64)
    val incomingPayloads: SharedFlow<SignalingPayload> = _incomingPayloads.asSharedFlow()

    val isRunning: Boolean
        get() = serverJob?.isActive == true && serverSocket?.isClosed == false

    fun start(port: Int = 52220) {
        if (serverJob?.isActive == true) return
        this.serverPort = port

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(serverPort).apply {
                    reuseAddress = true
                }
                Log.i(tag, "Local socket server listening on port $serverPort")

                while (isActive && serverSocket?.isClosed == false) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClientConnection(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(tag, "ServerSocket error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val line = reader.readLine()
            if (!line.isNullOrBlank()) {
                val payload = gson.fromJson(line, SignalingPayload::class.java)
                if (payload != null) {
                    Log.d(tag, "Received direct LAN payload type: ${payload.type} from ${payload.senderName}")
                    _incomingPayloads.emit(payload)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling client connection: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun sendPayload(ip: String, port: Int, payload: SignalingPayload): Boolean {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2500)
                socket.soTimeout = 5000

                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
                val jsonString = gson.toJson(payload)
                writer.println(jsonString)
                writer.flush()
                Log.d(tag, "Direct LAN payload sent to $ip:$port [type: ${payload.type}]")
                true
            } catch (e: Exception) {
                Log.w(tag, "Failed to send direct LAN payload to $ip:$port (${e.message}). Falling back to FCM.")
                false
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing ServerSocket: ${e.message}")
        }
        serverSocket = null
    }
}
