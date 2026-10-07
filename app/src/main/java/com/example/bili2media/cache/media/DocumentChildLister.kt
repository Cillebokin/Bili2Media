package com.example.bili2media.cache.media

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

fun interface DocumentChildLister {
    fun listChildren(directory: DocumentFile): List<DocumentFile>
}

internal class DocumentsContractDocumentChildLister(
    context: Context
) : DocumentChildLister {
    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver

    override fun listChildren(directory: DocumentFile): List<DocumentFile> {
        return runCatching {
            val directoryUri = directory.uri
            val directoryDocumentId = DocumentsContract.getDocumentId(directoryUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                directoryDocumentId
            )

            contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )?.use { cursor ->
                val documentIdColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                if (documentIdColumn < 0) {
                    return@use emptyList()
                }

                buildList {
                    while (cursor.moveToNext()) {
                        val childDocumentId = cursor.getString(documentIdColumn)
                            ?: continue
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(
                            directoryUri,
                            childDocumentId
                        )
                        DocumentFile.fromSingleUri(applicationContext, childUri)?.let(::add)
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
