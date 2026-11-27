package com.pokerlanka.pokie_talkie.lan

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

enum class MediaTransferKind {
    MEDIA,
    AVATAR
}

data class MediaTransferHeader(
    val kind: MediaTransferKind,
    val senderId: String,
    val senderName: String,
    val messageId: String? = null,
    val messageType: MessageType? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val avatarHash: String? = null,
    val avatarColor: Long? = null
)

data class IncomingMediaTransfer(
    val header: MediaTransferHeader,
    val savedFile: File,
    val sourceIp: String?
)

/**
 * Streaming binary LAN TCP transfer server/client (port 52221).
 * Streams files in 32 KB chunks directly between socket and disk so RAM stays flat.
 */
class LanMediaServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson()
) {
    private val tag = "LanMediaServer"
    var mediaPort: Int = DEFAULT_MEDIA_PORT
        private set

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeTransfers = AtomicInteger(0)

    private val _incomingTransfers = MutableSharedFlow<IncomingMediaTransfer>(extraBufferCapacity = 32)
    val incomingTransfers: SharedFlow<IncomingMediaTransfer> = _incomingTransfers.asSharedFlow()

    val isRunning: Boolean
        get() = serverJob?.isActive == true && serverSocket?.isClosed == false

    fun start(port: Int = DEFAULT_MEDIA_PORT) {
        if (serverJob?.isActive == true) return
        this.mediaPort = port

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(mediaPort).apply { reuseAddress = true }
                Log.i(tag, "LAN Media Server listening on port $mediaPort")

                while (isActive && serverSocket?.isClosed == false) {
                    val client = serverSocket?.accept() ?: break
                    if (activeTransfers.get() >= MAX_CONCURRENT_TRANSFERS) {
                        Log.w(tag, "Too many concurrent media transfers, dropping connection")
                        try { client.close() } catch (_: Exception) {}
                        continue
                    }
                    activeTransfers.incrementAndGet()
                    launch(Dispatchers.IO) {
                        try {
                            handleClient(client)
                        } finally {
                            activeTransfers.decrementAndGet()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(tag, "Media server error: ${e.message}")
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
                serverSocket = null
                serverJob = null
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val remoteIp = socket.inetAddress?.hostAddress
        var targetFile: File? = null
        try {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream(), BUFFER_SIZE)
            val headerLine = readHeaderLine(input) ?: return
            val header = gson.fromJson(headerLine, MediaTransferHeader::class.java) ?: return
            if (header.senderId.isBlank() || header.fileSize <= 0L) return

            val maxAllowed = when (header.kind) {
                MediaTransferKind.AVATAR -> MediaStorageHelper.MAX_AVATAR_BYTES.toLong()
                MediaTransferKind.MEDIA -> MediaStorageHelper.MAX_MEDIA_BYTES
            }
            if (header.fileSize > maxAllowed) {
                Log.w(tag, "Rejected oversized ${header.kind} (${header.fileSize} bytes) from $remoteIp")
                return
            }

            targetFile = when (header.kind) {
                MediaTransferKind.AVATAR -> {
                    val safePeer = header.senderId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val hash = header.avatarHash?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
                    val dir = MediaStorageHelper.avatarsDir(context)
                    dir.listFiles()?.filter { it.name.startsWith("peer_${safePeer}_") }?.forEach { it.delete() }
                    File(dir, "peer_${safePeer}_$hash.webp")
                }
                MediaTransferKind.MEDIA -> {
                    val msgId = header.messageId?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
                    val safeName = MediaStorageHelper.sanitizeFileName(header.fileName ?: "file")
                    File(MediaStorageHelper.mediaDir(context), "${msgId}_$safeName")
                }
            }

            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = header.fileSize
            FileOutputStream(targetFile).use { out ->
                while (remaining > 0L) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) {
                        throw java.io.EOFException("Unexpected EOF during ${header.kind} transfer (remaining=$remaining)")
                    }
                    out.write(buffer, 0, read)
                    remaining -= read
                }
                out.flush()
            }

            // Send ACK after file is safely persisted
            val output = socket.getOutputStream()
            output.write("OK\n".toByteArray(Charsets.UTF_8))
            output.flush()

            Log.i(tag, "Received ${header.kind} (${header.fileSize} bytes) from ${header.senderName} ($remoteIp)")
            _incomingTransfers.emit(IncomingMediaTransfer(header, targetFile, remoteIp))
        } catch (e: Exception) {
            Log.w(tag, "Media receive failed from $remoteIp: ${e.message}")
            try { targetFile?.delete() } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readHeaderLine(input: BufferedInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else bytes.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) {
                return bytes.toString(Charsets.UTF_8.name()).trimEnd('\r')
            }
            bytes.write(b)
            if (bytes.size() > MAX_HEADER_BYTES) {
                throw java.io.IOException("Media header exceeded $MAX_HEADER_BYTES bytes")
            }
        }
    }

    suspend fun sendFile(
        ip: String,
        port: Int = DEFAULT_MEDIA_PORT,
        header: MediaTransferHeader,
        file: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) return@withContext false
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 3500)
            socket.soTimeout = 30_000

            val out = BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE)
            val headerBytes = (gson.toJson(header) + "\n").toByteArray(Charsets.UTF_8)
            out.write(headerBytes)

            val buffer = ByteArray(BUFFER_SIZE)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
            out.flush()

            val ack = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
            val ok = ack == "OK"
            Log.d(tag, "Sent ${header.kind} (${file.length()} bytes) to $ip:$port -> ack=$ack")
            ok
        } catch (e: Exception) {
            Log.w(tag, "Failed sending ${header.kind} to $ip:$port: ${e.message}")
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    companion object {
        const val DEFAULT_MEDIA_PORT = 52221
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_CONCURRENT_TRANSFERS = 4

        internal fun validateHeader(header: MediaTransferHeader): Boolean {
            if (header.senderId.isBlank() || header.fileSize <= 0L) return false
            val cap = when (header.kind) {
                MediaTransferKind.AVATAR -> MediaStorageHelper.MAX_AVATAR_BYTES.toLong()
                MediaTransferKind.MEDIA -> MediaStorageHelper.MAX_MEDIA_BYTES
            }
            return header.fileSize <= cap
        }
    }
}
