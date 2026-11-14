package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ReportCard
import com.example.data.model.Student
import com.example.data.model.SubjectGradeRow
import com.example.data.model.calculateLetterGrade
import com.example.data.model.encodeSubjectsSummary
import com.example.data.model.normalizeThreadKey
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GradePulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeStudents(): Flow<List<Student>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/students"
        emitAll(
            db.collection("users").document(uid).collection("students")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeReportCards(): Flow<List<ReportCard>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/report_cards"
        emitAll(
            db.collection("users").document(uid).collection("report_cards")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReportCard::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getStudents(): Result<List<Student>> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid/students"
        try {
            val snapshot = db.collection("users").document(uid).collection("students").get().await()
            snapshot.toObjects(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getReportCards(): Result<List<ReportCard>> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid/report_cards"
        try {
            val snapshot = db.collection("users").document(uid).collection("report_cards").get().await()
            snapshot.toObjects(ReportCard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun getReportCardById(ownerUserId: String, reportId: String): Result<ReportCard?> = runCatching {
        val docRef = db.collection("users").document(ownerUserId)
            .collection("report_cards").document(reportId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(ReportCard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createStudent(
        fullName: String,
        studentCode: String,
        gradeClass: String,
        parentName: String,
        parentWhatsApp: String,
        attendanceDays: Long,
        totalTermDays: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("students").document()
        val payload = mapOf(
            "userId" to uid,
            "fullName" to fullName.trim().take(100),
            "studentCode" to studentCode.trim().take(40),
            "gradeClass" to gradeClass.trim().take(50),
            "parentName" to parentName.trim().take(100),
            "parentWhatsApp" to parentWhatsApp.trim().take(30),
            "attendanceDays" to attendanceDays.coerceIn(0L, 366L),
            "totalTermDays" to totalTermDays.coerceIn(1L, 366L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStudent(student: Student): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(student.id.isNotBlank()) { "Student ID must not be blank for update" }
        requireNotNull(student.createdAt) { "Student createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("students").document(student.id)
        try {
            docRef.set(
                student.copy(
                    userId = uid,
                    fullName = student.fullName.trim().take(100),
                    studentCode = student.studentCode.trim().take(40),
                    gradeClass = student.gradeClass.trim().take(50),
                    parentName = student.parentName.trim().take(100),
                    parentWhatsApp = student.parentWhatsApp.trim().take(30),
                    attendanceDays = student.attendanceDays.coerceIn(0L, 366L),
                    totalTermDays = student.totalTermDays.coerceIn(1L, 366L),
                    updatedAt = null
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("students").document(studentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun createReportCard(
        student: Student,
        schoolName: String,
        academicTerm: String,
        subjectRows: List<SubjectGradeRow>,
        classPosition: String,
        teacherRemarks: String,
        principalSignature: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("report_cards").document()
        val validRows = subjectRows.filter { it.subjectName.isNotBlank() }
        val avgScore = if (validRows.isNotEmpty()) {
            (validRows.sumOf { it.score.coerceIn(0, 100) } / validRows.size).toLong().coerceIn(0L, 100L)
        } else {
            85L
        }
        val overallGrade = calculateLetterGrade(avgScore.toInt())
        val encodedSubjects = encodeSubjectsSummary(validRows)

        val payload = mapOf(
            "userId" to uid,
            "studentId" to student.id.ifBlank { "stu_manual" }.take(128),
            "studentName" to student.fullName.trim().take(100),
            "studentCode" to student.studentCode.trim().take(40),
            "gradeClass" to student.gradeClass.trim().take(50),
            "parentName" to student.parentName.trim().take(100),
            "parentWhatsApp" to student.parentWhatsApp.trim().take(30),
            "schoolName" to schoolName.trim().ifBlank { "Winners Family School" }.take(120),
            "academicTerm" to academicTerm.trim().ifBlank { "Term 1 • 2026/2027" }.take(60),
            "attendanceSummary" to student.formattedAttendance.take(40),
            "subjectsSummary" to encodedSubjects,
            "overallScore" to avgScore,
            "overallGrade" to overallGrade.take(10),
            "classPosition" to classPosition.trim().ifBlank { "Top 10%" }.take(30),
            "teacherRemarks" to teacherRemarks.trim().ifBlank { "Shows strong commitment to academic growth." }.take(500),
            "principalSignature" to principalSignature.trim().ifBlank { "Office of the Principal" }.take(100),
            "whatsappStatus" to "pending",
            "dispatchCount" to 0L,
            "lastSentAt" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markReportCardSent(
        reportCard: ReportCard,
        status: String = "sent"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(reportCard.id.isNotBlank()) { "ReportCard ID must not be blank" }
        val docRef = db.collection("users").document(uid)
            .collection("report_cards").document(reportCard.id)
        val formattedTime = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date())
        val nextCount = (reportCard.dispatchCount + 1L).coerceAtMost(1000L)
        val safeStatus = if (status in listOf("pending", "sent", "reminded")) status else "sent"
        try {
            docRef.update(
                mapOf(
                    "whatsappStatus" to safeStatus,
                    "dispatchCount" to nextCount,
                    "lastSentAt" to formattedTime.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteReportCard(reportId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("report_cards").document(reportId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedSampleSchoolData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val studentsCol = db.collection("users").document(uid).collection("students")
        val reportsCol = db.collection("users").document(uid).collection("report_cards")
        val existingStudents = studentsCol.limit(1).get().await()
        if (!existingStudents.isEmpty) return@runCatching

        val batch = db.batch()
        val s1Ref = studentsCol.document()
        val s2Ref = studentsCol.document()
        val s3Ref = studentsCol.document()
        val s4Ref = studentsCol.document()

        val sampleStudents = listOf(
            s1Ref to Student(
                id = s1Ref.id,
                userId = uid,
                fullName = "Kwame Mensah",
                studentCode = "GP-2026-101",
                gradeClass = "Grade 8 • Emerald",
                parentName = "Mrs. Abena Mensah",
                parentWhatsApp = "+233244112233",
                attendanceDays = 63L,
                totalTermDays = 65L
            ),
            s2Ref to Student(
                id = s2Ref.id,
                userId = uid,
                fullName = "Ama Osei-Bonsu",
                studentCode = "GP-2026-102",
                gradeClass = "Grade 8 • Emerald",
                parentName = "Mr. Kofi Osei-Bonsu",
                parentWhatsApp = "+233208998877",
                attendanceDays = 65L,
                totalTermDays = 65L
            ),
            s3Ref to Student(
                id = s3Ref.id,
                userId = uid,
                fullName = "Nana Yaw Boateng",
                studentCode = "GP-2026-103",
                gradeClass = "Grade 9 • Gold",
                parentName = "Dr. Akosua Boateng",
                parentWhatsApp = "+233547654321",
                attendanceDays = 60L,
                totalTermDays = 65L
            ),
            s4Ref to Student(
                id = s4Ref.id,
                userId = uid,
                fullName = "Efua Asante",
                studentCode = "GP-2026-104",
                gradeClass = "Grade 9 • Gold",
                parentName = "Mr. Yaw Asante",
                parentWhatsApp = "+233276123987",
                attendanceDays = 64L,
                totalTermDays = 65L
            )
        )

        for ((ref, stu) in sampleStudents) {
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "fullName" to stu.fullName,
                    "studentCode" to stu.studentCode,
                    "gradeClass" to stu.gradeClass,
                    "parentName" to stu.parentName,
                    "parentWhatsApp" to stu.parentWhatsApp,
                    "attendanceDays" to stu.attendanceDays,
                    "totalTermDays" to stu.totalTermDays,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val sampleReports = listOf(
            Triple(
                sampleStudents[0].second,
                listOf(
                    SubjectGradeRow("Mathematics", 94, "A+", "Exceptional problem solver"),
                    SubjectGradeRow("Integrated Science", 89, "A", "Strong laboratory inquiry"),
                    SubjectGradeRow("English Language", 86, "A", "Articulate written expression"),
                    SubjectGradeRow("Computing & ICT", 96, "A+", "Top coding project in class"),
                    SubjectGradeRow("Social Studies", 85, "A", "Thoughtful civic discussions")
                ),
                Triple("1st of 28", "Kwame demonstrates exemplary leadership, curiosity, and academic discipline across all subjects.", "pending" to 0L)
            ),
            Triple(
                sampleStudents[1].second,
                listOf(
                    SubjectGradeRow("Mathematics", 88, "A", "Consistent accuracy"),
                    SubjectGradeRow("Integrated Science", 92, "A+", "Outstanding research project"),
                    SubjectGradeRow("English Language", 95, "A+", "Debate team captain"),
                    SubjectGradeRow("Computing & ICT", 84, "A", "Great digital presentation"),
                    SubjectGradeRow("Social Studies", 91, "A+", "Insightful historical analysis")
                ),
                Triple("2nd of 28", "Ama had 100% attendance this term and continues to inspire her peers with her communication skills.", "pending" to 0L)
            ),
            Triple(
                sampleStudents[2].second,
                listOf(
                    SubjectGradeRow("Mathematics", 78, "B+", "Good progress in algebra"),
                    SubjectGradeRow("Integrated Science", 82, "A", "Active in practicals"),
                    SubjectGradeRow("English Language", 76, "B+", "Steady reading improvement"),
                    SubjectGradeRow("Computing & ICT", 88, "A", "Enthusiastic learner"),
                    SubjectGradeRow("Social Studies", 81, "A", "Well-prepared assignments")
                ),
                Triple("6th of 26", "Nana Yaw has made commendable strides this term. Extra practice in algebra will propel him into the top 3.", "sent" to 1L)
            )
        )

        val sentTimestamp = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date())
        for ((stu, rows, meta) in sampleReports) {
            val (position, remarks, statusPair) = meta
            val (status, count) = statusPair
            val avgScore = (rows.sumOf { it.score } / rows.size).toLong()
            val repRef = reportsCol.document()
            batch.set(
                repRef,
                mapOf(
                    "userId" to uid,
                    "studentId" to stu.id,
                    "studentName" to stu.fullName,
                    "studentCode" to stu.studentCode,
                    "gradeClass" to stu.gradeClass,
                    "parentName" to stu.parentName,
                    "parentWhatsApp" to stu.parentWhatsApp,
                    "schoolName" to "Winners Family School",
                    "academicTerm" to "Term 1 • 2026/2027",
                    "attendanceSummary" to stu.formattedAttendance,
                    "subjectsSummary" to encodeSubjectsSummary(rows),
                    "overallScore" to avgScore,
                    "overallGrade" to calculateLetterGrade(avgScore.toInt()),
                    "classPosition" to position,
                    "teacherRemarks" to remarks,
                    "principalSignature" to "Smith Sampson, Principal",
                    "whatsappStatus" to status,
                    "dispatchCount" to count,
                    "lastSentAt" to if (status == "sent") sentTimestamp else "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reportsCol.path)
            throw e
        }
    }

    suspend fun migrateDefaultSchoolNameIfNeeded(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val reportsCol = db.collection("users").document(uid).collection("report_cards")
        val legacySchoolSnap = reportsCol
            .whereEqualTo("schoolName", "Royal Crest International School")
            .get()
            .await()
        val legacyPrincipalSnap = reportsCol
            .whereEqualTo("principalSignature", "Dr. E. Amedzro, Principal")
            .get()
            .await()

        val updatesById = linkedMapOf<String, Pair<com.google.firebase.firestore.DocumentReference, MutableMap<String, Any>>>()
        for (doc in legacySchoolSnap.documents) {
            val map = updatesById.getOrPut(doc.id) {
                doc.reference to mutableMapOf("updatedAt" to FieldValue.serverTimestamp())
            }.second
            map["schoolName"] = "Winners Family School"
        }
        for (doc in legacyPrincipalSnap.documents) {
            val map = updatesById.getOrPut(doc.id) {
                doc.reference to mutableMapOf("updatedAt" to FieldValue.serverTimestamp())
            }.second
            map["principalSignature"] = "Smith Sampson, Principal"
        }

        if (updatesById.isEmpty()) return@runCatching
        val batch = db.batch()
        for ((ref, fields) in updatesById.values) {
            batch.update(ref, fields)
        }
        batch.commit().await()
    }

    suspend fun updateSchoolBrandingOnReportCards(
        schoolName: String,
        academicTerm: String,
        principalSignature: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val reportsCol = db.collection("users").document(uid).collection("report_cards")
        val snap = reportsCol.get().await()
        if (snap.isEmpty) return@runCatching
        val safeSchool = schoolName.trim().ifBlank { "Winners Family School" }.take(120)
        val safeTerm = academicTerm.trim().ifBlank { "Term 1 • 2026/2027" }.take(60)
        val safeSig = principalSignature.trim().ifBlank { "Office of the Principal" }.take(100)
        val batch = db.batch()
        for (doc in snap.documents) {
            batch.update(
                doc.reference,
                mapOf(
                    "schoolName" to safeSchool,
                    "academicTerm" to safeTerm,
                    "principalSignature" to safeSig,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
    }

    fun observeChatMessages(): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chat_messages"
        emitAll(
            db.collection("users").document(uid).collection("chat_messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logChatMessage(
        threadKey: String,
        studentId: String,
        studentName: String,
        parentName: String,
        parentWhatsApp: String,
        direction: String,
        messageText: String,
        attachmentFileId: String = "",
        attachmentFileName: String = "",
        attachmentMimeType: String = "",
        deliveryStatus: String = if (direction == "incoming") "received" else "sent"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chat_messages").document()
        val safePhone = parentWhatsApp.trim().ifBlank { "+2330000000" }.take(30)
        val safeThread = threadKey.trim().ifBlank { normalizeThreadKey(safePhone, studentId) }.take(60)
        val safeDirection = if (direction in listOf("outgoing", "incoming")) direction else "outgoing"
        val safeStatus = if (deliveryStatus in listOf("sent", "delivered", "received")) deliveryStatus else "sent"
        val timeLabel = SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date()).take(60)

        val payload = mapOf(
            "userId" to uid,
            "threadKey" to safeThread,
            "studentId" to studentId.trim().take(128),
            "studentName" to studentName.trim().ifBlank { "Student" }.take(100),
            "parentName" to parentName.trim().ifBlank { "Parent / Guardian" }.take(100),
            "parentWhatsApp" to safePhone,
            "direction" to safeDirection,
            "messageText" to messageText.trim().ifBlank { "Report card dispatched" }.take(1500),
            "attachmentFileId" to attachmentFileId.trim().take(128),
            "attachmentFileName" to attachmentFileName.trim().take(140),
            "attachmentMimeType" to attachmentMimeType.trim().take(60),
            "deliveryStatus" to safeStatus,
            "sentTimeLabel" to timeLabel,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChatMessage(messageId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chat_messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChatThread(threadKey: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("chat_messages")
        val snap = col.whereEqualTo("threadKey", threadKey).get().await()
        if (snap.isEmpty) return@runCatching
        val batch = db.batch()
        for (doc in snap.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
