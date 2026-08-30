package com.example.rezepte

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DriveServiceHelper(private val mDriveService: Drive) {

    suspend fun listRecipes(folderName: String = "Rezepte"): List<Recipe> = withContext(Dispatchers.IO) {
        // Wir suchen jetzt nach dem Ordner, egal wo er liegt
        val rootFolderId = findFolderId(folderName) ?: return@withContext emptyList()
        return@withContext getAllPdfsInFolderRecursive(rootFolderId, null)
    }

    private suspend fun getAllPdfsInFolderRecursive(folderId: String, currentCategory: String?): List<Recipe> {
        val result = mutableListOf<Recipe>()
        
        // 1. Get all PDFs in this folder
        var pageToken: String? = null
        try {
            do {
                val fileList: FileList = mDriveService.files().list()
                    .setQ("'$folderId' in parents and mimeType = 'application/pdf' and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, mimeType, webViewLink)")
                    .setPageToken(pageToken)
                    .execute()
                
                fileList.files?.forEach { file ->
                    result.add(Recipe(file.id, file.name, file.mimeType, file.webViewLink, currentCategory))
                }
                pageToken = fileList.nextPageToken
            } while (pageToken != null)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 2. Get all subfolders and recurse
        pageToken = null
        try {
            do {
                val folderList: FileList = mDriveService.files().list()
                    .setQ("'$folderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute()
                
                folderList.files?.forEach { subfolder ->
                    result.addAll(getAllPdfsInFolderRecursive(subfolder.id, subfolder.name))
                }
                pageToken = folderList.nextPageToken
            } while (pageToken != null)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        
        return result
    }

    suspend fun searchRecipes(query: String): List<Recipe> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Recipe>()
        try {
            val fileList: FileList = mDriveService.files().list()
                .setQ("mimeType = 'application/pdf' and trashed = false and (name contains '$query' or fullText contains '$query')")
                .setSpaces("drive")
                .setFields("files(id, name, mimeType, webViewLink)")
                .execute()

            fileList.files?.forEach { file ->
                result.add(Recipe(file.id, file.name, file.mimeType, file.webViewLink))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        result
    }

    private suspend fun findFolderId(folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            val result: FileList = mDriveService.files().list()
                .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false")
                .setSpaces("drive")
                .setFields("files(id)")
                .execute()
            
            return@withContext result.files?.firstOrNull()?.id
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
