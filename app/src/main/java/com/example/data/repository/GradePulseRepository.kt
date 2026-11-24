package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.example.data.OperationType
import com.example.data.RosterParser
import com.example.data.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ReportCard
import com.example.data.model.Student
import com.example.data.model.SubjectGradeRow
import com.example.data.model.calculateLetterGrade
import com.example.data.model.encodeSubjectsSummary
import com.example.data.model.normalizeThreadKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local-first data layer.
 *
 * Replaces the original Cloud Firestore implementation so the app runs with no
 * Google account, no Firebase project and no network connection. Data lives in
 * [SharedPreferences] as JSON, keyed per user profile, and is exposed through
 * [MutableStateFlow]s that behave like Firestore snapshot listeners.
 *
 * Every public method keeps the exact signature and Result<T> contract of the
 * previous Firestore-backed repository, so the ViewModel needed no changes.
 */
class GradePulseRepository internal constructor(
    context: Context,
    private val profileUserId: String
) {

    companion object {
        private const val PREFS_NAME = "gradepulse_local_store"
        private const val KEY_STUDENTS = "students_json"
        private const val KEY_REPORTS = "report_cards_json"
        private const val KEY_CHAT = "chat_messages_json"
        private const val LOCAL_USER = "local_user"

        /** Local profiles available for sign-in, persisted on first run. */
        fun localProfiles(context: Context): List<LocalProfile> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getStringSet("profiles_raw", null) ?: run {
                val seeded = linkedSetOf(LOCAL_USER)
                prefs.edit().putStringSet("profiles_raw", seeded).apply()
                seeded
            }
            return raw.map { id ->
                LocalProfile(id = id, displayName = "This Device")
            }
        }
    }

    /** A lightweight stand-in for a signed-in Firebase user. */
    data class LocalProfile(val id: String, val displayName: String)

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val studentsFlow = MutableStateFlow(readStudents())
    private val reportsFlow = MutableStateFlow(readReports())
    private val chatFlow = MutableStateFlow(readChat())

    private fun readStudents(): List<Student> = decodeList(KEY_STUDENTS) { o -> Student(
        id = o.optString("id"), userId = o.optString("userId"), fullName = o.optString("fullName"),
        studentCode = o.optString("studentCode"), gradeClass = o.optString("gradeClass"),
        parentName = o.optString("parentName"), parentWhatsApp = o.optString("parentWhatsApp"),
        attendanceDays = o.optLong("attendanceDays"), totalTermDays = o.optLong("totalTermDays", 65L),
        createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt")
    ) }

    private fun readReports(): List<ReportCard> = decodeList(KEY_REPORTS) { o -> ReportCard(
        id = o.optString("id"), userId = o.optString("userId"), studentId = o.optString("studentId"),
        studentName = o.optString("studentName"), studentCode = o.optString("studentCode"),
        gradeClass = o.optString("gradeClass"), parentName = o.optString("parentName"),
        parentWhatsApp = o.optString("parentWhatsApp"), schoolName = o.optString("schoolName"),
        academicTerm = o.optString("academicTerm"), attendanceSummary = o.optString("attendanceSummary"),
        subjectsSummary = o.optString("subjectsSummary"), overallScore = o.optLong("overallScore"),
        overallGrade = o.optString("overallGrade"), classPosition = o.optString("classPosition"),
        teacherRemarks = o.optString("teacherRemarks"), principalSignature = o.optString("principalSignature"),
        whatsappStatus = o.optString("whatsappStatus", "pending"), dispatchCount = o.optLong("dispatchCount"),
        lastSentAt = o.optString("lastSentAt"), createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt")
    ) }

    private fun readChat(): List<ChatMessage> = decodeList(KEY_CHAT) { o -> ChatMessage(
        id = o.optString("id"), userId = o.optString("userId"), threadKey = o.optString("threadKey"),
        studentId = o.optString("studentId"), studentName = o.optString("studentName"),
        parentName = o.optString("parentName"), parentWhatsApp = o.optString("parentWhatsApp"),
        direction = o.optString("direction", "outgoing"), messageText = o.optString("messageText"),
        attachmentFileId = o.optString("attachmentFileId"), attachmentFileName = o.optString("attachmentFileName"),
        attachmentMimeType = o.optString("attachmentMimeType"), deliveryStatus = o.optString("deliveryStatus", "sent"),
        sentTimeLabel = o.optString("sentTimeLabel"), createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt")
    ) }

    private inline fun <T> decodeList(key: String, crossinline map: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    private fun encodeList(key: String, items: List<Any>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(when (item) {
                is Student -> JSONObject().apply {
                    put("id", item.id); put("userId", item.userId); put("fullName", item.fullName)
                    put("studentCode", item.studentCode); put("gradeClass", item.gradeClass)
                    put("parentName", item.parentName); put("parentWhatsApp", item.parentWhatsApp)
                    put("attendanceDays", item.attendanceDays); put("totalTermDays", item.totalTermDays)
                    put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
                }
                is ReportCard -> JSONObject().apply {
                    put("id", item.id); put("userId", item.userId); put("studentId", item.studentId)
                    put("studentName", item.studentName); put("studentCode", item.studentCode)
                    put("gradeClass", item.gradeClass); put("parentName", item.parentName)
                    put("parentWhatsApp", item.parentWhatsApp); put("schoolName", item.schoolName)
                    put("academicTerm", item.academicTerm); put("attendanceSummary", item.attendanceSummary)
                    put("subjectsSummary", item.subjectsSummary); put("overallScore", item.overallScore)
                    put("overallGrade", item.overallGrade); put("classPosition", item.classPosition)
                    put("teacherRemarks", item.teacherRemarks); put("principalSignature", item.principalSignature)
                    put("whatsappStatus", item.whatsappStatus); put("dispatchCount", item.dispatchCount)
                    put("lastSentAt", item.lastSentAt); put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
                is ChatMessage -> JSONObject().apply {
                    put("id", item.id); put("userId", item.userId); put("threadKey", item.threadKey)
                    put("studentId", item.studentId); put("studentName", item.studentName)
                    put("parentName", item.parentName); put("parentWhatsApp", item.parentWhatsApp)
                    put("direction", item.direction); put("messageText", item.messageText)
                    put("attachmentFileId", item.attachmentFileId); put("attachmentFileName", item.attachmentFileName)
                    put("attachmentMimeType", item.attachmentMimeType)
                    put("deliveryStatus", item.deliveryStatus); put("sentTimeLabel", item.sentTimeLabel)
                    put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
                }
                else -> JSONObject()
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun requireUserId(): String = profileUserId

    private fun persistStudents() { encodeList(KEY_STUDENTS, studentsFlow.value) }
    private fun persistReports() { encodeList(KEY_REPORTS, reportsFlow.value) }
    private fun persistChat() { encodeList(KEY_CHAT, chatFlow.value) }

    fun observeStudents(): Flow<List<Student>> = studentsFlow.asStateFlow()
        .map { list -> list.sortedBy { it.fullName.lowercase() } }

    fun observeReportCards(): Flow<List<ReportCard>> = reportsFlow.asStateFlow()
        .map { list -> list.sortedByDescending { it.updatedAt.coerceAtLeast(it.createdAt) } }

    fun observeChatMessages(): Flow<List<ChatMessage>> = chatFlow.asStateFlow()
        .map { list -> list.sortedBy { it.createdAt } }

    suspend fun getStudents(): Result<List<Student>> = runCatching {
        withContext(Dispatchers.IO) { observeStudents().first() }
    }

    suspend fun getReportCards(): Result<List<ReportCard>> = runCatching {
        withContext(Dispatchers.IO) { observeReportCards().first() }
    }

    suspend fun getReportCardById(ownerUserId: String, reportId: String): Result<ReportCard?> = runCatching {
        reportsFlow.value.firstOrNull { it.id == reportId }
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
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val student = Student(
            id = id,
            userId = uid,
            fullName = fullName.trim().take(100),
            studentCode = studentCode.trim().take(40),
            gradeClass = gradeClass.trim().take(50),
            parentName = parentName.trim().take(100),
            parentWhatsApp = parentWhatsApp.trim().take(30),
            attendanceDays = attendanceDays.coerceIn(0L, 366L),
            totalTermDays = totalTermDays.coerceIn(1L, 366L),
            createdAt = now,
            updatedAt = now
        )
        studentsFlow.value = studentsFlow.value + student
        persistStudents()
        id
    }

    suspend fun updateStudent(student: Student): Result<Unit> = runCatching {
        require(student.id.isNotBlank()) { "Student ID must not be blank for update" }
        studentsFlow.value = studentsFlow.value.map {
            if (it.id == student.id) student.copy(updatedAt = System.currentTimeMillis()) else it
        }
        persistStudents()
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> = runCatching {
        studentsFlow.value = studentsFlow.value.filterNot { it.id == studentId }
        persistStudents()
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
        val id = UUID.randomUUID().toString()
        val validRows = subjectRows.filter { it.subjectName.isNotBlank() }
        val avgScore = if (validRows.isNotEmpty()) {
            (validRows.sumOf { it.score.coerceIn(0, 100) } / validRows.size).toLong().coerceIn(0L, 100L)
        } else {
            85L
        }
        val now = System.currentTimeMillis()
        val report = ReportCard(
            id = id,
            userId = uid,
            studentId = student.id.ifBlank { "stu_manual" }.take(128),
            studentName = student.fullName.trim().take(100),
            studentCode = student.studentCode.trim().take(40),
            gradeClass = student.gradeClass.trim().take(50),
            parentName = student.parentName.trim().take(100),
            parentWhatsApp = student.parentWhatsApp.trim().take(30),
            schoolName = schoolName.trim().ifBlank { "Winners Family School" }.take(120),
            academicTerm = academicTerm.trim().ifBlank { "Term 1 • 2026/2027" }.take(60),
            attendanceSummary = student.formattedAttendance.take(40),
            subjectsSummary = encodeSubjectsSummary(validRows),
            overallScore = avgScore,
            overallGrade = calculateLetterGrade(avgScore.toInt()).take(10),
            classPosition = classPosition.trim().ifBlank { "Top 10%" }.take(30),
            teacherRemarks = teacherRemarks.trim().ifBlank { "Shows strong commitment to academic growth." }.take(500),
            principalSignature = principalSignature.trim().ifBlank { "Office of the Principal" }.take(100),
            whatsappStatus = "pending",
            dispatchCount = 0L,
            lastSentAt = "",
            createdAt = now,
            updatedAt = now
        )
        reportsFlow.value = reportsFlow.value + report
        persistReports()
        id
    }

    suspend fun markReportCardSent(reportCard: ReportCard, status: String = "sent"): Result<Unit> = runCatching {
        require(reportCard.id.isNotBlank()) { "ReportCard ID must not be blank" }
        val formattedTime = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date())
        val nextCount = (reportCard.dispatchCount + 1L).coerceAtMost(1000L)
        val safeStatus = if (status in listOf("pending", "sent", "reminded")) status else "sent"
        reportsFlow.value = reportsFlow.value.map {
            if (it.id == reportCard.id) it.copy(
                whatsappStatus = safeStatus,
                dispatchCount = nextCount,
                lastSentAt = formattedTime.take(60),
                updatedAt = System.currentTimeMillis()
            ) else it
        }
        persistReports()
    }

    suspend fun deleteReportCard(reportId: String): Result<Unit> = runCatching {
        reportsFlow.value = reportsFlow.value.filterNot { it.id == reportId }
        persistReports()
    }

    /**
     * Imports a parsed roster, skipping students already present (matched on
     * normalised phone number) so a re-import is safe.
     *
     * Returns the number of students actually added.
     */
    suspend fun importStudents(entries: List<RosterParser.ParsedStudent>): Result<Int> = runCatching {
        if (entries.isEmpty()) return@runCatching 0
        val uid = requireUserId()
        val now = System.currentTimeMillis()
        val existingPhones = studentsFlow.value.mapNotNull { normalizePhone(it.parentWhatsApp) }.toSet()

        val added = entries.filter { entry ->
            val p = normalizePhone(entry.parentWhatsApp)
            p == null || p !in existingPhones
        }

        val created = added.map { entry ->
            Student(
                id = UUID.randomUUID().toString(),
                userId = uid,
                fullName = entry.fullName.take(100),
                studentCode = entry.studentCode.ifBlank { "WFS-${System.currentTimeMillis() % 100000}" }.take(40),
                gradeClass = entry.gradeClass.ifBlank { "BECE 2027" }.take(50),
                parentName = entry.parentName.ifBlank { "Parent/Guardian" }.take(100),
                parentWhatsApp = entry.parentWhatsApp,
                attendanceDays = 65L,
                totalTermDays = 65L,
                createdAt = now,
                updatedAt = now
            )
        }

        if (created.isNotEmpty()) {
            studentsFlow.value = studentsFlow.value + created
            persistStudents()
        }
        created.size
    }

    private fun normalizePhone(raw: String): String? = when {
        raw.isBlank() -> null
        else -> RosterParser.normalizeGhanaNumber(raw)
    }

    suspend fun seedSampleSchoolData(): Result<Unit> = runCatching {
        if (studentsFlow.value.isNotEmpty()) return@runCatching

        val uid = requireUserId()
        val now = System.currentTimeMillis()
        // Real BECE 2027 roster for Winners Family School (41 students).
        // Students with no parent contact yet keep an empty WhatsApp number.
        val roster = listOf(
            Triple("WFS-BECE27-001", "ABRAHAM EZAN NYAMEAYE", "+233248907473"),
            Triple("WFS-BECE27-002", "ACKON CHRISTIANA NANA AMA NHYIRA OWUSUWAA", "+233531867572"),
            Triple("WFS-BECE27-003", "ADDEY JOSEPHINE AKUA BABYIE", "+233538604588"),
            Triple("WFS-BECE27-004", "ADU-BOAHEN DANIEL KWAKU", "+233555288127"),
            Triple("WFS-BECE27-005", "AFOAKWA KINGSLEY OHENE AKESE", "+233277085003"),
            Triple("WFS-BECE27-006", "ADJEI RANSFORD APPIAH", "+233531561451"),
            Triple("WFS-BECE27-007", "AIDOO KINGSLEY NANA KWEKU", "+233248013336"),
            Triple("WFS-BECE27-008", "AKUTUSI PRINCESS BRYANA IMORO", "+233244898939"),
            Triple("WFS-BECE27-009", "AKWADAH MIRACLE PENIEL", "+233544803281"),
            Triple("WFS-BECE27-010", "AMANBOHWE MCNELLE", "+233591464349"),
            Triple("WFS-BECE27-011", "AMENUKU QUEEN LORLORNYO", "+233240490005"),
            Triple("WFS-BECE27-012", "ANANE ANGELA NANA ADJOA", "+233242223245"),
            Triple("WFS-BECE27-013", "ANIM YESULEEN ANATE", "+233556370307"),
            Triple("WFS-BECE27-014", "ANKUVIE THEODORE GREAT ANGEL ENYONAM NHYIRA", "+233535135855"),
            Triple("WFS-BECE27-015", "ANNAN EPHRAIM BOAKYE", "+233242424551"),
            Triple("WFS-BECE27-016", "AYIKPA PRINCELLA DOYOE", ""),
            Triple("WFS-BECE27-017", "BADASU DENNIS NUKPLEM GODWINS", "+233242284984"),
            Triple("WFS-BECE27-018", "BOATENG PRINCE ACHEMPONG", "+233244656132"),
            Triple("WFS-BECE27-019", "BOATENG SEFA ISAAC RIGORBERT NANA KOFI AGYEMAN", "+233540546542"),
            Triple("WFS-BECE27-020", "BRUCE NOWELL NII ODARTEY", "+233257004175"),
            Triple("WFS-BECE27-021", "BUABENG BRIGHT FOSU", "+233553477947"),
            Triple("WFS-BECE27-022", "CHASANTY HALISA HABIL", "+233557202129"),
            Triple("WFS-BECE27-023", "COFIE GABRIELLE NAKI", "+233244111594"),
            Triple("WFS-BECE27-024", "DADSON ZILLAH KWEGYIREBA", "+233247810007"),
            Triple("WFS-BECE27-025", "ESIAKU EMMANUEL GERALD", "+233549515031"),
            Triple("WFS-BECE27-026", "KUMOR KHLOE EMEFA", "+233242945725"),
            Triple("WFS-BECE27-027", "MAVOR KAREN NHYIRA", "+233545257688"),
            Triple("WFS-BECE27-028", "MIREKU VIDA AWURA ESI", "+233241797236"),
            Triple("WFS-BECE27-029", "NYAMEDOR EMMANUELLA", "+233244889491"),
            Triple("WFS-BECE27-030", "OFORIWAA NANCY", "+233553843928"),
            Triple("WFS-BECE27-031", "OKRAH TETTEH CHRISTABEL ABIGAIL", "+233543701713"),
            Triple("WFS-BECE27-032", "OPOKU ALBERTA AMPOMAH", "+233249321910"),
            Triple("WFS-BECE27-033", "OWUSU LOIS TUTU", "+233555533067"),
            Triple("WFS-BECE27-034", "QUAIDOO NHYIRA", "+233242238567"),
            Triple("WFS-BECE27-035", "SAKYI CHRISTABEL MAFIA GLADYS", "+233242365365"),
            Triple("WFS-BECE27-036", "SOMUAH CHRISTABEL OFOSUAH NANA ADJOA", "+233244384545"),
            Triple("WFS-BECE27-037", "TAWIAH EMMANUEL KOFI", "+233243277271"),
            Triple("WFS-BECE27-038", "TETTEH STEPHANIE OKAIKOR", "+233240075738"),
            Triple("WFS-BECE27-039", "TOHOUNGOLIME ELIZABETH", "+233550314623"),
            Triple("WFS-BECE27-040", "WILSON WILHELMINA", "+233243855880"),
            Triple("WFS-BECE27-041", "YEBOAH KINGSLEY KOFI AGYAPONG", "")
        )
        val sampleStudents = roster.map { (code, name, phone) ->
            Student(
                id = UUID.randomUUID().toString(),
                userId = uid,
                fullName = name,
                studentCode = code,
                gradeClass = "BECE 2027",
                parentName = "Parent of ${name.takeWhile { it != ' ' }}",
                parentWhatsApp = phone,
                attendanceDays = 65L,
                totalTermDays = 65L,
                createdAt = now,
                updatedAt = now
            )
        }
        studentsFlow.value = sampleStudents
        persistStudents()

        val sentTimestamp = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date())
        val sampleReports = listOf(
            Triple(sampleStudents[0],
                listOf(
                    SubjectGradeRow("Mathematics", 94, "A+", "Exceptional problem solver"),
                    SubjectGradeRow("Integrated Science", 89, "A", "Strong laboratory inquiry"),
                    SubjectGradeRow("English Language", 86, "A", "Articulate written expression"),
                    SubjectGradeRow("Computing & ICT", 96, "A+", "Top coding project in class"),
                    SubjectGradeRow("Social Studies", 85, "A", "Thoughtful civic discussions")
                ),
                Triple("1st of 41", "Demonstrates exemplary leadership, curiosity, and academic discipline across all subjects.", "pending" to 0L)
            ),
            Triple(sampleStudents[1],
                listOf(
                    SubjectGradeRow("Mathematics", 88, "A", "Consistent accuracy"),
                    SubjectGradeRow("Integrated Science", 92, "A+", "Outstanding research project"),
                    SubjectGradeRow("English Language", 95, "A+", "Debate team captain"),
                    SubjectGradeRow("Computing & ICT", 84, "A", "Great digital presentation"),
                    SubjectGradeRow("Social Studies", 91, "A+", "Insightful historical analysis")
                ),
                Triple("2nd of 41", "Had 100% attendance this term and continues to inspire her peers with her communication skills.", "pending" to 0L)
            ),
            Triple(sampleStudents[2],
                listOf(
                    SubjectGradeRow("Mathematics", 78, "B+", "Good progress in algebra"),
                    SubjectGradeRow("Integrated Science", 82, "A", "Active in practicals"),
                    SubjectGradeRow("English Language", 76, "B+", "Steady reading improvement"),
                    SubjectGradeRow("Computing & ICT", 88, "A", "Enthusiastic learner"),
                    SubjectGradeRow("Social Studies", 81, "A", "Well-prepared assignments")
                ),
                Triple("6th of 41", "Has made commendable strides this term. Extra practice in algebra will propel them into the top 3.", "sent" to 1L)
            )
        )

        val built = sampleReports.map { (stu, rows, meta) ->
            val (position, remarks, statusPair) = meta
            val (status, count) = statusPair
            val avgScore = (rows.sumOf { it.score } / rows.size).toLong()
            ReportCard(
                id = UUID.randomUUID().toString(),
                userId = uid,
                studentId = stu.id,
                studentName = stu.fullName,
                studentCode = stu.studentCode,
                gradeClass = stu.gradeClass,
                parentName = stu.parentName,
                parentWhatsApp = stu.parentWhatsApp,
                schoolName = "Winners Family School",
                academicTerm = "Term 1 • 2026/2027",
                attendanceSummary = stu.formattedAttendance,
                subjectsSummary = encodeSubjectsSummary(rows),
                overallScore = avgScore,
                overallGrade = calculateLetterGrade(avgScore.toInt()),
                classPosition = position,
                teacherRemarks = remarks,
                principalSignature = "Smith Sampson, Principal",
                whatsappStatus = status,
                dispatchCount = count,
                lastSentAt = if (status == "sent") sentTimestamp else "",
                createdAt = now,
                updatedAt = now
            )
        }
        reportsFlow.value = built
        persistReports()
    }

    suspend fun migrateDefaultSchoolNameIfNeeded(): Result<Unit> = runCatching {
        val updated = reportsFlow.value.map { report ->
            if (report.schoolName == "Royal Crest International School") {
                report.copy(schoolName = "Winners Family School", updatedAt = System.currentTimeMillis())
            } else if (report.principalSignature == "Dr. E. Amedzro, Principal") {
                report.copy(principalSignature = "Smith Sampson, Principal", updatedAt = System.currentTimeMillis())
            } else {
                report
            }
        }
        if (updated != reportsFlow.value) {
            reportsFlow.value = updated
            persistReports()
        }
    }

    /**
     * One-time migration: replace the original 4 placeholder students
     * (studentCode GP-2026-10x) with the real BECE 2027 roster. Runs once per
     * user profile; afterwards the roster is left untouched.
     */
    suspend fun migratePlaceholderRosterIfNeeded(): Result<Unit> = runCatching {
        val current = studentsFlow.value
        val hasPlaceholder = current.any { it.studentCode.startsWith("GP-2026-10") }
        if (!hasPlaceholder) {
            // Backfill verified bio-data numbers that were dropped by earlier 027-prefix filtering
            // and clean any legacy "Parent/Guardian" labels on existing installs.
            val backfilledPhones = mapOf(
                "WFS-BECE27-005" to "+233277085003" // AFOAKWA KINGSLEY OHENE AKESE (0277085003)
            )
            var changed = false
            val updated = current.map { stu ->
                var next = stu
                val missingPhone = backfilledPhones[stu.studentCode]
                if (missingPhone != null && next.parentWhatsApp.isBlank()) {
                    next = next.copy(parentWhatsApp = missingPhone)
                    changed = true
                }
                if (next.parentName.startsWith("Parent/Guardian of ")) {
                    next = next.copy(parentName = next.parentName.replace("Parent/Guardian of ", "Parent of "))
                    changed = true
                } else if (next.parentName == "Parent/Guardian") {
                    next = next.copy(parentName = "Parent of ${next.fullName.takeWhile { it != ' ' }}")
                    changed = true
                }
                next
            }
            if (changed) {
                studentsFlow.value = updated
                persistStudents()
            }
            return@runCatching
        }

        val realCodes = listOf(
            "WFS-BECE27-001", "WFS-BECE27-002", "WFS-BECE27-003", "WFS-BECE27-004",
            "WFS-BECE27-005", "WFS-BECE27-006", "WFS-BECE27-007", "WFS-BECE27-008",
            "WFS-BECE27-009", "WFS-BECE27-010", "WFS-BECE27-011", "WFS-BECE27-012",
            "WFS-BECE27-013", "WFS-BECE27-014", "WFS-BECE27-015", "WFS-BECE27-016",
            "WFS-BECE27-017", "WFS-BECE27-018", "WFS-BECE27-019", "WFS-BECE27-020",
            "WFS-BECE27-021", "WFS-BECE27-022", "WFS-BECE27-023", "WFS-BECE27-024",
            "WFS-BECE27-025", "WFS-BECE27-026", "WFS-BECE27-027", "WFS-BECE27-028",
            "WFS-BECE27-029", "WFS-BECE27-030", "WFS-BECE27-031", "WFS-BECE27-032",
            "WFS-BECE27-033", "WFS-BECE27-034", "WFS-BECE27-035", "WFS-BECE27-036",
            "WFS-BECE27-037", "WFS-BECE27-038", "WFS-BECE27-039", "WFS-BECE27-040",
            "WFS-BECE27-041"
        ).toSet()
        if (current.any { it.studentCode in realCodes }) {
            // Also clean up any legacy "Parent/Guardian" labels on already-seeded profiles
            val hasParentGuardian = current.any { it.parentName.startsWith("Parent/Guardian") }
            if (hasParentGuardian) {
                studentsFlow.value = current.map { stu ->
                    if (stu.parentName.startsWith("Parent/Guardian of ")) {
                        stu.copy(parentName = stu.parentName.replace("Parent/Guardian of ", "Parent of "))
                    } else if (stu.parentName == "Parent/Guardian") {
                        stu.copy(parentName = "Parent of ${stu.fullName.takeWhile { it != ' ' }}")
                    } else {
                        stu
                    }
                }
                persistStudents()
            }
            return@runCatching
        }

        seedSampleSchoolData().getOrThrow()
        // Drop placeholder report cards that reference the removed sample students.
        reportsFlow.value = reportsFlow.value.filter { it.studentCode !in setOf("GP-2026-101", "GP-2026-102", "GP-2026-103", "GP-2026-104") }
        persistReports()
    }

    suspend fun updateSchoolBrandingOnReportCards(
        schoolName: String,
        academicTerm: String,
        principalSignature: String
    ): Result<Unit> = runCatching {
        if (reportsFlow.value.isEmpty()) return@runCatching
        val safeSchool = schoolName.trim().ifBlank { "Winners Family School" }.take(120)
        val safeTerm = academicTerm.trim().ifBlank { "Term 1 • 2026/2027" }.take(60)
        val safeSig = principalSignature.trim().ifBlank { "Office of the Principal" }.take(100)
        reportsFlow.value = reportsFlow.value.map {
            it.copy(
                schoolName = safeSchool,
                academicTerm = safeTerm,
                principalSignature = safeSig,
                updatedAt = System.currentTimeMillis()
            )
        }
        persistReports()
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
        val id = UUID.randomUUID().toString()
        val safePhone = parentWhatsApp.trim().ifBlank { "+2330000000" }.take(30)
        val safeThread = threadKey.trim().ifBlank { normalizeThreadKey(safePhone, studentId) }.take(60)
        val safeDirection = if (direction in listOf("outgoing", "incoming")) direction else "outgoing"
        val safeStatus = if (deliveryStatus in listOf("sent", "delivered", "received", "opened")) deliveryStatus else "sent"
        val timeLabel = SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date()).take(60)
        val now = System.currentTimeMillis()

        val message = ChatMessage(
            id = id,
            userId = uid,
            threadKey = safeThread,
            studentId = studentId.trim().take(128),
            studentName = studentName.trim().ifBlank { "Student" }.take(100),
            parentName = parentName.trim().ifBlank { "Parent / Guardian" }.take(100),
            parentWhatsApp = safePhone,
            direction = safeDirection,
            messageText = messageText.trim().ifBlank { "Report card dispatched" }.take(1500),
            attachmentFileId = attachmentFileId.trim().take(128),
            attachmentFileName = attachmentFileName.trim().take(140),
            attachmentMimeType = attachmentMimeType.trim().take(60),
            deliveryStatus = safeStatus,
            sentTimeLabel = timeLabel,
            createdAt = now,
            updatedAt = now
        )
        chatFlow.value = chatFlow.value + message
        persistChat()
        id
    }

    suspend fun deleteChatMessage(messageId: String): Result<Unit> = runCatching {
        chatFlow.value = chatFlow.value.filterNot { it.id == messageId }
        persistChat()
    }

    suspend fun deleteChatThread(threadKey: String): Result<Unit> = runCatching {
        chatFlow.value = chatFlow.value.filterNot { it.threadKey == threadKey }
        persistChat()
    }
}

/** Consumed by [GradePulseViewModel.provideFactory]; kept for API compatibility. */
fun gradePulseRepository(context: Context, userId: String): GradePulseRepository =
    GradePulseRepository(context, userId)
