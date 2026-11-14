package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Non-exported, intent-based Android [Service] responsible for validating and securely passing
 * student report card data and FileProvider URIs to WhatsApp pre-filled in the target parent's chat.
 */
class WhatsAppDispatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        // Defense-in-depth beyond android:exported="false": verify the per-process nonce.
        val providedNonce = intent.getStringExtra(EXTRA_DISPATCH_NONCE)
        if (providedNonce != PROCESS_DISPATCH_NONCE) {
            emitEvent(WhatsAppDispatchResult.Failed("Rejected unauthorized dispatch intent."))
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val result = executeServiceIntent(this, intent)
        emitEvent(result)
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_DISPATCH_TEXT_CHAT = "com.example.action.DISPATCH_WHATSAPP_TEXT_CHAT"
        const val ACTION_DISPATCH_REPORT_FILE = "com.example.action.DISPATCH_WHATSAPP_REPORT_FILE"
        const val ACTION_DISPATCH_BATCH_FILES = "com.example.action.DISPATCH_WHATSAPP_BATCH_FILES"

        const val EXTRA_DISPATCH_NONCE = "extra_dispatch_nonce"
        const val EXTRA_PARENT_NAME = "extra_parent_name"
        const val EXTRA_PARENT_WHATSAPP = "extra_parent_whatsapp"
        const val EXTRA_MESSAGE_TEXT = "extra_message_text"
        const val EXTRA_SUBJECT_LABEL = "extra_subject_label"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_PATHS = "extra_file_paths"
        const val EXTRA_MIME_TYPE = "extra_mime_type"

        private val PROCESS_DISPATCH_NONCE: String = buildProcessNonce()
        private val eventSequence = AtomicLong(0L)

        private val _dispatchEvents = MutableStateFlow<WhatsAppDispatchEvent?>(null)
        val dispatchEvents: StateFlow<WhatsAppDispatchEvent?> = _dispatchEvents.asStateFlow()

        fun clearLastDispatchEvent() {
            _dispatchEvents.value = null
        }

        private fun emitEvent(result: WhatsAppDispatchResult) {
            _dispatchEvents.value = WhatsAppDispatchEvent(
                id = eventSequence.incrementAndGet(),
                result = result
            )
        }

        private fun buildProcessNonce(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun createTextChatServiceIntent(
            context: Context,
            parentWhatsApp: String,
            parentName: String,
            messageText: String,
            subjectLabel: String = "Report Card"
        ): Intent {
            return Intent(context, WhatsAppDispatchService::class.java).apply {
                action = ACTION_DISPATCH_TEXT_CHAT
                putExtra(EXTRA_DISPATCH_NONCE, PROCESS_DISPATCH_NONCE)
                putExtra(EXTRA_PARENT_WHATSAPP, parentWhatsApp)
                putExtra(EXTRA_PARENT_NAME, parentName)
                putExtra(EXTRA_MESSAGE_TEXT, messageText)
                putExtra(EXTRA_SUBJECT_LABEL, subjectLabel)
            }
        }

        fun createReportFileServiceIntent(
            context: Context,
            filePath: String,
            mimeType: String,
            caption: String,
            parentWhatsApp: String,
            parentName: String,
            subjectLabel: String = "Official Report Card"
        ): Intent {
            return Intent(context, WhatsAppDispatchService::class.java).apply {
                action = ACTION_DISPATCH_REPORT_FILE
                putExtra(EXTRA_DISPATCH_NONCE, PROCESS_DISPATCH_NONCE)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_MESSAGE_TEXT, caption)
                putExtra(EXTRA_PARENT_WHATSAPP, parentWhatsApp)
                putExtra(EXTRA_PARENT_NAME, parentName)
                putExtra(EXTRA_SUBJECT_LABEL, subjectLabel)
            }
        }

        fun createBatchFilesServiceIntent(
            context: Context,
            filePaths: List<String>,
            mimeType: String,
            caption: String,
            parentWhatsApp: String = "",
            parentName: String = "Parents"
        ): Intent {
            return Intent(context, WhatsAppDispatchService::class.java).apply {
                action = ACTION_DISPATCH_BATCH_FILES
                putExtra(EXTRA_DISPATCH_NONCE, PROCESS_DISPATCH_NONCE)
                putStringArrayListExtra(EXTRA_FILE_PATHS, ArrayList(filePaths))
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_MESSAGE_TEXT, caption)
                putExtra(EXTRA_PARENT_WHATSAPP, parentWhatsApp)
                putExtra(EXTRA_PARENT_NAME, parentName)
            }
        }

        /**
         * Dispatches via [WhatsAppDispatchService] using an explicit internal service intent.
         * Also returns true/false immediately based on input validation and service start.
         */
        fun startTextChatDispatch(
            context: Context,
            parentWhatsApp: String,
            parentName: String,
            messageText: String,
            subjectLabel: String = "Report Card"
        ): Boolean {
            if (messageText.isBlank()) {
                emitEvent(WhatsAppDispatchResult.Failed("Cannot send an empty report card message."))
                return false
            }
            val serviceIntent = createTextChatServiceIntent(
                context = context,
                parentWhatsApp = parentWhatsApp,
                parentName = parentName,
                messageText = messageText,
                subjectLabel = subjectLabel
            )
            return startServiceOrExecuteDirect(context, serviceIntent)
        }

        fun startReportFileDispatch(
            context: Context,
            filePath: String,
            mimeType: String,
            caption: String,
            parentWhatsApp: String,
            parentName: String,
            subjectLabel: String = "Official Report Card"
        ): Boolean {
            if (WhatsAppSecurityValidator.resolveAuthorizedReportFile(context, filePath) == null) {
                emitEvent(WhatsAppDispatchResult.Failed("Report card file could not be verified or found."))
                return false
            }
            val serviceIntent = createReportFileServiceIntent(
                context = context,
                filePath = filePath,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                parentName = parentName,
                subjectLabel = subjectLabel
            )
            return startServiceOrExecuteDirect(context, serviceIntent)
        }

        fun startBatchFilesDispatch(
            context: Context,
            filePaths: List<String>,
            mimeType: String,
            caption: String,
            parentWhatsApp: String = "",
            parentName: String = "Parents"
        ): Boolean {
            if (filePaths.isEmpty()) {
                emitEvent(WhatsAppDispatchResult.Failed("No report card files selected."))
                return false
            }
            val serviceIntent = createBatchFilesServiceIntent(
                context = context,
                filePaths = filePaths,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                parentName = parentName
            )
            return startServiceOrExecuteDirect(context, serviceIntent)
        }

        private fun startServiceOrExecuteDirect(context: Context, serviceIntent: Intent): Boolean {
            return try {
                val component = context.startService(serviceIntent)
                if (component != null) {
                    true
                } else {
                    val result = executeServiceIntent(context, serviceIntent)
                    emitEvent(result)
                    result !is WhatsAppDispatchResult.Failed
                }
            } catch (_: IllegalStateException) {
                // Android background-start restriction safeguard: run directly from caller context
                val result = executeServiceIntent(context, serviceIntent)
                emitEvent(result)
                result !is WhatsAppDispatchResult.Failed
            } catch (_: Exception) {
                emitEvent(WhatsAppDispatchResult.Failed("Unable to launch WhatsApp dispatch service."))
                false
            }
        }

        internal fun executeServiceIntent(context: Context, intent: Intent): WhatsAppDispatchResult {
            return when (intent.action) {
                ACTION_DISPATCH_TEXT_CHAT -> handleTextChatIntent(context, intent)
                ACTION_DISPATCH_REPORT_FILE -> handleReportFileIntent(context, intent)
                ACTION_DISPATCH_BATCH_FILES -> handleBatchFilesIntent(context, intent)
                else -> WhatsAppDispatchResult.Failed("Unknown WhatsApp dispatch action.")
            }
        }

        private fun handleTextChatIntent(context: Context, intent: Intent): WhatsAppDispatchResult {
            val parentWhatsApp = intent.getStringExtra(EXTRA_PARENT_WHATSAPP).orEmpty()
            val parentName = intent.getStringExtra(EXTRA_PARENT_NAME).orEmpty().ifBlank { "Parent" }
            val message = intent.getStringExtra(EXTRA_MESSAGE_TEXT).orEmpty()
            val subject = intent.getStringExtra(EXTRA_SUBJECT_LABEL).orEmpty().ifBlank { "Report Card" }

            if (message.isBlank()) {
                return WhatsAppDispatchResult.Failed("Report card message is empty.")
            }

            val installedPkg = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
            val chatJid = WhatsAppSecurityValidator.buildWhatsAppJid(parentWhatsApp)

            if (installedPkg != null) {
                val directIntent = WhatsAppIntentFactory.buildDirectChatTextIntent(
                    context = context,
                    parentWhatsApp = parentWhatsApp,
                    messageText = message,
                    targetPackage = installedPkg
                )
                if (tryLaunchIntent(context, directIntent)) {
                    return WhatsAppDispatchResult.DispatchedToWhatsApp(
                        targetPackage = installedPkg,
                        chatJid = chatJid,
                        recipientLabel = parentName,
                        includesAttachment = false
                    )
                }
            }

            // Fallback 1: universal https://wa.me deep link
            val waMeIntent = WhatsAppIntentFactory.buildUniversalWaMeIntent(
                parentWhatsApp = parentWhatsApp,
                messageText = message
            )
            if (tryLaunchIntent(context, waMeIntent)) {
                return WhatsAppDispatchResult.DispatchedViaFallback(
                    channelLabel = "WhatsApp Web / Browser link",
                    recipientLabel = parentName
                )
            }

            // Fallback 2: Android system share sheet
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooser = Intent.createChooser(shareIntent, "Send to $parentName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (tryLaunchIntent(context, chooser, verifyResolution = false)) {
                WhatsAppDispatchResult.DispatchedViaFallback(
                    channelLabel = "Share sheet (WhatsApp not installed)",
                    recipientLabel = parentName
                )
            } else {
                WhatsAppDispatchResult.Failed("No app available to handle WhatsApp dispatch.")
            }
        }

        private fun handleReportFileIntent(context: Context, intent: Intent): WhatsAppDispatchResult {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
            val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty()
            val caption = intent.getStringExtra(EXTRA_MESSAGE_TEXT).orEmpty()
            val parentWhatsApp = intent.getStringExtra(EXTRA_PARENT_WHATSAPP).orEmpty()
            val parentName = intent.getStringExtra(EXTRA_PARENT_NAME).orEmpty().ifBlank { "Parent" }
            val subject = intent.getStringExtra(EXTRA_SUBJECT_LABEL).orEmpty().ifBlank { "Official Report Card" }

            val installedPkg = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
            val chatJid = WhatsAppSecurityValidator.buildWhatsAppJid(parentWhatsApp)

            val targetedIntent = WhatsAppIntentFactory.buildReportFileShareIntent(
                context = context,
                filePath = filePath,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                targetPackage = installedPkg
            ) ?: return WhatsAppDispatchResult.Failed("Report card file is missing or unauthorized.")

            if (installedPkg != null && tryLaunchIntent(context, targetedIntent)) {
                return WhatsAppDispatchResult.DispatchedToWhatsApp(
                    targetPackage = installedPkg,
                    chatJid = chatJid,
                    recipientLabel = parentName,
                    includesAttachment = true
                )
            }

            // Fallback without package pinning via chooser if WhatsApp is not installed
            val chooserSource = WhatsAppIntentFactory.buildReportFileShareIntent(
                context = context,
                filePath = filePath,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                targetPackage = null
            ) ?: return WhatsAppDispatchResult.Failed("Report card file is missing or unauthorized.")
            chooserSource.putExtra(Intent.EXTRA_SUBJECT, subject)
            chooserSource.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val chooser = Intent.createChooser(chooserSource, "Send Report Card to $parentName").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (tryLaunchIntent(context, chooser, verifyResolution = false)) {
                WhatsAppDispatchResult.DispatchedViaFallback(
                    channelLabel = "Share sheet (WhatsApp not installed)",
                    recipientLabel = parentName
                )
            } else {
                WhatsAppDispatchResult.Failed("Unable to open share sheet for report card.")
            }
        }

        private fun handleBatchFilesIntent(context: Context, intent: Intent): WhatsAppDispatchResult {
            val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList()
            val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE).orEmpty()
            val caption = intent.getStringExtra(EXTRA_MESSAGE_TEXT).orEmpty()
            val parentWhatsApp = intent.getStringExtra(EXTRA_PARENT_WHATSAPP).orEmpty()
            val parentName = intent.getStringExtra(EXTRA_PARENT_NAME).orEmpty().ifBlank { "Parents" }

            val installedPkg = WhatsAppSecurityValidator.resolveInstalledWhatsAppPackage(context)
            val chatJid = WhatsAppSecurityValidator.buildWhatsAppJid(parentWhatsApp)

            val targetedIntent = WhatsAppIntentFactory.buildMultipleFilesShareIntent(
                context = context,
                filePaths = filePaths,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                targetPackage = installedPkg
            ) ?: return WhatsAppDispatchResult.Failed("No valid report card files available to send.")

            if (installedPkg != null && tryLaunchIntent(context, targetedIntent)) {
                return WhatsAppDispatchResult.DispatchedToWhatsApp(
                    targetPackage = installedPkg,
                    chatJid = chatJid,
                    recipientLabel = parentName,
                    includesAttachment = true
                )
            }

            val chooserSource = WhatsAppIntentFactory.buildMultipleFilesShareIntent(
                context = context,
                filePaths = filePaths,
                mimeType = mimeType,
                caption = caption,
                parentWhatsApp = parentWhatsApp,
                targetPackage = null
            ) ?: return WhatsAppDispatchResult.Failed("No valid report card files available to send.")
            chooserSource.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val chooser = Intent.createChooser(chooserSource, "Share ${filePaths.size} Report Cards").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (tryLaunchIntent(context, chooser, verifyResolution = false)) {
                WhatsAppDispatchResult.DispatchedViaFallback(
                    channelLabel = "Share sheet (WhatsApp not installed)",
                    recipientLabel = parentName
                )
            } else {
                WhatsAppDispatchResult.Failed("Unable to share report cards.")
            }
        }

        private fun tryLaunchIntent(
            context: Context,
            intent: Intent,
            verifyResolution: Boolean = true
        ): Boolean {
            return try {
                if (verifyResolution && !WhatsAppSecurityValidator.canResolveActivity(context, intent)) {
                    return false
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
