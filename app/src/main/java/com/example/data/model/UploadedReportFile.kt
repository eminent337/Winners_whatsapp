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
    val greeting = if (customIntroNote.isNotBlank()) {
        customIntroNote.trim()
    } else if (file.parentName.isNotBlank()) {
        "Dear ${file.parentName},\nPlease find attached the official report card for ${file.displayStudentOrFile} from $schoolName ($academicTerm)."
    } else {
        "Dear Parent/Guardian,\nPlease find attached the official report card (${file.displayStudentOrFile}) from $schoolName ($academicTerm)."
    }

    return buildString {
        append("*${schoolName.uppercase()}*\n")
        append("Official Report Card • $academicTerm\n\n")
        append(greeting)
        append("\n\nSigned: $principalSignature")
    }
}
