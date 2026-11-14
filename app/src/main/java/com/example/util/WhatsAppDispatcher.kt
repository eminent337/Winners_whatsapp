package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.ReportCard
import com.example.data.model.UploadedReportFile
import com.example.data.model.buildUploadedFileWhatsAppCaption
import com.example.data.model.buildWhatsAppReportMessage
import com.example.service.WhatsAppDispatchService
import com.example.service.WhatsAppSecurityValidator
import java.io.File
import java.io.FileOutputStream

object WhatsAppDispatcher {

    /**
     * Normalizes a phone number into E.164 digits for WhatsApp JID & deep links.
     */
    fun normalizeWhatsAppDigits(rawPhone: String): String {
        return WhatsAppSecurityValidator.normalizeE164Digits(rawPhone)
            ?: rawPhone.filter { it.isDigit() }
    }

    /**
     * Checks if WhatsApp or WhatsApp Business is installed on the device.
     */
    fun resolveInstalledWhatsAppPackage(context: Context): String? {
        return WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
    }

    /**
     * Passes structured student report card data through [WhatsAppDispatchService] to open
     * the parent's WhatsApp chat pre-filled with the formatted report card.
     */
    fun launchWhatsAppDirectText(
        context: Context,
        report: ReportCard,
        customIntroNote: String = ""
    ): Boolean {
        val message = buildWhatsAppReportMessage(report, customIntroNote)
        return WhatsAppDispatchService.startTextChatDispatch(
            context = context,
            parentWhatsApp = report.parentWhatsApp,
            parentName = report.parentName,
            messageText = message,
            subjectLabel = "Report Card: ${report.studentName}"
        )
    }

    /**
     * Passes a direct parent message through [WhatsAppDispatchService] pre-filled in the parent's chat.
     */
    fun launchWhatsAppDirectPlainText(
        context: Context,
        parentWhatsApp: String,
        parentName: String,
        message: String
    ): Boolean {
        return WhatsAppDispatchService.startTextChatDispatch(
            context = context,
            parentWhatsApp = parentWhatsApp,
            parentName = parentName,
            messageText = message,
            subjectLabel = "Message for $parentName"
        )
    }

    /**
     * Renders an official high-resolution School Report Card Certificate PNG image into the
     * authorized cache directory and dispatches it securely via [WhatsAppDispatchService].
     */
    fun shareReportCardCertificateImage(
        context: Context,
        report: ReportCard,
        customIntroNote: String = ""
    ): Boolean {
        return try {
            val bitmap = renderReportCardCertificateBitmap(report)
            val cacheFolder = File(context.cacheDir, "report_cards").apply { mkdirs() }
            val safeFileName = "ReportCard_${report.studentCode.replace(Regex("[^a-zA-Z0-9]"), "_")}.png"
            val imageFile = File(cacheFolder, safeFileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val caption = buildWhatsAppReportMessage(report, customIntroNote)
            WhatsAppDispatchService.startReportFileDispatch(
                context = context,
                filePath = imageFile.absolutePath,
                mimeType = "image/png",
                caption = caption,
                parentWhatsApp = report.parentWhatsApp,
                parentName = report.parentName,
                subjectLabel = "Official Report Card: ${report.studentName}"
            )
        } catch (_: Exception) {
            false
        }
    }

    fun copyReportMessageToClipboard(
        context: Context,
        report: ReportCard,
        customIntroNote: String = ""
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val message = buildWhatsAppReportMessage(report, customIntroNote)
        clipboard.setPrimaryClip(ClipData.newPlainText("Report Card - ${report.studentName}", message))
    }

    /**
     * Validates and sends an uploaded PDF or image report card through [WhatsAppDispatchService]
     * pre-filled for the parent's WhatsApp chat (`jid`).
     */
    fun launchWhatsAppWithUploadedFile(
        context: Context,
        file: UploadedReportFile,
        schoolName: String,
        academicTerm: String,
        principalSignature: String,
        customIntroNote: String = ""
    ): Boolean {
        val caption = buildUploadedFileWhatsAppCaption(
            file = file,
            schoolName = schoolName,
            academicTerm = academicTerm,
            principalSignature = principalSignature,
            customIntroNote = customIntroNote
        )
        val mimeType = file.mimeType.ifBlank { if (file.isPdf) "application/pdf" else "image/*" }
        return WhatsAppDispatchService.startReportFileDispatch(
            context = context,
            filePath = file.localFilePath,
            mimeType = mimeType,
            caption = caption,
            parentWhatsApp = file.parentWhatsApp,
            parentName = file.parentName.ifBlank { file.displayStudentOrFile },
            subjectLabel = "Report Card: ${file.displayStudentOrFile}"
        )
    }

    /**
     * Validates and shares multiple selected report card files together through [WhatsAppDispatchService].
     * If all selected files target the same parent WhatsApp number, pre-fills that chat automatically.
     */
    fun shareMultipleFilesToWhatsApp(
        context: Context,
        files: List<UploadedReportFile>,
        schoolName: String,
        academicTerm: String,
        principalSignature: String,
        customIntroNote: String = ""
    ): Boolean {
        if (files.isEmpty()) return false
        if (files.size == 1) {
            return launchWhatsAppWithUploadedFile(
                context = context,
                file = files.first(),
                schoolName = schoolName,
                academicTerm = academicTerm,
                principalSignature = principalSignature,
                customIntroNote = customIntroNote
            )
        }

        val validPaths = files.mapNotNull { item ->
            WhatsAppSecurityValidator.resolveAuthorizedReportFile(context, item.localFilePath)?.absolutePath
        }
        if (validPaths.isEmpty()) return false

        val mimeType = when {
            files.all { it.isPdf } -> "application/pdf"
            files.all { it.isImage } -> "image/*"
            else -> "*/*"
        }
        val caption = buildString {
            append("*${schoolName.uppercase()}*\n")
            append("Official Report Cards (${validPaths.size}) • $academicTerm\n")
            if (customIntroNote.isNotBlank()) {
                append("\n${customIntroNote.trim()}\n")
            }
            append("\nSigned: $principalSignature")
        }

        val uniqueNumbers = files.mapNotNull {
            WhatsAppSecurityValidator.normalizeE164Digits(it.parentWhatsApp)
        }.distinct()
        val sharedParentWhatsApp = if (uniqueNumbers.size == 1) uniqueNumbers.first() else ""
        val sharedParentName = if (uniqueNumbers.size == 1) {
            files.firstOrNull { it.parentName.isNotBlank() }?.parentName ?: "Parent"
        } else {
            "Parents"
        }

        return WhatsAppDispatchService.startBatchFilesDispatch(
            context = context,
            filePaths = validPaths,
            mimeType = mimeType,
            caption = caption,
            parentWhatsApp = sharedParentWhatsApp,
            parentName = sharedParentName
        )
    }

    /**
     * Renders a single page of an uploaded PDF report card for crisp in-app viewing.
     */
    fun renderPdfPageToBitmap(filePath: String, pageIndex: Int = 0): Pair<Bitmap?, Int> {
        val file = File(filePath)
        if (!file.exists()) return null to 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            if (count <= 0) return null to 0
            val safePage = pageIndex.coerceIn(0, count - 1)
            renderer.openPage(safePage).use { page ->
                val scale = 2
                val bmpWidth = (page.width * scale).coerceAtMost(1440)
                val bmpHeight = (page.height * scale).coerceAtMost(2040)
                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap to count
            }
        } catch (_: Exception) {
            null to 0
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    fun decodeImagePreviewBitmap(filePath: String, maxDimension: Int = 1280): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(filePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a crisp, print-ready Official Report Card Certificate Bitmap.
     */
    fun renderReportCardCertificateBitmap(report: ReportCard): Bitmap {
        val subjects = report.parsedSubjects
        val width = 1080
        val height = (1320 + subjects.size * 76).coerceAtLeast(1520)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm ivory parchment background
        canvas.drawColor(Color.parseColor("#FBF9F5"))

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#0B3D2E")
            strokeWidth = 8f
        }
        canvas.drawRoundRect(RectF(28f, 28f, width - 28f, height - 28f), 28f, 28f, borderPaint)

        val goldBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#C79A3B")
            strokeWidth = 3f
        }
        canvas.drawRoundRect(RectF(42f, 42f, width - 42f, height - 42f), 20f, 20f, goldBorderPaint)

        // Header banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B3D2E")
        }
        canvas.drawRoundRect(RectF(64f, 64f, width - 64f, 290f), 20f, 20f, headerPaint)

        val schoolTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4C46B")
            textSize = 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(report.schoolName.uppercase().take(34), width / 2f, 145f, schoolTitlePaint)

        val reportSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("OFFICIAL ACADEMIC REPORT CARD", width / 2f, 205f, reportSubtitlePaint)

        val termPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7F5D4")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(report.academicTerm, width / 2f, 255f, termPaint)

        // Student Info Box
        val infoBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EBF1ED")
        }
        canvas.drawRoundRect(RectF(64f, 320f, width - 64f, 510f), 16f, 16f, infoBoxPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3E4944")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B3D2E")
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawText("STUDENT NAME", 96f, 365f, labelPaint)
        canvas.drawText(report.studentName.take(28), 96f, 402f, valuePaint)

        canvas.drawText("STUDENT ID", 620f, 365f, labelPaint)
        canvas.drawText(report.studentCode.take(18), 620f, 402f, valuePaint)

        canvas.drawText("CLASS / GRADE", 96f, 448f, labelPaint)
        canvas.drawText(report.gradeClass.take(24), 96f, 485f, valuePaint)

        canvas.drawText("ATTENDANCE", 620f, 448f, labelPaint)
        canvas.drawText(report.attendanceSummary.take(22), 620f, 485f, valuePaint)

        // Table Header
        var currentY = 560f
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#17665B")
        }
        canvas.drawRoundRect(RectF(64f, currentY, width - 64f, currentY + 62f), 12f, 12f, tableHeaderPaint)

        val thTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("SUBJECT", 92f, currentY + 40f, thTextPaint)
        canvas.drawText("SCORE", 540f, currentY + 40f, thTextPaint)
        canvas.drawText("GRADE", 680f, currentY + 40f, thTextPaint)
        canvas.drawText("REMARK", 800f, currentY + 40f, thTextPaint)

        currentY += 74f
        val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161D19")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val rowBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B3D2E")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F1F5F2")
        }

        subjects.forEachIndexed { index, row ->
            if (index % 2 == 0) {
                canvas.drawRoundRect(RectF(64f, currentY, width - 64f, currentY + 64f), 8f, 8f, zebraPaint)
            }
            canvas.drawText(row.subjectName.take(24), 92f, currentY + 42f, rowTextPaint)
            canvas.drawText("${row.score}%", 545f, currentY + 42f, rowBoldPaint)
            canvas.drawText(row.grade, 695f, currentY + 42f, rowBoldPaint)
            canvas.drawText(row.remark.take(16), 800f, currentY + 42f, rowTextPaint)
            currentY += 72f
        }

        // Overall Summary Banner
        currentY += 24f
        val summaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFF4DC")
        }
        canvas.drawRoundRect(RectF(64f, currentY, width - 64f, currentY + 130f), 16f, 16f, summaryPaint)
        canvas.drawRoundRect(RectF(64f, currentY, width - 64f, currentY + 130f), 16f, 16f, goldBorderPaint)

        canvas.drawText("OVERALL AVERAGE", 96f, currentY + 50f, labelPaint)
        val bigScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B3D2E")
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("${report.overallScore}% (${report.overallGrade})", 96f, currentY + 102f, bigScorePaint)

        canvas.drawText("CLASS POSITION", 620f, currentY + 50f, labelPaint)
        canvas.drawText(report.classPosition.take(20), 620f, currentY + 102f, bigScorePaint)

        // Educator Remarks
        currentY += 165f
        canvas.drawText("EDUCATOR'S REMARKS:", 84f, currentY, labelPaint)
        currentY += 38f
        val remarksText = "\"${report.teacherRemarks}\""
        val words = remarksText.split(" ")
        var line = ""
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (rowTextPaint.measureText(testLine) > (width - 180f)) {
                canvas.drawText(line, 84f, currentY, rowTextPaint)
                line = word
                currentY += 34f
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 84f, currentY, rowTextPaint)
        }

        // Signature Footer
        val footerY = height - 110f
        canvas.drawText("Parent / Guardian: ${report.parentName.take(28)}", 84f, footerY, labelPaint)
        canvas.drawText("Signed: ${report.principalSignature.take(28)}", 580f, footerY, rowBoldPaint)

        return bitmap
    }
}
