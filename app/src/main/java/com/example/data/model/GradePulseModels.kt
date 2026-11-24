package com.example.data.model

// Local-first models. Timestamps are epoch millis so the app can persist
// offline via SharedPreferences without any Google/Firebase dependency.
data class Student(
    val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val studentCode: String = "",
    val gradeClass: String = "",
    val parentName: String = "",
    val parentWhatsApp: String = "",
    val attendanceDays: Long = 0L,
    val totalTermDays: Long = 65L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val attendancePercentage: Int
        get() = if (totalTermDays > 0) ((attendanceDays * 100L) / totalTermDays).toInt().coerceIn(0, 100) else 0

    val formattedAttendance: String
        get() = "$attendanceDays / $totalTermDays Days ($attendancePercentage%)"
}

data class ReportCard(
    val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val gradeClass: String = "",
    val parentName: String = "",
    val parentWhatsApp: String = "",
    val schoolName: String = "",
    val academicTerm: String = "",
    val attendanceSummary: String = "",
    val subjectsSummary: String = "",
    val overallScore: Long = 0L,
    val overallGrade: String = "",
    val classPosition: String = "",
    val teacherRemarks: String = "",
    val principalSignature: String = "",
    val whatsappStatus: String = "pending",
    val dispatchCount: Long = 0L,
    val lastSentAt: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val parsedSubjects: List<SubjectGradeRow>
        get() = parseSubjectsSummary(subjectsSummary)
}

data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val threadKey: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val parentName: String = "",
    val parentWhatsApp: String = "",
    val direction: String = "outgoing",
    val messageText: String = "",
    val attachmentFileId: String = "",
    val attachmentFileName: String = "",
    val attachmentMimeType: String = "",
    val deliveryStatus: String = "sent",
    val sentTimeLabel: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ChatThread(
    val threadKey: String,
    val studentId: String,
    val studentName: String,
    val parentName: String,
    val parentWhatsApp: String,
    val lastMessageText: String,
    val lastAttachmentName: String,
    val lastTimeLabel: String,
    val lastDirection: String,
    val lastDeliveryStatus: String,
    val messageCount: Int,
    val lastTimestampSeconds: Long
)

fun normalizeThreadKey(parentWhatsApp: String, studentId: String = ""): String {
    val digits = parentWhatsApp.filter { it.isDigit() }
    return when {
        digits.length >= 6 -> digits.take(60)
        studentId.isNotBlank() -> "stu_${studentId.take(50)}"
        else -> "chat_${parentWhatsApp.hashCode().toUInt()}"
    }
}

/**
 * Normalizes a raw phone number into Ghanaian international format (`233XXXXXXXXX`).
 * Handles 10-digit local numbers starting with `0` (e.g. `0248907473` -> `+233248907473`).
 */
fun normalizeParentPhone(rawPhone: String): String {
    val trimmed = rawPhone.trim()
    if (trimmed.isEmpty()) return ""
    var digits = trimmed.filter { it.isDigit() }
    if (digits.startsWith("00") && digits.length > 11) {
        digits = digits.removePrefix("00")
    }
    if (digits.length == 13 && digits.startsWith("2330")) {
        digits = "233" + digits.substring(4)
    }
    if (digits.length == 10 && digits.startsWith("0")) {
        val prefix3 = digits.take(3)
        if (prefix3 in setOf("020", "023", "024", "025", "026", "027", "028", "050", "053", "054", "055", "056", "057", "058", "059")) {
            digits = "233" + digits.substring(1)
        }
    }
    return if (digits.startsWith("233") && digits.length >= 11) "+$digits" else trimmed
}

data class SubjectGradeRow(
    val subjectName: String,
    val score: Int,
    val grade: String = calculateLetterGrade(score),
    val remark: String = defaultRemarkForScore(score)
)

fun calculateLetterGrade(score: Int): String = when {
    score >= 90 -> "A+"
    score >= 80 -> "A"
    score >= 75 -> "B+"
    score >= 70 -> "B"
    score >= 65 -> "C+"
    score >= 60 -> "C"
    score >= 50 -> "D"
    else -> "F"
}

fun defaultRemarkForScore(score: Int): String = when {
    score >= 90 -> "Distinction"
    score >= 80 -> "Excellent"
    score >= 75 -> "Very Good"
    score >= 70 -> "Good"
    score >= 60 -> "Credit"
    score >= 50 -> "Pass"
    else -> "Needs Support"
}

fun encodeSubjectsSummary(rows: List<SubjectGradeRow>): String {
    return rows
        .filter { it.subjectName.isNotBlank() }
        .joinToString("\n") { row ->
            val cleanName = row.subjectName.replace("|", "-").replace("\n", " ").trim().take(45)
            val clampedScore = row.score.coerceIn(0, 100)
            val grade = calculateLetterGrade(clampedScore)
            val cleanRemark = row.remark.replace("|", "-").replace("\n", " ").trim().ifBlank {
                defaultRemarkForScore(clampedScore)
            }.take(45)
            "$cleanName|$clampedScore|$grade|$cleanRemark"
        }
        .take(1500)
        .ifBlank { "General Studies|85|A|Excellent" }
}

fun parseSubjectsSummary(summary: String): List<SubjectGradeRow> {
    if (summary.isBlank()) return emptyList()
    return summary.lines()
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val score = parts[1].trim().toIntOrNull()?.coerceIn(0, 100) ?: 0
                val grade = parts.getOrNull(2)?.trim()?.ifBlank { calculateLetterGrade(score) }
                    ?: calculateLetterGrade(score)
                val remark = parts.getOrNull(3)?.trim()?.ifBlank { defaultRemarkForScore(score) }
                    ?: defaultRemarkForScore(score)
                if (name.isNotEmpty()) SubjectGradeRow(name, score, grade, remark) else null
            } else {
                null
            }
        }
}

fun buildWhatsAppReportMessage(
    report: ReportCard,
    customIntroNote: String = ""
): String {
    val subjectsBlock = report.parsedSubjects.joinToString("\n") { row ->
        "• *${row.subjectName}*: ${row.score}% (${row.grade}) — _${row.remark}_"
    }
    val introLine = if (customIntroNote.isNotBlank()) {
        "${customIntroNote.trim()}\n\n"
    } else {
        "Dear *${report.parentName}*,\nPlease find the official terminal academic report card for *${report.studentName}* below:\n\n"
    }

    return buildString {
        append("🎓 *${report.schoolName.uppercase()}*\n")
        append("📋 *OFFICIAL REPORT CARD • ${report.academicTerm}*\n")
        append("━━━━━━━━━━━━━━━━━━━━\n\n")
        append(introLine)
        append("👤 *Student:* ${report.studentName} (${report.studentCode})\n")
        append("🏫 *Class:* ${report.gradeClass}\n")
        append("📅 *Attendance:* ${report.attendanceSummary}\n\n")
        append("📊 *SUBJECT PERFORMANCE*\n")
        append(subjectsBlock)
        append("\n\n")
        append("🏆 *Overall Average:* *${report.overallScore}% (${report.overallGrade})*\n")
        append("🥇 *Class Position:* ${report.classPosition}\n\n")
        append("💬 *Educator's Remarks:*\n\"${report.teacherRemarks}\"\n\n")
        append("✍️ *Signed:* ${report.principalSignature}\n")
        append("━━━━━━━━━━━━━━━━━━━━\n")
        append("_Please reply to this WhatsApp message to acknowledge receipt or schedule a parent-teacher conference._")
    }
}
