package com.example.data.model

import java.util.Locale

data class UploadedReportFile(
    val id: String,
    val fileName: String,
    val folderName: String,
    val mimeType: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val uploadedAtMillis: Long,
    val matchedStudentId: String = "",
    val studentName: String = "",
    val parentName: String = "",
    val parentWhatsApp: String = "",
    val whatsappStatus: String = "pending",
    val dispatchCount: Long = 0L,
    val lastSentAt: String = ""
) {
    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)

    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) ||
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".webp", ignoreCase = true)

    /** Zip / archive attachments. */
    val isZip: Boolean
        get() = mimeType.equals("application/zip", ignoreCase = true) ||
            mimeType.contains("compressed", ignoreCase = true) ||
            fileName.endsWith(".zip", ignoreCase = true) ||
            fileName.endsWith(".rar", ignoreCase = true) ||
            fileName.endsWith(".7z", ignoreCase = true)

    /** Word / Excel / PowerPoint / plain text documents. */
    val isDocument: Boolean
        get() = DocumentMime.isDocument(mimeType, fileName)

    /** Anything that is not a plain report card — i.e. a general attachment. */
    val isGeneralAttachment: Boolean
        get() = isZip || isDocument

    /** Short uppercase badge shown in the file row. */
    val typeBadge: String
        get() = when {
            isPdf -> "PDF"
            isImage -> "IMG"
            isZip -> "ZIP"
            fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> "DOC"
            fileName.endsWith(".xls", true) || fileName.endsWith(".xlsx", true) ||
                fileName.endsWith(".csv", true) -> "XLS"
            fileName.endsWith(".ppt", true) || fileName.endsWith(".pptx", true) -> "PPT"
            fileName.endsWith(".txt", true) -> "TXT"
            else -> "FILE"
        }

    val isSent: Boolean
        get() = whatsappStatus == "sent" || dispatchCount > 0L

    val hasAssignedRecipient: Boolean
        get() = parentWhatsApp.filter { it.isDigit() }.length >= 7

    val displayStudentOrFile: String
        get() = studentName.ifBlank {
            fileName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
                .ifBlank { fileName }
        }

    val formattedSize: String
        get() = when {
            fileSizeBytes < 1024L -> "$fileSizeBytes B"
            fileSizeBytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", fileSizeBytes / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
        }
}

fun buildUploadedFileWhatsAppCaption(
    file: UploadedReportFile,
    schoolName: String,
    academicTerm: String,
    principalSignature: String,
    customIntroNote: String = ""
): String {
    // If the teacher wrote their own note, send exactly that — no auto template.
    if (customIntroNote.isNotBlank()) return customIntroNote.trim()

    val greeting = if (file.parentName.isNotBlank()) {
        "Dear ${file.parentName},\nPlease find attached the official report card for ${file.displayStudentOrFile} from $schoolName ($academicTerm)."
    } else {
        "Dear Parent/Guardian,\nPlease find attached the official report card (${file.displayStudentOrFile}) from $schoolName ($academicTerm)."
    }

    return buildString {
        append(greeting)
        append("\n\nSigned: $principalSignature")
    }
}
