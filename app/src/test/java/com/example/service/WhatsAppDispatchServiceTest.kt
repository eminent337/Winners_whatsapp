package com.example.service

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UploadedReportFile
import com.example.data.model.buildUploadedFileWhatsAppCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsAppDispatchServiceTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WhatsAppDispatchService.clearLastDispatchEvent()
        // Robolectric creates a fresh filesDir per test method while FileProvider.sCache is static.
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun `normalizeE164Digits and buildWhatsAppJid handle valid and invalid phone numbers`() {
        assertEquals("233241234567", WhatsAppSecurityValidator.normalizeE164Digits("+233 24-123-4567"))
        assertEquals("233241234567", WhatsAppSecurityValidator.normalizeE164Digits("00233241234567"))
        assertEquals("233241234567@s.whatsapp.net", WhatsAppSecurityValidator.buildWhatsAppJid("+233 24 123 4567"))

        assertNull(WhatsAppSecurityValidator.normalizeE164Digits("12345"))
        assertNull(WhatsAppSecurityValidator.normalizeE164Digits(""))
        assertNull(WhatsAppSecurityValidator.buildWhatsAppJid("abc"))
    }

    @Test
    fun `resolveAuthorizedReportFile blocks path traversal and outside files`() {
        val authorizedDir = File(app.filesDir, "uploaded_report_cards/user_1").apply { mkdirs() }
        val validPdf = File(authorizedDir, "kwame_mensah.pdf").apply { writeText("%PDF-1.4 sample") }

        val outsideFile = File(app.filesDir, "secret_db.txt").apply { writeText("sensitive") }
        val traversalPath = "${authorizedDir.absolutePath}/../../secret_db.txt"

        assertNotNull(WhatsAppSecurityValidator.resolveAuthorizedReportFile(app, validPdf.absolutePath))
        assertNull(WhatsAppSecurityValidator.resolveAuthorizedReportFile(app, outsideFile.absolutePath))
        assertNull(WhatsAppSecurityValidator.resolveAuthorizedReportFile(app, traversalPath))
        assertNull(WhatsAppSecurityValidator.getAuthorizedFileUri(app, outsideFile.absolutePath))
    }

    @Test
    fun `buildReportFileShareIntent attaches FileProvider ClipData, JID, and WhatsApp package`() {
        val authorizedDir = File(app.filesDir, "uploaded_report_cards/user_1").apply { mkdirs() }
        val reportPdf = File(authorizedDir, "ama_owusu_report.pdf").apply { writeText("%PDF-1.4 content") }

        val reportFile = UploadedReportFile(
            id = "file_1",
            fileName = "ama_owusu_report.pdf",
            folderName = "Term 1",
            mimeType = "application/pdf",
            localFilePath = reportPdf.absolutePath,
            fileSizeBytes = reportPdf.length(),
            uploadedAtMillis = 1000L,
            studentName = "Ama Owusu",
            parentName = "Mr. Kojo Owusu",
            parentWhatsApp = "+233 20 987 6543"
        )
        val caption = buildUploadedFileWhatsAppCaption(
            file = reportFile,
            schoolName = "Winners Family School",
            academicTerm = "Term 1 • 2026/2027",
            principalSignature = "Smith Sampson, Principal"
        )

        val intent = WhatsAppIntentFactory.buildReportFileShareIntent(
            context = app,
            filePath = reportFile.localFilePath,
            mimeType = reportFile.mimeType,
            caption = caption,
            parentWhatsApp = reportFile.parentWhatsApp,
            targetPackage = "com.whatsapp"
        )

        assertNotNull(intent)
        requireNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("233209876543@s.whatsapp.net", intent.getStringExtra("jid"))
        assertEquals(WhatsAppIntentFactory.DEFAULT_DIRECT_FILE_CAPTION, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertNotNull(intent.clipData)
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals("content", streamUri?.scheme)
        assertEquals("${app.packageName}.fileprovider", streamUri?.authority)
    }

    @Test
    fun `buildReportFileShareIntent preserves a real caption while targeting parent jid`() {
        val authorizedDir = File(app.filesDir, "uploaded_report_cards/user_2").apply { mkdirs() }
        val reportPdf = File(authorizedDir, "yaw_mensah_report.pdf").apply { writeText("%PDF-1.4 content") }

        val intent = WhatsAppIntentFactory.buildReportFileShareIntent(
            context = app,
            filePath = reportPdf.absolutePath,
            mimeType = "application/pdf",
            caption = "Please review this report.",
            parentWhatsApp = "0241234567",
            targetPackage = "com.whatsapp"
        )

        requireNotNull(intent)
        assertEquals("Please review this report.", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("233241234567@s.whatsapp.net", intent.getStringExtra("jid"))
    }

    @Test
    fun `buildDirectChatTextIntent pre-fills target chat phone and message`() {
        val intent = WhatsAppIntentFactory.buildDirectChatTextIntent(
            context = app,
            parentWhatsApp = "+233 55 111 2222",
            messageText = "Hello Mrs. Mensah, Kwame's report card is ready.",
            targetPackage = "com.whatsapp"
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        val uri = intent.data
        assertNotNull(uri)
        assertEquals("233551112222", uri?.getQueryParameter("phone"))
        assertTrue(uri?.getQueryParameter("text").orEmpty().contains("Kwame's report card"))
    }

    @Test
    fun `WhatsAppDispatchService rejects unauthorized intents missing dispatch nonce`() {
        val forgedIntent = Intent(app, WhatsAppDispatchService::class.java).apply {
            action = WhatsAppDispatchService.ACTION_DISPATCH_TEXT_CHAT
            putExtra(WhatsAppDispatchService.EXTRA_PARENT_WHATSAPP, "+233241234567")
            putExtra(WhatsAppDispatchService.EXTRA_MESSAGE_TEXT, "Forged payload")
        }

        val controller = Robolectric.buildService(WhatsAppDispatchService::class.java, forgedIntent).create()
        controller.startCommand(0, 1)

        val event = WhatsAppDispatchService.dispatchEvents.value
        assertNotNull(event)
        assertTrue(event?.result is WhatsAppDispatchResult.Failed)
    }

    @Test
    fun `WhatsAppDispatchService launches pre-filled WhatsApp chat when WhatsApp package is installed`() {
        val shadowPm = shadowOf(app.packageManager)
        val waAppInfo = android.content.pm.ApplicationInfo().apply {
            packageName = "com.whatsapp"
        }
        val waActivityInfo = android.content.pm.ActivityInfo().apply {
            packageName = "com.whatsapp"
            name = "com.whatsapp.ContactPicker"
            applicationInfo = waAppInfo
            exported = true
        }
        val waPackageInfo = android.content.pm.PackageInfo().apply {
            packageName = "com.whatsapp"
            applicationInfo = waAppInfo
            activities = arrayOf(waActivityInfo)
        }
        shadowPm.installPackage(waPackageInfo)

        val resolveInfo = android.content.pm.ResolveInfo().apply {
            activityInfo = waActivityInfo
        }
        val expectedQueryIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage("com.whatsapp")
        }
        shadowPm.addResolveInfoForIntent(expectedQueryIntent, resolveInfo)

        val authorizedDir = File(app.filesDir, "uploaded_report_cards/user_1").apply { mkdirs() }
        val pdf = File(authorizedDir, "kwame_term1.pdf").apply { writeText("%PDF-1.4 report") }

        val serviceIntent = WhatsAppDispatchService.createReportFileServiceIntent(
            context = app,
            filePath = pdf.absolutePath,
            mimeType = "application/pdf",
            caption = "Official Report Card for Kwame Mensah — Winners Family School",
            parentWhatsApp = "+233 24 123 4567",
            parentName = "Mrs. Abena Mensah"
        )

        val controller = Robolectric.buildService(WhatsAppDispatchService::class.java, serviceIntent).create()
        controller.startCommand(0, 1)

        val event = WhatsAppDispatchService.dispatchEvents.value
        assertNotNull(event)
        assertTrue(
            "Expected DispatchedToWhatsApp, but got: ${event?.result}",
            event?.result is WhatsAppDispatchResult.DispatchedToWhatsApp
        )
        val result = event?.result as WhatsAppDispatchResult.DispatchedToWhatsApp
        assertEquals("com.whatsapp", result.targetPackage)
        assertEquals("233241234567@s.whatsapp.net", result.chatJid)
        assertTrue(result.includesAttachment)

        val launched = shadowOf(app).nextStartedActivity
        assertNotNull(launched)
        assertEquals("com.whatsapp", launched.`package`)
        assertEquals("233241234567@s.whatsapp.net", launched.getStringExtra("jid"))
        assertTrue(launched.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("Kwame Mensah"))
    }
}
