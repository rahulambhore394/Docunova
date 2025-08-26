package com.developer_rahul.docunova.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    data class TempFile(val file: File, val mimeType: String)

    fun copyUriToCache(context: Context, uri: Uri, defaultName: String = "scan.pdf"): TempFile {
        val resolver: ContentResolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else defaultName
            } else defaultName
        }

        val mime = resolver.getType(uri) ?: "application/pdf"
        val outFile = File(context.cacheDir, name ?: defaultName)

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }

        return TempFile(outFile, mime)
    }
}
