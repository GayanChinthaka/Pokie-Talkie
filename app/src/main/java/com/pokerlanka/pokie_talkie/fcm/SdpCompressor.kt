package com.pokerlanka.pokie_talkie.fcm

import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SdpCompressor {
    private const val TAG = "SdpCompressor"
    private const val PREFIX = "gz:"

    /**
     * Compresses raw SDP string using GZIP and Base64 encoding.
     * WebRTC SDPs contain massive text redundancy and compress ~80% (from 4KB down to ~700B),
     * ensuring they easily fit within the Firebase Cloud Messaging 4KB data payload limit.
     */
    fun compress(rawSdp: String): String {
        if (rawSdp.isBlank() || rawSdp.startsWith(PREFIX)) return rawSdp
        return try {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(rawSdp.toByteArray(StandardCharsets.UTF_8))
            }
            val compressedBytes = bos.toByteArray()
            val encoded = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            val result = PREFIX + encoded
            Log.d(TAG, "Compressed SDP from ${rawSdp.length} chars to ${result.length} chars")
            result
        } catch (e: Exception) {
            Log.e(TAG, "SDP compression failed, returning raw SDP: ${e.message}")
            rawSdp
        }
    }

    /**
     * Decompresses an SDP string if it was compressed with [compress].
     */
    fun decompress(maybeCompressed: String): String {
        if (!maybeCompressed.startsWith(PREFIX)) return maybeCompressed
        return try {
            val stripped = maybeCompressed.removePrefix(PREFIX)
            val compressedBytes = Base64.decode(stripped, Base64.NO_WRAP)
            val bis = ByteArrayInputStream(compressedBytes)
            val decompressed = GZIPInputStream(bis).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            Log.d(TAG, "Decompressed SDP from ${maybeCompressed.length} chars to ${decompressed.length} chars")
            decompressed
        } catch (e: Exception) {
            Log.e(TAG, "SDP decompression failed: ${e.message}")
            maybeCompressed
        }
    }
}
