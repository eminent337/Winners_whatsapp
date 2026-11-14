package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.example.data.model.ReportCard
import com.example.data.model.UploadedReportFile
import com.example.data.model.buildUploadedFileWhatsAppCaption
import com.example.data.model.buildWhatsAppReportMessage
import java.io.File
import java.io.FileOutputStream

object WhatsAppDispatcher {

    /**
     * Normalizes a phone number into digits only for WhatsApp wa.me / api.whatsapp.com URLs.
     */
    fun normalizeWhatsAppDigits(rawPhone: String): String {
        return rawPhone.filter { it.isDigit() }
    }

    /**
     * Checks if WhatsApp or WhatsApp Business is installed on the device.
     */
    fun resolveInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in candidates) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
                // Not installed
            }
        }
        return null
    }

    /**
     * Opens WhatsApp directly to the parent's phone number with the pre-filled report card message.
     * Falls back gracefully to the universal https://wa.me link or system share sheet if WhatsApp
     * is not installed on the device/emulator.
     */
    fun launchWhatsAppDirectText(
        context: Context,
        report: ReportCard,
        customIntroNote: String = ""
    ): Boolean {
        val message = buildWhatsAppReportMessage(report, customIntroNote)
        val cleanDigits = normalizeWhatsAppDigits(report.parentWhatsApp)
        val encodedText = Uri.encode(message)
        val waUri = if (cleanDigits.isNotEmpty()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
        }

        val installedPkg = resolveInstalledWhatsAppPackage(context)
        if (installedPkg != null) {
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage(installedPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(directIntent)
                return true
            } catch (_: Exception) {
                // Fall through
            }
        }

        // Try launching the https://wa.me deep link in browser / handler
        try {
            val webUri = if (cleanDigits.isNotEmpty()) {
                Uri.parse("https://wa.me/$cleanDigits?text=$encodedText")
            } else {
                waUri
            }
            val viewIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            return true
        } catch (_: Exception) {
            // Final fallback: system share sheet so it works on any device/emulator
            return try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Report Card: ${report.studentName}")
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                val chooser = Intent.createChooser(shareIntent, "Send Report Card to ${report.parentName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Opens WhatsApp directly to the parent's phone number with an arbitrary chat message.
     */
    fun launchWhatsAppDirectPlainText(
        context: Context,
        parentWhatsApp: String,
        parentName: String,
        message: String
    ): Boolean {
        val cleanDigits = normalizeWhatsAppDigits(parentWhatsApp)
        val encodedText = Uri.encode(message)
        val waUri = if (cleanDigits.isNotEmpty()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
        }

        val installedPkg = resolveInstalledWhatsAppPackage(context)
        if (installedPkg != null) {
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    setPackage(installedPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(directIntent)
                return true
            } catch (_: Exception) {
                // Fall through
            }
        }

        return try {
            val webUri = if (cleanDigits.isNotEmpty()) {
                Uri.parse("https://wa.me/$cleanDigits?text=$encodedText")
            } else {
                waUri
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Send WhatsApp Message to $parentName")
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Renders an official high-resolution School Report Card Certificate PNG image and shares it
     * along with the formatted WhatsApp caption via FileProvider.
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

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val caption = buildWhatsAppReportMessage(report, customIntroNote)
            val cleanDigits = normalizeWhatsAppDigits(report.parentWhatsApp)
            val installedPkg = resolveInstalledWhatsAppPackage(context)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                if (cleanDigits.isNotEmpty()) {
                    putExtra("jid", "$cleanDigits@s.whatsapp.net")
                }
                if (installedPkg != null) {
                    setPackage(installedPkg)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(sendIntent)
            } catch (_: Exception) {
                val fallbackShare = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(fallbackShare, "Share Official Report Card Certificate").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
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
     * Sends an uploaded PDF or image report card directly to WhatsApp (targeting the parent's jid
     * when assigned) using FileProvider. Falls back to the Android share sheet if needed.
     */
    fun launchWhatsAppWithUploadedFile(
        context: Context,
        file: UploadedReportFile,
        schoolName: String,
        academicTerm: String,
        principalSignature: String,
        customIntroNote: String = ""
    ): Boolean {
        val localFile = File(file.localFilePath)
        if (!localFile.exists()) return false
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )
            val caption = buildUploadedFileWhatsAppCaption(
                file = file,
                schoolName = schoolName,
                academicTerm = academicTerm,
                principalSignature = principalSignature,
                customIntroNote = customIntroNote
            )
            val cleanDigits = normalizeWhatsAppDigits(file.parentWhatsApp)
            val installedPkg = resolveInstalledWhatsAppPackage(context)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType.ifBlank { if (file.isPdf) "application/pdf" else "image/*" }
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                if (cleanDigits.isNotEmpty()) {
                    putExtra("jid", "$cleanDigits@s.whatsapp.net")
                }
                if (installedPkg != null) {
                    setPackage(installedPkg)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(sendIntent)
            } catch (_: Exception) {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = file.mimeType.ifBlank { if (file.isPdf) "application/pdf" else "image/*" }
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(fallback, "Send Report Card via WhatsApp").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shares multiple selected report card files together to WhatsApp via ACTION_SEND_MULTIPLE.
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

        return try {
            val uris = ArrayList<Uri>()
            for (item in files) {
                val localFile = File(item.localFilePath)
                if (localFile.exists()) {
                    uris.add(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            localFile
                        )
                    )
                }
            }
            if (uris.isEmpty()) return false

            val mimeType = when {
                files.all { it.isPdf } -> "application/pdf"
                files.all { it.isImage } -> "image/*"
                else -> "*/*"
            }
            val caption = buildString {
                append("*${schoolName.uppercase()}*\n")
                append("Official Report Cards (${uris.size}) • $academicTerm\n")
                if (customIntroNote.isNotBlank()) {
                    append("\n${customIntroNote.trim()}\n")
                }
                append("\nSigned: $principalSignature")
            }

            val installedPkg = resolveInstalledWhatsAppPackage(context)
            val multiIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_TEXT, caption)
                if (installedPkg != null) {
                    setPackage(installedPkg)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(multiIntent)
            } catch (_: Exception) {
                val fallback = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(fallback, "Share ${uris.size} Report Cards").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (_: Exception) {
            false
        }
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
