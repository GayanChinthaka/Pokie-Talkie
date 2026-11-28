package com.pokerlanka.pokie_talkie.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream

/**
 * SAF (Storage Access Framework) helper for listing and opening user-approved shared folders.
 */
class SafFileSystem(private val ctx: Context) {

    data class Entry(
        val id: String,
        val name: String,
        val isDir: Boolean,
        val size: Long
    )

    fun list(root: Uri): List<Entry> {
        val docId = DocumentsContract.getTreeDocumentId(root)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, docId)
        val out = mutableListOf<Entry>()
        ctx.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { c ->
            while (c.moveToNext()) {
                out += Entry(
                    id = c.getString(0),
                    name = c.getString(1) ?: "",
                    isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = c.getLong(3)
                )
            }
        }
        return out
    }

    fun open(root: Uri, docId: String): InputStream? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(root, docId)
        return ctx.contentResolver.openInputStream(uri)
    }
}
