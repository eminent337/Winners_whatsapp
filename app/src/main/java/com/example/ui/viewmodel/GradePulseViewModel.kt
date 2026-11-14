package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ReportCard
import com.example.data.model.Student
import com.example.data.model.SubjectGradeRow
import com.example.data.model.UploadedReportFile
import com.example.data.model.buildUploadedFileWhatsAppCaption
import com.example.data.model.normalizeThreadKey
import com.example.data.RosterParser
import com.example.data.repository.GradePulseRepository
import com.example.data.repository.UploadedFolderRepository
import com.example.service.WhatsAppDispatchResult
import com.example.service.WhatsAppDispatchService
import com.example.data.repository.gradePulseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SchoolSettingsState(
    val schoolName: String = "Winners Family School",
    val academicTerm: String = "Term 1 • 2026/2027",
    val principalSignature: String = "Smith Sampson, Principal",
    val customWhatsAppGreeting: String = "",
    val statusBannerMessage: String? = null,
    val isSeeding: Boolean = false,
    val isSaving: Boolean = false
)

class GradePulseViewModel(
    private val repository: GradePulseRepository,
    private val folderRepository: UploadedFolderRepository,
    val currentUserId: String
) : ViewModel() {

    private val _schoolSettings = MutableStateFlow(SchoolSettingsState())
    val schoolSettings: StateFlow<SchoolSettingsState> = _schoolSettings.asStateFlow()

    val uploadedFiles: StateFlow<List<UploadedReportFile>> = folderRepository.files

    val studentsState: StateFlow<UiState<List<Student>>> = repository.observeStudents()
        .onEach { students ->
            if (students.isNotEmpty()) {
                folderRepository.autoMatchUnmatched(students)
            }
        }
        .map<List<Student>, UiState<List<Student>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            val rootError = generateSequence(error) { it.cause }
                .filterIsInstance<Exception>()
                .firstOrNull() ?: error
            Log.w(TAG, "Error observing students", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load students"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val reportCardsState: StateFlow<UiState<List<ReportCard>>> = repository.observeReportCards()
        .map<List<ReportCard>, UiState<List<ReportCard>>> { list ->
            val latest = list.firstOrNull()
            if (latest != null &&
                _schoolSettings.value.schoolName == "Winners Family School" &&
                latest.schoolName != "Royal Crest International School"
            ) {
                _schoolSettings.update { current ->
                    current.copy(
                        schoolName = latest.schoolName,
                        academicTerm = latest.academicTerm,
                        principalSignature = if (latest.principalSignature == "Dr. E. Amedzro, Principal") {
                            current.principalSignature
                        } else {
                            latest.principalSignature.ifBlank { current.principalSignature }
                        }
                    )
                }
            }
            UiState.Success(list)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            val rootError = generateSequence(error) { it.cause }
                .filterIsInstance<Exception>()
                .firstOrNull() ?: error
            Log.w(TAG, "Error observing report cards", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load report cards"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> = repository.observeChatMessages()
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            val rootError = generateSequence(error) { it.cause }
                .filterIsInstance<Exception>()
                .firstOrNull() ?: error
            Log.w(TAG, "Error observing chat messages", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load chat history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val chatThreads: StateFlow<List<ChatThread>> = combine(
        chatMessagesState,
        studentsState
    ) { msgState, stuState ->
        val messages = (msgState as? UiState.Success)?.data ?: emptyList()
        val students = (stuState as? UiState.Success)?.data ?: emptyList()

        val grouped = messages.groupBy { it.threadKey }
        grouped.map { (threadKey, msgs) ->
            val latest = msgs.last()
            val matchedStudent = students.find {
                it.id == latest.studentId ||
                    normalizeThreadKey(it.parentWhatsApp, it.id) == threadKey
            }
            ChatThread(
                threadKey = threadKey,
                studentId = matchedStudent?.id ?: latest.studentId,
                studentName = matchedStudent?.fullName ?: latest.studentName,
                parentName = matchedStudent?.parentName ?: latest.parentName,
                parentWhatsApp = matchedStudent?.parentWhatsApp ?: latest.parentWhatsApp,
                lastMessageText = latest.messageText,
                lastAttachmentName = latest.attachmentFileName,
                lastTimeLabel = latest.sentTimeLabel,
                lastDirection = latest.direction,
                lastDeliveryStatus = latest.deliveryStatus,
                messageCount = msgs.size,
                lastTimestampSeconds = latest.createdAt.coerceAtLeast(0L)
            )
        }.sortedByDescending { it.lastTimestampSeconds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    init {
        // Automatically seed sample school roster on first launch and migrate legacy placeholder school name
        viewModelScope.launch {
            repository.seedSampleSchoolData()
            repository.migrateDefaultSchoolNameIfNeeded()
        }
        viewModelScope.launch {
            WhatsAppDispatchService.dispatchEvents.collect { event ->
                when (val result = event?.result) {
                    is WhatsAppDispatchResult.Failed -> {
                        showBannerMessage("WhatsApp dispatch error: ${result.reason}")
                        WhatsAppDispatchService.clearLastDispatchEvent()
                    }
                    is WhatsAppDispatchResult.DispatchedViaFallback -> {
                        showBannerMessage("Opened ${result.channelLabel} for ${result.recipientLabel}.")
                        WhatsAppDispatchService.clearLastDispatchEvent()
                    }
                    is WhatsAppDispatchResult.DispatchedToWhatsApp, null -> Unit
                }
            }
        }
    }

    fun updateSchoolConfig(
        schoolName: String,
        academicTerm: String,
        principalSignature: String,
        customGreeting: String
    ) {
        val cleanSchool = schoolName.trim().ifBlank { "Winners Family School" }
        val cleanTerm = academicTerm.trim().ifBlank { "Term 1 • 2026/2027" }
        val cleanSig = principalSignature.trim().ifBlank { "Office of the Principal" }
        _schoolSettings.update {
            it.copy(
                schoolName = cleanSchool,
                academicTerm = cleanTerm,
                principalSignature = cleanSig,
                customWhatsAppGreeting = customGreeting.trim(),
                statusBannerMessage = "School & WhatsApp template settings updated."
            )
        }
        viewModelScope.launch {
            repository.updateSchoolBrandingOnReportCards(cleanSchool, cleanTerm, cleanSig)
        }
    }

    fun clearBannerMessage() {
        _schoolSettings.update { it.copy(statusBannerMessage = null) }
    }

    fun showBannerMessage(message: String) {
        _schoolSettings.update { it.copy(statusBannerMessage = message) }
    }

    /** Parses pasted roster text and imports the valid rows. */
    fun importRoster(rawText: String, gradeClass: String = "BECE 2027") {
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val parsed = RosterParser.parse(rawText, gradeClass)
            val result = repository.importStudents(parsed.students)
            val skippedCount = parsed.skipped.size

            val message = if (result.isSuccess) {
                val added = result.getOrDefault(0)
                buildString {
                    append("Imported $added student(s).")
                    if (skippedCount > 0) append(" Skipped $skippedCount row(s) with no valid phone.")
                }
            } else {
                "Import failed: ${result.exceptionOrNull()?.localizedMessage}"
            }

            _schoolSettings.update {
                it.copy(isSaving = false, statusBannerMessage = message)
            }
        }
    }

    fun seedSampleRoster() {
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSeeding = true) }
            val result = repository.seedSampleSchoolData()
            _schoolSettings.update {
                it.copy(
                    isSeeding = false,
                    statusBannerMessage = if (result.isSuccess) {
                        "Sample school roster & report cards loaded!"
                    } else {
                        "Could not load sample data: ${result.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
        }
    }

    fun addStudent(
        fullName: String,
        studentCode: String,
        gradeClass: String,
        parentName: String,
        parentWhatsApp: String,
        attendanceDays: Long,
        totalTermDays: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val res = repository.createStudent(
                fullName = fullName,
                studentCode = studentCode,
                gradeClass = gradeClass,
                parentName = parentName,
                parentWhatsApp = parentWhatsApp,
                attendanceDays = attendanceDays,
                totalTermDays = totalTermDays
            )
            _schoolSettings.update {
                it.copy(
                    isSaving = false,
                    statusBannerMessage = if (res.isSuccess) {
                        "Added student $fullName (${parentName})"
                    } else {
                        "Failed to add student: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
            if (res.isSuccess) onComplete()
        }
    }

    /**
     * Update an existing student — corrects a wrong parent number, a misspelt
     * name or a missing contact without deleting and re-adding the row.
     */
    fun updateStudent(
        student: Student,
        fullName: String,
        studentCode: String,
        gradeClass: String,
        parentName: String,
        parentWhatsApp: String,
        attendanceDays: Long,
        totalTermDays: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val updated = student.copy(
                fullName = fullName,
                studentCode = studentCode,
                gradeClass = gradeClass,
                parentName = parentName,
                parentWhatsApp = parentWhatsApp,
                attendanceDays = attendanceDays,
                totalTermDays = totalTermDays
            )
            val res = repository.updateStudent(updated)
            _schoolSettings.update {
                it.copy(
                    isSaving = false,
                    statusBannerMessage = if (res.isSuccess) {
                        "Updated $fullName — WhatsApp ${parentWhatsApp}"
                    } else {
                        "Failed to update student: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
            if (res.isSuccess) onComplete()
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentId)
            showBannerMessage("Student removed from roster.")
        }
    }

    fun deleteStudents(studentIds: List<String>) {
        if (studentIds.isEmpty()) return
        viewModelScope.launch {
            studentIds.forEach { id ->
                repository.deleteStudent(id)
            }
            val label = if (studentIds.size == 1) {
                "1 parent contact deleted."
            } else {
                "${studentIds.size} parent contacts deleted."
            }
            showBannerMessage(label)
        }
    }

    fun createReportCard(
        student: Student,
        subjectRows: List<SubjectGradeRow>,
        classPosition: String,
        teacherRemarks: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val cfg = _schoolSettings.value
            val res = repository.createReportCard(
                student = student,
                schoolName = cfg.schoolName,
                academicTerm = cfg.academicTerm,
                subjectRows = subjectRows,
                classPosition = classPosition,
                teacherRemarks = teacherRemarks,
                principalSignature = cfg.principalSignature
            )
            _schoolSettings.update {
                it.copy(
                    isSaving = false,
                    statusBannerMessage = if (res.isSuccess) {
                        "Report card created for ${student.fullName}!"
                    } else {
                        "Error saving report card: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
            if (res.isSuccess) onComplete()
        }
    }

    fun markReportCardDispatched(reportCard: ReportCard, status: String = "sent") {
        viewModelScope.launch {
            repository.markReportCardSent(reportCard, status)
            showBannerMessage("WhatsApp dispatch logged for ${reportCard.parentName} (${reportCard.studentName}).")
        }
    }

    fun deleteReportCard(reportId: String) {
        viewModelScope.launch {
            repository.deleteReportCard(reportId)
            showBannerMessage("Report card deleted.")
        }
    }

    fun importReportDocuments(uris: List<Uri>, folderName: String = _schoolSettings.value.academicTerm) {
        if (uris.isEmpty()) return
        val students = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val result = folderRepository.importDocumentUris(uris, folderName, students)
            _schoolSettings.update { it.copy(isSaving = false) }
            result.onSuccess { imported ->
                val matchedCount = imported.count { it.hasAssignedRecipient }
                val msg = if (imported.isEmpty()) {
                    "No supported PDF or image report cards found in selection."
                } else if (matchedCount > 0) {
                    "Loaded ${imported.size} report card(s) into folder ($matchedCount auto-matched to parents)."
                } else {
                    "Loaded ${imported.size} report card(s) into folder."
                }
                showBannerMessage(msg)
            }.onFailure { err ->
                showBannerMessage("Could not upload files: ${err.localizedMessage}")
            }
        }
    }

    fun importReportFolderTree(treeUri: Uri) {
        val students = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        val fallbackName = _schoolSettings.value.academicTerm
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSaving = true) }
            val result = folderRepository.importDeviceFolderTree(treeUri, fallbackName, students)
            _schoolSettings.update { it.copy(isSaving = false) }
            result.onSuccess { imported ->
                if (imported.isEmpty()) {
                    showBannerMessage("No PDF or image report cards found inside that folder.")
                } else {
                    val matched = imported.count { it.hasAssignedRecipient }
                    showBannerMessage("Imported ${imported.size} report card(s) into \"${imported.first().folderName}\" ($matched matched).")
                }
            }.onFailure { err ->
                showBannerMessage("Could not import folder: ${err.localizedMessage}")
            }
        }
    }

    fun assignFileRecipient(
        fileId: String,
        studentName: String,
        parentName: String,
        parentWhatsApp: String,
        studentId: String = ""
    ) {
        viewModelScope.launch {
            folderRepository.assignRecipient(
                fileId = fileId,
                studentName = studentName,
                parentName = parentName,
                parentWhatsApp = parentWhatsApp,
                studentId = studentId
            )
            showBannerMessage("Assigned parent WhatsApp ($parentWhatsApp) to report card.")
        }
    }

    fun markUploadedFilesDispatched(
        fileIds: List<String>,
        customCaption: String = _schoolSettings.value.customWhatsAppGreeting
    ) {
        viewModelScope.launch {
            val allFiles = folderRepository.files.value
            val dispatchedFiles = allFiles.filter { it.id in fileIds }
            folderRepository.markDispatched(fileIds)

            val cfg = _schoolSettings.value
            for (file in dispatchedFiles) {
                if (file.hasAssignedRecipient) {
                    val caption = buildUploadedFileWhatsAppCaption(
                        file = file,
                        schoolName = cfg.schoolName,
                        academicTerm = cfg.academicTerm,
                        principalSignature = cfg.principalSignature,
                        customIntroNote = customCaption
                    )
                    repository.logChatMessage(
                        threadKey = normalizeThreadKey(file.parentWhatsApp, file.matchedStudentId),
                        studentId = file.matchedStudentId,
                        studentName = file.displayStudentOrFile,
                        parentName = file.parentName.ifBlank { "Parent / Guardian" },
                        parentWhatsApp = file.parentWhatsApp,
                        direction = "outgoing",
                        messageText = caption,
                        attachmentFileId = file.id,
                        attachmentFileName = file.fileName,
                        attachmentMimeType = file.mimeType,
                        deliveryStatus = "sent"
                    )
                }
            }

            if (fileIds.size == 1) {
                showBannerMessage("Report card sent & saved to chat history.")
            } else if (fileIds.isNotEmpty()) {
                showBannerMessage("Sent ${fileIds.size} report cards & updated chat history.")
            }
        }
    }

    fun sendChatMessage(
        threadKey: String,
        studentId: String,
        studentName: String,
        parentName: String,
        parentWhatsApp: String,
        messageText: String,
        attachedFile: UploadedReportFile? = null
    ) {
        viewModelScope.launch {
            val cfg = _schoolSettings.value
            val finalBody = if (attachedFile != null) {
                buildUploadedFileWhatsAppCaption(
                    file = attachedFile.copy(
                        studentName = studentName,
                        parentName = parentName,
                        parentWhatsApp = parentWhatsApp
                    ),
                    schoolName = cfg.schoolName,
                    academicTerm = cfg.academicTerm,
                    principalSignature = cfg.principalSignature,
                    customIntroNote = messageText
                )
            } else {
                messageText.trim()
            }

            repository.logChatMessage(
                threadKey = threadKey,
                studentId = studentId,
                studentName = studentName,
                parentName = parentName,
                parentWhatsApp = parentWhatsApp,
                direction = "outgoing",
                messageText = finalBody,
                attachmentFileId = attachedFile?.id.orEmpty(),
                attachmentFileName = attachedFile?.fileName.orEmpty(),
                attachmentMimeType = attachedFile?.mimeType.orEmpty(),
                deliveryStatus = "sent"
            )
            if (attachedFile != null) {
                folderRepository.markDispatched(listOf(attachedFile.id))
            }
        }
    }

    fun logIncomingParentReply(
        threadKey: String,
        studentId: String,
        studentName: String,
        parentName: String,
        parentWhatsApp: String,
        replyText: String
    ) {
        if (replyText.isBlank()) return
        viewModelScope.launch {
            repository.logChatMessage(
                threadKey = threadKey,
                studentId = studentId,
                studentName = studentName,
                parentName = parentName,
                parentWhatsApp = parentWhatsApp,
                direction = "incoming",
                messageText = replyText.trim(),
                deliveryStatus = "received"
            )
            showBannerMessage("Parent reply recorded in chat history.")
        }
    }

    fun deleteChatMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteChatMessage(messageId)
        }
    }

    fun deleteChatThread(threadKey: String) {
        viewModelScope.launch {
            repository.deleteChatThread(threadKey)
            showBannerMessage("Chat history cleared.")
        }
    }

    fun deleteUploadedFiles(fileIds: List<String>) {
        viewModelScope.launch {
            folderRepository.deleteFiles(fileIds)
            showBannerMessage("Removed ${fileIds.size} file(s) from folder.")
        }
    }

    fun moveUploadedFilesToFolder(fileIds: List<String>, targetFolder: String) {
        viewModelScope.launch {
            folderRepository.moveFilesToFolder(fileIds, targetFolder)
            showBannerMessage("Moved ${fileIds.size} file(s) to \"$targetFolder\".")
        }
    }

    fun generateSamplePdfReports() {
        val cfg = _schoolSettings.value
        val students = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _schoolSettings.update { it.copy(isSeeding = true) }
            val result = folderRepository.generateSamplePdfReportCards(
                schoolName = cfg.schoolName,
                academicTerm = cfg.academicTerm,
                principalSignature = cfg.principalSignature,
                students = students
            )
            _schoolSettings.update { it.copy(isSeeding = false) }
            result.onSuccess { created ->
                showBannerMessage("Added ${created.size} sample PDF report cards to folder.")
            }.onFailure { err ->
                showBannerMessage("Could not create sample PDFs: ${err.localizedMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "GradePulseVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                GradePulseViewModel(
                    repository = gradePulseRepository(app.applicationContext, currentUserId),
                    folderRepository = UploadedFolderRepository(app.applicationContext, currentUserId),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
