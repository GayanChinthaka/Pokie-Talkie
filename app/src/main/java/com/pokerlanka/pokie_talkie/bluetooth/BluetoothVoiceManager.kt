package com.pokerlanka.pokie_talkie.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Handles low-latency, router-free, full-duplex Walkie-Talkie voice streaming over Bluetooth RFCOMM.
 */
class BluetoothVoiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val tag = "PokieBtVoice"

    companion object {
        // Dedicated RFCOMM UUID for real-time audio voice stream
        val POKIE_VOICE_UUID: UUID = UUID.fromString("fa87c0d1-afac-11de-8a39-0800200c9a66")
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 640 // 20ms of 16kHz 16-bit audio
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var activeSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null

    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var serverJob: Job? = null

    var isCallActive: Boolean = false
        private set

    var isMuted: Boolean = false

    var isBluetoothCallActiveProvider: (() -> Boolean)? = null

    val isRunning: Boolean
        get() = serverJob?.isActive == true

    private var currentSessionToken: Long = 0L

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Starts listening for incoming Bluetooth voice call streams.
     */
    @SuppressLint("MissingPermission")
    fun startVoiceServer() {
        if (serverJob?.isActive == true || bluetoothAdapter == null) return
        if (!hasPermission()) {
            Log.w(tag, "Bluetooth connect permission not granted, skipping voice server start")
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("PokieVoice", POKIE_VOICE_UUID)
                Log.i(tag, "Bluetooth Voice Server listening on UUID $POKIE_VOICE_UUID")

                while (isActive && serverSocket != null) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    if (isBluetoothCallActiveProvider?.invoke() != true) {
                        Log.w(tag, "Rejected incoming Bluetooth voice connection from ${socket.remoteDevice.address}: call not active/connected")
                        try { socket.close() } catch (ignored: Exception) {}
                        continue
                    }

                    Log.i(tag, "Accepted incoming Bluetooth voice connection from ${socket.remoteDevice.address}")
                    startDuplexAudio(socket)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error in Bluetooth voice server: ${e.message}")
            } finally {
                try {
                    serverSocket?.close()
                } catch (ignored: Exception) {}
                serverSocket = null
                serverJob = null
            }
        }
    }

    /**
     * Connects an outgoing Bluetooth voice call stream to a remote device.
     */
    @SuppressLint("MissingPermission")
    fun startOutgoingCall(targetAddress: String): Boolean {
        if (bluetoothAdapter == null) return false

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(targetAddress) ?: return false
            bluetoothAdapter.cancelDiscovery()

            val socket = device.createRfcommSocketToServiceRecord(POKIE_VOICE_UUID)
            socket.connect()
            Log.i(tag, "Connected outgoing Bluetooth voice stream to $targetAddress")

            startDuplexAudio(socket)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect outgoing Bluetooth voice call to $targetAddress: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDuplexAudio(socket: BluetoothSocket) {
        stopDuplexAudio()
        activeSocket = socket
        isCallActive = true
        val token = System.currentTimeMillis()
        currentSessionToken = token

        val minRecordBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val recordBufSize = maxOf(minRecordBuf, BUFFER_SIZE * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                recordBufSize
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to instantiate AudioRecord: ${e.message}")
        }

        val minTrackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val trackBufSize = maxOf(minTrackBuf, BUFFER_SIZE * 4)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .setEncoding(ENCODING)
            .build()

        try {
            audioTrack = AudioTrack(
                attributes,
                format,
                trackBufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioTrack: ${e.message}")
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord recording: ${e.message}")
        }

        // Recording & Sending Loop
        recordJob = scope.launch(Dispatchers.IO) {
            val outputStream: OutputStream = try {
                socket.outputStream
            } catch (e: Exception) {
                return@launch
            }

            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive && isCallActive && currentSessionToken == token) {
                val readBytes = try {
                    audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: -1
                } catch (e: Exception) {
                    -1
                }
                if (readBytes > 0) {
                    if (isMuted) {
                        buffer.fill(0)
                    }
                    try {
                        outputStream.write(buffer, 0, readBytes)
                        outputStream.flush()
                    } catch (e: Exception) {
                        Log.w(tag, "Voice send socket error: ${e.message}")
                        break
                    }
                }
            }
        }

        // Receiving & Playback Loop
        playJob = scope.launch(Dispatchers.IO) {
            val inputStream: InputStream = try {
                socket.inputStream
            } catch (e: Exception) {
                return@launch
            }

            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive && isCallActive && currentSessionToken == token) {
                val readBytes = try {
                    inputStream.read(buffer)
                } catch (e: Exception) {
                    -1
                }
                if (readBytes > 0) {
                    audioTrack?.write(buffer, 0, readBytes)
                } else if (readBytes == -1) {
                    Log.i(tag, "Remote party closed voice stream")
                    break
                }
            }
            stopDuplexAudio(token)
        }
    }

    fun stopDuplexAudio(sessionToken: Long? = null) {
        if (sessionToken != null && sessionToken != currentSessionToken) {
            Log.d(tag, "Ignoring stopDuplexAudio for outdated session token $sessionToken (current: $currentSessionToken)")
            return
        }
        isCallActive = false
        recordJob?.cancel()
        recordJob = null
        playJob?.cancel()
        playJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {}
        audioTrack = null

        try {
            activeSocket?.close()
        } catch (ignored: Exception) {}
        activeSocket = null
    }

    fun setSpeakerphone(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
    }

    fun stop() {
        stopDuplexAudio()
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }
}
