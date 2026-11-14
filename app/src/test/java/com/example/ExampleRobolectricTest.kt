package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ReportCard
import com.example.data.model.SubjectGradeRow
import com.example.data.model.buildWhatsAppReportMessage
import com.example.data.model.calculateLetterGrade
import com.example.data.model.encodeSubjectsSummary
import com.example.data.model.parseSubjectsSummary
import com.example.util.WhatsAppDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GradePulse", appName)
    }

    @Test
    fun `encode and parse subjects summary and build WhatsApp message`() {
        val rows = listOf(
            SubjectGradeRow("Mathematics", 95),
            SubjectGradeRow("Integrated Science", 84)
        )
        val encoded = encodeSubjectsSummary(rows)
        val parsed = parseSubjectsSummary(encoded)
        assertEquals(2, parsed.size)
        assertEquals("A+", parsed[0].grade)
        assertEquals("A", parsed[1].grade)
        assertEquals("A+", calculateLetterGrade(92))

        val report = ReportCard(
            id = "rep_1",
            studentName = "Kwame Mensah",
            studentCode = "GP-101",
            gradeClass = "Grade 8 Emerald",
            parentName = "Mrs. Abena Mensah",
            parentWhatsApp = "+233 24 123 4567",
            schoolName = "Winners Family School",
            academicTerm = "Term 1 • 2026/2027",
            attendanceSummary = "63 / 65 Days (96%)",
            subjectsSummary = encoded,
            overallScore = 90L,
            overallGrade = "A+",
            classPosition = "1st of 28",
            teacherRemarks = "Outstanding work.",
            principalSignature = "Dr. E. Amedzro"
        )
        val message = buildWhatsAppReportMessage(report)
        assertTrue(message.contains("Kwame Mensah"))
        assertTrue(message.contains("Mathematics"))
        assertEquals("233241234567", WhatsAppDispatcher.normalizeWhatsAppDigits(report.parentWhatsApp))
    }
}
