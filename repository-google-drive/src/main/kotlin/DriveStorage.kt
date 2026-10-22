/*package drive

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class DriveStorage(private val drive: Drive) {

    private val folderMime = "application/vnd.google-apps.folder"

    companion object {
        fun connect(credentialsFile: Path, tokensDirectory: Path): DriveStorage {
            val json = GsonFactory.getDefaultInstance()
            val transport = GoogleNetHttpTransport.newTrustedTransport()

            val secrets = Files.newBufferedReader(credentialsFile).use {
                GoogleClientSecrets.load(json, it)
            }
            val flow = GoogleAuthorizationCodeFlow.Builder(
                transport, json, secrets, listOf(DriveScopes.DRIVE)
            )
                .setDataStoreFactory(FileDataStoreFactory(tokensDirectory.toFile()))
                .setAccessType("offline")
                .build()

            val credential: Credential = AuthorizationCodeInstalledApp(
                flow, LocalServerReceiver.Builder().setPort(8888).build()
            ).authorize("user")

            val drive = Drive.Builder(transport, json, credential)
                .setApplicationName("Component Repository")
                .build()
            return DriveStorage(drive)
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    private fun children(parentId: String, extra: String = ""): List<File> {
        val result = mutableListOf<File>()
        var pageToken: String? = null
        do {
            val page = drive.files().list()
                .setQ("'$parentId' in parents and trashed = false $extra")
                .setFields("nextPageToken, files(id, name, mimeType)")
                .setPageSize(1000)
                .setPageToken(pageToken)
                .execute()
            result.addAll(page.files)
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    fun findFolder(parentId: String, name: String): String? =
        children(parentId, "and name = '${escape(name)}' and mimeType = '$folderMime'")
            .firstOrNull()?.id

    fun findFile(parentId: String, name: String): String? =
        children(parentId, "and name = '${escape(name)}' and mimeType != '$folderMime'")
            .firstOrNull()?.id

    fun listFolderNames(parentId: String): List<String> =
        children(parentId, "and mimeType = '$folderMime'").map { it.name }.sorted()

    fun listFiles(parentId: String): List<File> =
        children(parentId, "and mimeType != '$folderMime'").sortedBy { it.name }

    fun createFolder(parentId: String, name: String): String {
        val meta = File().setName(name).setMimeType(folderMime).setParents(listOf(parentId))
        return drive.files().create(meta).setFields("id").execute().id
    }

    fun ensureFolder(parentId: String, name: String): String =
        findFolder(parentId, name) ?: createFolder(parentId, name)

    fun uploadFile(parentId: String, file: Path) {
        val meta = File().setName(file.fileName.toString()).setParents(listOf(parentId))
        val content = FileContent("application/octet-stream", file.toFile())
        drive.files().create(meta, content).setFields("id").execute()
    }

    fun readText(fileId: String): String {
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toString(Charsets.UTF_8)
    }

    /** Pravi fajl ili prepisuje postojeci. */
    fun writeText(parentId: String, name: String, text: String) {
        val content = ByteArrayContent("text/plain", text.toByteArray(Charsets.UTF_8))
        val existing = findFile(parentId, name)
        if (existing == null) {
            val meta = File().setName(name).setParents(listOf(parentId))
            drive.files().create(meta, content).setFields("id").execute()
        } else {
            drive.files().update(existing, File(), content).execute()
        }
    }

    fun download(fileId: String, target: Path) {
        Files.newOutputStream(target).use { drive.files().get(fileId).executeMediaAndDownloadTo(it) }
    }

    fun delete(id: String) {
        drive.files().delete(id).execute()
    }
}*/