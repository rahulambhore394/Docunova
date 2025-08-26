package com.developer_rahul.docunova

import android.content.Context
import android.net.Uri
import com.developer_rahul.docunova.Fragments.Home.DriveFileModel
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

object DriveServiceHelper {

    /** Build Google Drive service */
    fun buildService(context: Context, accountName: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccountName = accountName

        return Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            com.google.api.client.json.gson.GsonFactory(),
            credential
        ).setApplicationName("DocuNova")
            .build()
    }

    /** Create or return "DocuNova" app folder */
    suspend fun getOrCreateAppFolder(drive: Drive): String = withContext(Dispatchers.IO) {
        val folderName = "DocuNova"
        val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
        val result = drive.files().list().setQ(query).setSpaces("drive").execute()

        if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            val metadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }
            val folder = drive.files().create(metadata).execute()
            folder.id
        }
    }

    /** Upload a PDF file into "DocuNova" folder */
    suspend fun uploadFileToAppFolder(drive: Drive, context: Context, uri: Uri, fileName: String): String =
        withContext(Dispatchers.IO) {
            val folderId = getOrCreateAppFolder(drive)

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val inputStream = context.contentResolver.openInputStream(uri)
            val mediaContent = InputStreamContent("application/pdf", inputStream)

            val file = drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
            file.id
        }

    /** List files with thumbnails from "DocuNova" folder */
    suspend fun listFilesFromAppFolder(drive: Drive): List<DriveFileModel> = withContext(Dispatchers.IO) {
        val folderId = getOrCreateAppFolder(drive)
        val query = "'$folderId' in parents and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setFields("files(id, name, mimeType, size, thumbnailLink)")
            .execute()
        result.files.map {
            DriveFileModel(
                id = it.id,
                name = it.name,
                mimeType = it.mimeType,
                size = it.size?.toString() ?: "Unknown",
                thumbnailLink = it.thumbnailLink ?: ""
            )
        }
    }

    fun downloadFile(driveService: Drive, fileId: String, outputFile: java.io.File) {
        FileOutputStream(outputFile).use { outputStream: FileOutputStream ->
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
    }


}
