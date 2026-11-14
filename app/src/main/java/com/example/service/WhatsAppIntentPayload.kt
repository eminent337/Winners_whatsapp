package com.example.service

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

sealed interface WhatsAppDispatchResult {
    data class DispatchedToWhatsApp(
        val targetPackage: String,
        val chatJid: String?,
        val recipientLabel: String,
        val includesAttachment: Boolean
    ) : WhatsAppDispatchResult

    data class DispatchedViaFallback(
        val channelLabel: String,
        val recipientLabel: String
    ) : WhatsAppDispatchResult

    data class Failed(
        val reason: String
    ) : WhatsAppDispatchResult
}

data class WhatsAppDispatchEvent(
    val id: Long,
    val result: WhatsAppDispatchResult
)

/**
 * Validates recipient numbers, canonicalizes local report card paths to prevent directory traversal,
 * and ensures intents are strictly scoped to verified WhatsApp packages with FileProvider read grants.
 */
object WhatsAppSecurityValidator {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    private const val MIN_E164_DIGITS = 7
    private const val MAX_E164_DIGITS = 15

    /**
     * Normalizes a raw phone number into E.164 digits (7..15 digits) without '+', stripping any
     * leading "00" international dial prefix. Returns null if the number is invalid.
     */
    fun normalizeE164Digits(rawPhone: String): String? {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return null
        var digits = trimmed.filter { it.isDigit() }
        if (digits.startsWith("00") && digits.length > MIN_E164_DIGITS + 2) {
            digits = digits.removePrefix("00")
        }
        return if (digits.length in MIN_E164_DIGITS..MAX_E164_DIGITS) digits else null
    }

    /**
     * Builds a WhatsApp JID (`<digits>@s.whatsapp.net`) for pre-targeting a specific parent chat.
     */
    fun buildWhatsAppJid(rawPhone: String): String? {
        val digits = normalizeE164Digits(rawPhone) ?: return null
        return "$digits@s.whatsapp.net"
    }

    /**
     * Verifies that [filePath] resolves canonically inside one of the app's authorized report card
     * directories (`filesDir/uploaded_report_cards` or `cacheDir/report_cards`), preventing path
     * traversal or symlink escapes before generating a FileProvider URI.
     */
    fun resolveAuthorizedReportFile(context: Context, filePath: String): File? {
        if (filePath.isBlank()) return null
        return try {
            val candidate = File(filePath).canonicalFile
            if (!candidate.exists() || !candidate.isFile || !candidate.canRead()) {
                return null
            }
            val allowedRoots = listOf(
                File(context.filesDir, "uploaded_report_cards").canonicalFile,
                File(context.cacheDir, "report_cards").canonicalFile
            )
            val candidatePath = candidate.path
            val isInsideAuthorizedRoot = allowedRoots.any { root ->
                val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separatorChar
                candidatePath.startsWith(rootPrefix)
            }
            if (isInsideAuthorizedRoot) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Produces a FileProvider content URI only for an authorized, verified report card file.
     */
    fun getAuthorizedFileUri(context: Context, filePath: String): Uri? {
        val authorizedFile = resolveAuthorizedReportFile(context, filePath) ?: return null
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                authorizedFile
            )
        } catch (_: Exception) {
            null
        }
    }

    fun resolveInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
                // Package not installed
            }
        }
        return null
    }

    fun canResolveActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Constructs hardened Android Intents that pass student report card data and attachments
 * directly into the targeted parent's WhatsApp chat.
 */
object WhatsAppIntentFactory {

    fun buildDirectChatTextIntent(
        context: Context,
        parentWhatsApp: String,
        messageText: String,
        targetPackage: String? = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
    ): Intent {
        val digits = WhatsAppSecurityValidator.normalizeE164Digits(parentWhatsApp)
        val encodedText = Uri.encode(messageText)
        val waUri = if (digits != null) {
            Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=$encodedText")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
        }

        return Intent(Intent.ACTION_VIEW, waUri).apply {
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildUniversalWaMeIntent(
        parentWhatsApp: String,
        messageText: String
    ): Intent {
        val digits = WhatsAppSecurityValidator.normalizeE164Digits(parentWhatsApp)
        val encodedText = Uri.encode(messageText)
        val webUri = if (digits != null) {
            Uri.parse("https://wa.me/$digits?text=$encodedText")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
        }
        return Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildReportFileShareIntent(
        context: Context,
        filePath: String,
        mimeType: String,
        caption: String,
        parentWhatsApp: String,
        targetPackage: String? = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
    ): Intent? {
        val authorizedFile = WhatsAppSecurityValidator.resolveAuthorizedReportFile(context, filePath)
            ?: return null
        val contentUri = WhatsAppSecurityValidator.getAuthorizedFileUri(context, authorizedFile.path)
            ?: return null

        val resolvedMime = mimeType.ifBlank {
            if (authorizedFile.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
        }
        val chatJid = WhatsAppSecurityValidator.buildWhatsAppJid(parentWhatsApp)

        return Intent(Intent.ACTION_SEND).apply {
            type = resolvedMime
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            // Attach ClipData so Android scoped URI read permission grants reliably cover EXTRA_STREAM.
            clipData = ClipData.newUri(context.contentResolver, authorizedFile.name, contentUri)
            if (chatJid != null) {
                putExtra("jid", chatJid)
            }
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildMultipleFilesShareIntent(
        context: Context,
        filePaths: List<String>,
        mimeType: String,
        caption: String,
        parentWhatsApp: String = "",
        targetPackage: String? = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
    ): Intent? {
        val uris = ArrayList<Uri>()
        var clipData: ClipData? = null
        for (path in filePaths) {
            val authorizedFile = WhatsAppSecurityValidator.resolveAuthorizedReportFile(context, path)
                ?: continue
            val uri = WhatsAppSecurityValidator.getAuthorizedFileUri(context, authorizedFile.path)
                ?: continue
            uris.add(uri)
            clipData = if (clipData == null) {
                ClipData.newUri(context.contentResolver, authorizedFile.name, uri)
            } else {
                clipData.apply { addItem(ClipData.Item(uri)) }
            }
        }
        if (uris.isEmpty()) return null

        val chatJid = WhatsAppSecurityValidator.buildWhatsAppJid(parentWhatsApp)

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType.ifBlank { "*/*" }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, caption)
            if (clipData != null) {
                this.clipData = clipData
            }
            if (chatJid != null) {
                putExtra("jid", chatJid)
            }
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
