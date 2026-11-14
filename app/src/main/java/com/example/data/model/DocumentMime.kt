package com.example.data.model

/**
 * MIME detection for documents and archives that schools commonly share —
 * beyond the PDF/image report cards the app originally accepted.
 */
object DocumentMime {

    private val EXT_MAP = mapOf(
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "gz" to "application/gzip",
        "tar" to "application/x-tar",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "csv" to "text/csv",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "txt" to "text/plain",
        "rtf" to "application/rtf",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet"
    )

    /** True when the name or MIME type indicates a supported document/archive. */
    fun isDocument(mimeType: String, fileName: String): Boolean =
        mimeFromName(fileName) != null || isDocumentMime(mimeType)

    fun isDocumentMime(mimeType: String): Boolean {
        val m = mimeType.lowercase()
        if (m.isBlank() || m == "application/octet-stream") return false
        return m in EXT_MAP.values ||
            m.startsWith("application/vnd.openxmlformats") ||
            m.startsWith("application/vnd.ms-") ||
            m.startsWith("application/vnd.oasis.opendocument") ||
            m == "text/csv" || m == "text/plain"
    }

    /** Maps a filename extension to a MIME type, or null when unsupported. */
    fun mimeFromName(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == fileName.lowercase()) return null // no dot at all
        return EXT_MAP[ext]
    }

    /** Every MIME type / extension this app now accepts. */
    fun acceptedMimeTypes(): Array<String> = (
        EXT_MAP.values + listOf("image/*")
        ).distinct().toTypedArray()
}
