package com.pokerlanka.pokie_talkie.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.pokerlanka.pokie_talkie.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.roundToInt

data class PreparedMediaFile(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val messageType: MessageType
)

object MediaStorageHelper {
    const val MAX_MEDIA_BYTES = 100L * 1024L * 1024L // 100 MB cap
    const val MAX_AVATAR_BYTES = 256 * 1024 // 256 KB cap
    const val MAX_BT_AVATAR_BYTES = 24 * 1024 // 24 KB cap for Bluetooth RFCOMM JSON
    const val MAX_BT_STICKER_BYTES = 128 * 1024 // 128 KB cap if needed
    private const val AVATAR_MAX_DIM = 256
    private const val PHOTO_MAX_DIM = 2048
    private const val STREAM_BUFFER_SIZE = 32 * 1024

    fun avatarsDir(context: Context): File =
        File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }

    fun mediaDir(context: Context): File =
        File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    fun cameraCacheDir(context: Context): File =
        File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }

    fun createCameraCaptureUri(context: Context): Pair<Uri, File> {
        val file = File(cameraCacheDir(context), "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    suspend fun compressAndSaveMyAvatar(context: Context, sourceUri: Uri): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodeSampledBitmap(context, sourceUri, AVATAR_MAX_DIM) ?: return@withContext null
                val cropped = centerCropSquare(decoded)
                val scaled = if (cropped.width > AVATAR_MAX_DIM || cropped.height > AVATAR_MAX_DIM) {
                    Bitmap.createScaledBitmap(cropped, AVATAR_MAX_DIM, AVATAR_MAX_DIM, true)
                } else {
                    cropped
                }

                val baos = ByteArrayOutputStream()
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                scaled.compress(format, 80, baos)
                val bytes = baos.toByteArray()
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== decoded) cropped.recycle()
                decoded.recycle()

                val hash = shortSha256(bytes)
                val dir = avatarsDir(context)
                dir.listFiles()?.filter { it.name.startsWith("me_") }?.forEach { it.delete() }
                val outFile = File(dir, "me_$hash.webp")
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath to hash
            } catch (_: Exception) {
                null
            }
        }

    suspend fun savePeerAvatarBytes(
        context: Context,
        peerId: String,
        bytes: ByteArray,
        hashHint: String? = null
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return@withContext null
        try {
            val hash = hashHint?.takeIf { it.isNotBlank() } ?: shortSha256(bytes)
            val safePeerId = peerId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dir = avatarsDir(context)
            dir.listFiles()?.filter { it.name.startsWith("peer_${safePeerId}_") }?.forEach { it.delete() }
            val outFile = File(dir, "peer_${safePeerId}_$hash.webp")
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile.absolutePath to hash
        } catch (_: Exception) {
            null
        }
    }

    fun deletePeerAvatarFiles(context: Context, peerId: String) {
        val safePeerId = peerId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        avatarsDir(context).listFiles()
            ?.filter { it.name.startsWith("peer_${safePeerId}_") }
            ?.forEach { try { it.delete() } catch (_: Exception) {} }
    }

    fun deleteMyAvatarFiles(context: Context) {
        avatarsDir(context).listFiles()
            ?.filter { it.name.startsWith("me_") }
            ?.forEach { try { it.delete() } catch (_: Exception) {} }
    }

    suspend fun prepareOutgoingMedia(
        context: Context,
        uri: Uri,
        messageId: String,
        mimeTypeHint: String? = null,
        preferSticker: Boolean = false
    ): PreparedMediaFile? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val resolvedMime = (mimeTypeHint ?: cr.getType(uri) ?: "application/octet-stream").lowercase()
            val (rawName, rawSize) = queryDisplayNameAndSize(context, uri)
            val safeName = sanitizeFileName(rawName ?: defaultFileNameForMime(resolvedMime))

            val messageType = when {
                resolvedMime == "image/gif" -> MessageType.GIF
                preferSticker && resolvedMime.startsWith("image/") -> MessageType.STICKER
                resolvedMime == "image/webp" && rawSize in 1..(512 * 1024) -> MessageType.STICKER
                resolvedMime.startsWith("image/") -> MessageType.IMAGE
                else -> MessageType.FILE
            }

            // Downscale large non-animated photos to keep LAN transfers snappy and low-RAM
            if (messageType == MessageType.IMAGE && resolvedMime != "image/gif" && resolvedMime != "image/webp") {
                if (rawSize < 0 || rawSize > 900 * 1024L) {
                    val compressed = tryCompressPhoto(context, uri, messageId, safeName)
                    if (compressed != null) return@withContext compressed
                }
            }

            val outFile = File(mediaDir(context), "${messageId}_$safeName")
            var totalBytes = 0L
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_MEDIA_BYTES) {
                            output.close()
                            outFile.delete()
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext null

            if (totalBytes <= 0L) {
                outFile.delete()
                return@withContext null
            }

            PreparedMediaFile(
                file = outFile,
                fileName = safeName,
                mimeType = resolvedMime,
                fileSize = totalBytes,
                messageType = messageType
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun tryCompressPhoto(
        context: Context,
        uri: Uri,
        messageId: String,
        safeName: String
    ): PreparedMediaFile? {
        return try {
            val bmp = decodeSampledBitmap(context, uri, PHOTO_MAX_DIM) ?: return null
            val baseName = safeName.substringBeforeLast('.').ifBlank { "photo" } + ".jpg"
            val outFile = File(mediaDir(context), "${messageId}_$baseName")
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bmp.recycle()
            PreparedMediaFile(
                file = outFile,
                fileName = baseName,
                mimeType = "image/jpeg",
                fileSize = outFile.length(),
                messageType = MessageType.IMAGE
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val maxSide = maxOf(w, h)
                    if (maxSide > maxDim && maxDim > 0) {
                        val scale = maxDim.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val size = min(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return if (src.width == src.height) src else Bitmap.createBitmap(src, x, y, size, size)
    }

    fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        return name to size
    }

    fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('.', '_')
        return cleaned.ifBlank { "file_${System.currentTimeMillis()}" }.take(80)
    }

    private fun defaultFileNameForMime(mime: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: when {
            mime == "image/gif" -> "gif"
            mime == "image/webp" -> "webp"
            mime.startsWith("image/") -> "jpg"
            else -> "bin"
        }
        return "media_${System.currentTimeMillis()}.$ext"
    }

    fun shortSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${(bytes / 1024.0).roundToInt()} KB"
            else -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun openFileExternally(context: Context, filePath: String, mimeType: String?): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
