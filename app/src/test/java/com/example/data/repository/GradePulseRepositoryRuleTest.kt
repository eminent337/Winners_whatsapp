package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Student
import com.example.data.model.SubjectGradeRow
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GradePulseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createStudentAndReportCard_authenticatedOwner_succeedsAndEmitsUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = GradePulseRepository(firestore, auth)

        val studentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createStudent(
                fullName = "Kwame Mensah",
                studentCode = "GP-2026-001",
                gradeClass = "Grade 8 Emerald",
                parentName = "Mrs. Abena Mensah",
                parentWhatsApp = "+233241234567",
                attendanceDays = 62L,
                totalTermDays = 65L
            ).getOrThrow()
        }
        assertTrue(studentId.isNotEmpty())

        val student = Student(
            id = studentId,
            userId = aliceUid,
            fullName = "Kwame Mensah",
            studentCode = "GP-2026-001",
            gradeClass = "Grade 8 Emerald",
            parentName = "Mrs. Abena Mensah",
            parentWhatsApp = "+233241234567",
            attendanceDays = 62L,
            totalTermDays = 65L
        )

        val reportId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createReportCard(
                student = student,
                schoolName = "Winners Family School",
                academicTerm = "Term 1 • 2026/2027",
                subjectRows = listOf(
                    SubjectGradeRow("Mathematics", 92, "A+", "Distinction"),
                    SubjectGradeRow("English Language", 88, "A", "Excellent")
                ),
                classPosition = "1st of 28",
                teacherRemarks = "Outstanding academic achievement.",
                principalSignature = "Dr. E. Amedzro, Principal"
            ).getOrThrow()
        }
        assertTrue(reportId.isNotEmpty())

        val emittedReports = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeReportCards().first { list -> list.any { it.id == reportId } }
        }
        val fetched = emittedReports.first { it.id == reportId }
        assertEquals("Kwame Mensah", fetched.studentName)
        assertEquals("pending", fetched.whatsappStatus)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markReportCardSent(fetched, status = "sent").getOrThrow()
        }

        val updatedReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getReportCardById(aliceUid, reportId).getOrThrow()
        }
        assertNotNull(updatedReport)
        assertEquals("sent", updatedReport?.whatsappStatus)
        assertEquals(1L, updatedReport?.dispatchCount)
    }

    @Test
    fun getReportCardById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = GradePulseRepository(firestore, auth)

        val reportId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createReportCard(
                student = Student(
                    id = "stu_1",
                    userId = aliceUid,
                    fullName = "Ama Osei",
                    studentCode = "GP-102",
                    gradeClass = "Grade 8",
                    parentName = "Mr. Osei",
                    parentWhatsApp = "+233201112222",
                    attendanceDays = 64L,
                    totalTermDays = 65L
                ),
                schoolName = "Winners Family School",
                academicTerm = "Term 1",
                subjectRows = listOf(SubjectGradeRow("Science", 90)),
                classPosition = "2nd",
                teacherRemarks = "Great work",
                principalSignature = "Principal"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = GradePulseRepository(firestore, auth)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getReportCardById(aliceUid, reportId).getOrThrow()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun observeReportCards_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("report_cards")
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.teacher@gradepulse.edu"
        const val BOB_EMAIL = "bob.teacher@gradepulse.edu"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
