package app.infinitum.nether.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Signature-only file pipe for primary, decoy, and meta vault blobs. */
class VaultBlobProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = file(uri)
        if (!file.exists()) {
            if (!mode.contains("w") && !mode.contains("t")) return null
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val bits = if (mode.contains("w")) {
            var flags = ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            if (mode.contains("t")) flags = flags or ParcelFileDescriptor.MODE_TRUNCATE
            flags
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, bits)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return if (file(uri).delete()) 1 else 0
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: VaultFiles.PRIMARY
        return File(context!!.filesDir, "nether/$name.vault")
    }
}
