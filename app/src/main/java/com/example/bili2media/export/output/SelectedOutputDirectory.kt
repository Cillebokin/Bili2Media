package com.example.bili2media.export.output

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.bili2media.storage.OutputDirectoryStore

internal class SelectedOutputDirectory(context: Context) {
    private val appContext = context.applicationContext
    private val directoryStore = OutputDirectoryStore(appContext)

    fun existingFileNames(): Set<String>? {
        val directory = selectedDirectory() ?: return null
        return directory.listFiles()
            .asSequence()
            .filterNot(DocumentFile::isDirectory)
            .mapNotNull(DocumentFile::getName)
            .toSet()
    }

    fun createFile(displayName: String, mimeType: String): Uri? {
        val directory = selectedDirectory() ?: return null
        return directory.createFile(mimeType, displayName)?.uri
            ?: error("The selected output directory could not create a file")
    }

    fun deleteFile(uri: Uri) {
        DocumentFile.fromSingleUri(appContext, uri)?.delete()
    }

    private fun selectedDirectory(): DocumentFile? {
        val treeUri = directoryStore.currentTreeUri() ?: return null
        val directory = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("The selected output directory is unavailable")
        check(directory.isDirectory && directory.canWrite()) {
            "The selected output directory is not writable"
        }
        return directory
    }
}
