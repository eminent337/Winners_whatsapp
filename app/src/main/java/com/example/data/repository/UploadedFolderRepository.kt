package com.example.data.repository

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.model.Student
import com.example.data.model.UploadedReportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UploadedFolderRepository(
    context: Context,
    private val userId: String
) {
    private val appContext = context.applicationContext
    private val safeUserDirName = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }
    private val rootDir = File(appContext.filesDir, "uploaded_report_cards/$safeUserDirName").apply { mkdirs() }
    private val indexFile = File(rootDir, "folder_index.json")

    private val _files = MutableStateFlow<List<UploadedReportFile>>(emptyList())
    val files: StateFlow<List<UploadedReportFile>> = _files.asStateFlow()

    init {
        _files.value = loadIndexFromDisk()
    }

    suspend fun refreshFromDisk() = withContext(Dispatchers.IO) {
        _files.value = loadIndexFromDisk()
    }

    suspend fun importDocumentUris(
        uris: List<Uri>,
        targetFolderName: String,
        students: List<Student>
    ): Result<List<UploadedReportFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanFolder = sanitizeFolderName(targetFolderName)
            val folderDir = File(rootDir, cleanFolder).apply { mkdirs() }
            val imported = mutableListOf<UploadedReportFile>()
            val existing = loadIndexFromDisk().toMutableList()

            for (uri in uris) {
                val (displayName, _) = queryDisplayNameAndSize(uri)
                val resolvedMime = resolveMimeType(uri, displayName)
                if (!isSupportedReportMime(resolvedMime, displayName)) continue

                val safeName = ensureUniqueFileName(folderDir, sanitizeFileName(displayName))
                val destFile = File(folderDir, safeName)
                val copiedBytes = copyUriToFile(uri, destFile)
                if (copiedBytes <= 0L) {
                    destFile.delete()
                    continue
                }

                val matchedStudent = autoMatchStudent(displayName, students)
                val record = UploadedReportFile(
                    id = UUID.randomUUID().toString(),
                    fileName = displayName,
                    folderName = cleanFolder,
                    mimeType = resolvedMime,
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = copiedBytes,
                    uploadedAtMillis = System.currentTimeMillis(),
                    matchedStudentId = matchedStudent?.id.orEmpty(),
                    studentName = matchedStudent?.fullName.orEmpty(),
                    parentName = matchedStudent?.parentName.orEmpty(),
                    parentWhatsApp = matchedStudent?.parentWhatsApp.orEmpty()
                )
                imported.add(record)
                existing.add(0, record)
            }

            saveIndexToDisk(existing)
            _files.value = existing
            imported
        }
    }

    suspend fun importDeviceFolderTree(
        treeUri: Uri,
        fallbackFolderName: String,
        students: List<Student>
    ): Result<List<UploadedReportFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val folderDisplayName = queryDocumentName(treeDocUri)?.takeIf { it.isNotBlank() }
                ?: fallbackFolderName

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val childDocs = mutableListOf<Triple<Uri, String, String>>()

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "report_card" else "report_card"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (isSupportedReportMime(mime, name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        childDocs.add(Triple(docUri, name, resolveMimeType(docUri, name, mime)))
                    }
                }
            }

            val cleanFolder = sanitizeFolderName(folderDisplayName)
            val folderDir = File(rootDir, cleanFolder).apply { mkdirs() }
            val existing = loadIndexFromDisk().toMutableList()
            val imported = mutableListOf<UploadedReportFile>()

            for ((docUri, name, mime) in childDocs) {
                val safeName = ensureUniqueFileName(folderDir, sanitizeFileName(name))
                val destFile = File(folderDir, safeName)
                val copiedBytes = copyUriToFile(docUri, destFile)
                if (copiedBytes <= 0L) {
                    destFile.delete()
                    continue
                }
                val matchedStudent = autoMatchStudent(name, students)
                val record = UploadedReportFile(
                    id = UUID.randomUUID().toString(),
                    fileName = name,
                    folderName = cleanFolder,
                    mimeType = mime,
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = copiedBytes,
                    uploadedAtMillis = System.currentTimeMillis(),
                    matchedStudentId = matchedStudent?.id.orEmpty(),
                    studentName = matchedStudent?.fullName.orEmpty(),
                    parentName = matchedStudent?.parentName.orEmpty(),
                    parentWhatsApp = matchedStudent?.parentWhatsApp.orEmpty()
                )
                imported.add(record)
                existing.add(0, record)
            }

            saveIndexToDisk(existing)
            _files.value = existing
            imported
        }
    }

    suspend fun autoMatchUnmatched(students: List<Student>) = withContext(Dispatchers.IO) {
        if (students.isEmpty()) return@withContext
        val current = loadIndexFromDisk()
        var changed = false
        val updated = current.map { file ->
            if (file.hasAssignedRecipient) {
                file
            } else {
                val match = autoMatchStudent(file.fileName, students)
                if (match != null) {
                    changed = true
                    file.copy(
                        matchedStudentId = match.id,
                        studentName = match.fullName,
                        parentName = match.parentName,
                        parentWhatsApp = match.parentWhatsApp
                    )
                } else {
                    file
                }
            }
        }
        if (changed) {
            saveIndexToDisk(updated)
            _files.value = updated
        }
    }

    suspend fun assignRecipient(
        fileId: String,
        studentName: String,
        parentName: String,
        parentWhatsApp: String,
        studentId: String = ""
    ) = withContext(Dispatchers.IO) {
        val current = loadIndexFromDisk()
        val updated = current.map { file ->
            if (file.id == fileId) {
                file.copy(
                    matchedStudentId = studentId,
                    studentName = studentName.trim(),
                    parentName = parentName.trim(),
                    parentWhatsApp = parentWhatsApp.trim()
                )
            } else {
                file
            }
        }
        saveIndexToDisk(updated)
        _files.value = updated
    }

    suspend fun markDispatched(fileIds: List<String>) = withContext(Dispatchers.IO) {
        if (fileIds.isEmpty()) return@withContext
        val idSet = fileIds.toSet()
        val timestamp = SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date())
        val current = loadIndexFromDisk()
        val updated = current.map { file ->
            if (file.id in idSet) {
                file.copy(
                    whatsappStatus = "sent",
                    dispatchCount = file.dispatchCount + 1L,
                    lastSentAt = timestamp
                )
            } else {
                file
            }
        }
        saveIndexToDisk(updated)
        _files.value = updated
    }

    suspend fun moveFilesToFolder(fileIds: List<String>, targetFolderName: String) = withContext(Dispatchers.IO) {
        if (fileIds.isEmpty()) return@withContext
        val cleanFolder = sanitizeFolderName(targetFolderName)
        val destFolderDir = File(rootDir, cleanFolder).apply { mkdirs() }
        val idSet = fileIds.toSet()
        val current = loadIndexFromDisk()
        val updated = current.map { file ->
            if (file.id in idSet && file.folderName != cleanFolder) {
                val srcFile = File(file.localFilePath)
                val newDest = File(destFolderDir, ensureUniqueFileName(destFolderDir, sanitizeFileName(file.fileName)))
                val finalPath = if (srcFile.exists() && srcFile.renameTo(newDest)) {
                    newDest.absolutePath
                } else {
                    file.localFilePath
                }
                file.copy(folderName = cleanFolder, localFilePath = finalPath)
            } else {
                file
            }
        }
        saveIndexToDisk(updated)
        _files.value = updated
    }

    suspend fun deleteFiles(fileIds: List<String>) = withContext(Dispatchers.IO) {
        if (fileIds.isEmpty()) return@withContext
        val idSet = fileIds.toSet()
        val current = loadIndexFromDisk()
        val remaining = current.filter { file ->
            if (file.id in idSet) {
                runCatching { File(file.localFilePath).delete() }
                false
            } else {
                true
            }
        }
        saveIndexToDisk(remaining)
        _files.value = remaining
    }

    suspend fun generateSamplePdfReportCards(
        schoolName: String,
        academicTerm: String,
        principalSignature: String,
        students: List<Student>
    ): Result<List<UploadedReportFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanFolder = sanitizeFolderName(academicTerm)
            val folderDir = File(rootDir, cleanFolder).apply { mkdirs() }
            val existing = loadIndexFromDisk().toMutableList()
            val created = mutableListOf<UploadedReportFile>()

            val demoEntries = if (students.isNotEmpty()) {
                students.take(3).mapIndexed { index, stu ->
                    SamplePdfSpec(
                        studentId = stu.id,
                        studentName = stu.fullName,
                        gradeClass = stu.gradeClass,
                        parentName = stu.parentName,
                        parentWhatsApp = stu.parentWhatsApp,
                        average = 91 - index * 4,
                        grade = if (index == 0) "A+" else "A",
                        position = "${index + 1} of 28"
                    )
                }
            } else {
                listOf(
                    SamplePdfSpec("", "Kwame Mensah", "Grade 8 • Emerald", "Mrs. Abena Mensah", "+233244112233", 92, "A+", "1st of 28"),
                    SamplePdfSpec("", "Ama Osei-Bonsu", "Grade 8 • Emerald", "Mr. Kofi Osei-Bonsu", "+233208998877", 90, "A+", "2nd of 28"),
                    SamplePdfSpec("", "Nana Yaw Boateng", "Grade 9 • Gold", "Dr. Akosua Boateng", "+233547654321", 84, "A", "4th of 26")
                )
            }

            for (spec in demoEntries) {
                val baseName = "${spec.studentName.replace(" ", "_")}_ReportCard.pdf"
                val destFile = File(folderDir, ensureUniqueFileName(folderDir, baseName))
                writeSamplePdfReport(destFile, spec, schoolName, academicTerm, principalSignature)
                val record = UploadedReportFile(
                    id = UUID.randomUUID().toString(),
                    fileName = baseName,
                    folderName = cleanFolder,
                    mimeType = "application/pdf",
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = destFile.length(),
                    uploadedAtMillis = System.currentTimeMillis(),
                    matchedStudentId = spec.studentId,
                    studentName = spec.studentName,
                    parentName = spec.parentName,
                    parentWhatsApp = spec.parentWhatsApp
                )
                created.add(record)
                existing.add(0, record)
            }

            saveIndexToDisk(existing)
            _files.value = existing
            created
        }
    }

    private data class SamplePdfSpec(
        val studentId: String,
        val studentName: String,
        val gradeClass: String,
        val parentName: String,
        val parentWhatsApp: String,
        val average: Int,
        val grade: String,
        val position: String
    )

    private fun writeSamplePdfReport(
        destFile: File,
        spec: SamplePdfSpec,
        schoolName: String,
        academicTerm: String,
        principalSignature: String
    ) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#008069")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(32f, 32f, 563f, 138f), 12f, 12f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(schoolName.uppercase().take(36), 297.5f, 76f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8FDD2")
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TERMINAL ACADEMIC REPORT CARD • $academicTerm", 297.5f, 108f, subPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#54656F")
            textSize = 11f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111B21")
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawText("STUDENT: ${spec.studentName}", 44f, 180f, bodyPaint)
        canvas.drawText("CLASS: ${spec.gradeClass}", 340f, 180f, bodyPaint)
        canvas.drawText("PARENT / GUARDIAN: ${spec.parentName}", 44f, 206f, labelPaint)
        canvas.drawText("WHATSAPP: ${spec.parentWhatsApp}", 340f, 206f, labelPaint)

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D1D7DB")
            strokeWidth = 1f
        }
        canvas.drawLine(44f, 224f, 551f, 224f, dividerPaint)

        val subjects = listOf(
            Triple("Mathematics", spec.average + 2, "Distinction"),
            Triple("English Language", spec.average - 1, "Excellent"),
            Triple("Integrated Science", spec.average + 1, "Distinction"),
            Triple("Computing & ICT", spec.average + 3, "Distinction"),
            Triple("Social Studies", spec.average - 2, "Very Good")
        )
        var y = 264f
        canvas.drawText("SUBJECT", 48f, y, bodyPaint)
        canvas.drawText("SCORE", 340f, y, bodyPaint)
        canvas.drawText("REMARK", 430f, y, bodyPaint)
        y += 28f

        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111B21")
            textSize = 12f
        }
        for ((subj, sc, rem) in subjects) {
            val clamped = sc.coerceIn(50, 100)
            canvas.drawText(subj, 48f, y, normalPaint)
            canvas.drawText("$clamped%", 340f, y, bodyPaint)
            canvas.drawText(rem, 430f, y, normalPaint)
            y += 26f
            canvas.drawLine(44f, y - 14f, 551f, y - 14f, dividerPaint)
        }

        y += 30f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8FDD2")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(40f, y, 555f, y + 72f), 10f, 10f, boxPaint)
        canvas.drawText("OVERALL AVERAGE: ${spec.average}% (Grade ${spec.grade})", 56f, y + 30f, bodyPaint)
        canvas.drawText("CLASS STANDING: ${spec.position}", 56f, y + 54f, normalPaint)

        canvas.drawText("Signed: $principalSignature", 44f, 770f, bodyPaint)
        canvas.drawText("Generated for WhatsApp Parent Dispatch", 44f, 792f, labelPaint)

        pdf.finishPage(page)
        FileOutputStream(destFile).use { out -> pdf.writeTo(out) }
        pdf.close()
    }

    private fun autoMatchStudent(fileName: String, students: List<Student>): Student? {
        if (students.isEmpty()) return null
        val normalizedFile = fileName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), " ")
        val compactFile = normalizedFile.replace(" ", "")

        // First try exact student code match (e.g. GP-2026-101)
        students.firstOrNull { stu ->
            val code = stu.studentCode.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            code.length >= 4 && compactFile.contains(code)
        }?.let { return it }

        // Next match full name or all significant tokens (length >= 3)
        return students.firstOrNull { stu ->
            val tokens = stu.fullName.lowercase(Locale.ROOT)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 }
            tokens.isNotEmpty() && tokens.all { token -> normalizedFile.contains(token) }
        } ?: students.firstOrNull { stu ->
            // Fallback: match first + last name even if middle differs
            val tokens = stu.fullName.lowercase(Locale.ROOT)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 }
            tokens.size >= 2 && tokens.count { normalizedFile.contains(it) } >= 2
        }
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "ReportCard_${System.currentTimeMillis()}.pdf"
        var size = 0L
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIdx >= 0) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun queryDocumentName(docUri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun resolveMimeType(uri: Uri, fileName: String, explicitMime: String = ""): String {
        val raw = explicitMime.ifBlank { appContext.contentResolver.getType(uri).orEmpty() }
        if (raw.isNotBlank() && raw != "application/octet-stream") return raw
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) ||
                fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "application/pdf"
        }
    }

    private fun isSupportedReportMime(mimeType: String, fileName: String): Boolean {
        return mimeType.equals("application/pdf", ignoreCase = true) ||
            mimeType.startsWith("image/", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) ||
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".webp", ignoreCase = true)
    }

    private fun copyUriToFile(uri: Uri, destFile: File): Long {
        val input = appContext.contentResolver.openInputStream(uri) ?: return 0L
        var totalBytes = 0L
        input.use { inStream ->
            FileOutputStream(destFile).use { outStream ->
                val buffer = ByteArray(8192)
                var read = inStream.read(buffer)
                while (read != -1) {
                    outStream.write(buffer, 0, read)
                    totalBytes += read
                    read = inStream.read(buffer)
                }
            }
        }
        return totalBytes
    }

    private fun sanitizeFolderName(name: String): String {
        return name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "-").ifBlank { "Uploaded Reports" }.take(60)
    }

    private fun sanitizeFileName(name: String): String {
        return name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "report_card.pdf" }.take(120)
    }

    private fun ensureUniqueFileName(dir: File, desiredName: String): String {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return desiredName
        val base = desiredName.substringBeforeLast(".")
        val ext = desiredName.substringAfterLast(".", "")
        var counter = 2
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
            candidate = File(dir, nextName)
            counter++
        }
        return candidate.name
    }

    private fun loadIndexFromDisk(): List<UploadedReportFile> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val raw = indexFile.readText()
            val arr = JSONArray(raw)
            val result = mutableListOf<UploadedReportFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("localFilePath", "")
                if (path.isNotBlank() && File(path).exists()) {
                    result.add(
                        UploadedReportFile(
                            id = obj.optString("id"),
                            fileName = obj.optString("fileName"),
                            folderName = obj.optString("folderName", "Uploaded Reports"),
                            mimeType = obj.optString("mimeType", "application/pdf"),
                            localFilePath = path,
                            fileSizeBytes = obj.optLong("fileSizeBytes", File(path).length()),
                            uploadedAtMillis = obj.optLong("uploadedAtMillis", System.currentTimeMillis()),
                            matchedStudentId = obj.optString("matchedStudentId", ""),
                            studentName = obj.optString("studentName", ""),
                            parentName = obj.optString("parentName", ""),
                            parentWhatsApp = obj.optString("parentWhatsApp", ""),
                            whatsappStatus = obj.optString("whatsappStatus", "pending"),
                            dispatchCount = obj.optLong("dispatchCount", 0L),
                            lastSentAt = obj.optString("lastSentAt", "")
                        )
                    )
                }
            }
            result.sortedByDescending { it.uploadedAtMillis }
        }.getOrDefault(emptyList())
    }

    private fun saveIndexToDisk(items: List<UploadedReportFile>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("folderName", item.folderName)
                put("mimeType", item.mimeType)
                put("localFilePath", item.localFilePath)
                put("fileSizeBytes", item.fileSizeBytes)
                put("uploadedAtMillis", item.uploadedAtMillis)
                put("matchedStudentId", item.matchedStudentId)
                put("studentName", item.studentName)
                put("parentName", item.parentName)
                put("parentWhatsApp", item.parentWhatsApp)
                put("whatsappStatus", item.whatsappStatus)
                put("dispatchCount", item.dispatchCount)
                put("lastSentAt", item.lastSentAt)
            }
            arr.put(obj)
        }
        indexFile.writeText(arr.toString())
    }
}
