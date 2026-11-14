package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Student
import com.example.data.model.UploadedReportFile
import com.example.data.model.normalizeThreadKey
import com.example.ui.components.ActiveChatTarget
import com.example.ui.components.AssignParentDialog
import com.example.ui.components.BatchSelectedSendDialog
import com.example.ui.components.CreateStudentDialog
import com.example.ui.components.ParentChatThreadScreen
import com.example.ui.components.StartNewChatDialog
import com.example.ui.components.UploadedFolderTab
import com.example.ui.components.UploadedReportPreviewScreen
import com.example.ui.components.WhatsAppChatsTab
import com.example.ui.theme.WhatsAppDivider
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppTealDark
import com.example.ui.viewmodel.GradePulseViewModel
import com.example.ui.viewmodel.SchoolSettingsState
import com.example.ui.viewmodel.UiState
import com.example.util.WhatsAppDispatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradePulseMainScreen(
    currentUserId: String,
    userDisplayName: String?,
    onSignOut: () -> Unit,
    viewModel: GradePulseViewModel = viewModel(
        key = currentUserId,
        factory = GradePulseViewModel.provideFactory(currentUserId)
    )
) {
    val context = LocalContext.current
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val uploadedFiles by viewModel.uploadedFiles.collectAsStateWithLifecycle()
    val chatMessagesState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val chatThreads by viewModel.chatThreads.collectAsStateWithLifecycle()
    val schoolSettings by viewModel.schoolSettings.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Chats, 1=Folder, 2=Parents, 3=School
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previewFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeChatTarget by remember { mutableStateOf<ActiveChatTarget?>(null) }
    var fileForRecipientAssign by remember { mutableStateOf<UploadedReportFile?>(null) }
    var showCreateStudentDialog by remember { mutableStateOf(false) }
    var showStartNewChatDialog by remember { mutableStateOf(false) }
    var showBatchSelectedSendDialog by remember { mutableStateOf(false) }

    val studentsList = (studentsState as? UiState.Success)?.data ?: emptyList()
    val allChatMessages = (chatMessagesState as? UiState.Success)?.data ?: emptyList()

    val multiDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importReportDocuments(uris, schoolSettings.academicTerm)
        }
    }

    val folderTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            viewModel.importReportFolderTree(treeUri)
        }
    }

    val validSelectedIds = remember(uploadedFiles, selectedFileIds) {
        val allIds = uploadedFiles.map { it.id }.toSet()
        selectedFileIds.intersect(allIds)
    }

    // Full-screen uploaded PDF/Image preview takes priority (can be opened from Folder OR from a Chat bubble!)
    val activePreviewFile = previewFileId?.let { id -> uploadedFiles.find { it.id == id } }
    if (activePreviewFile != null) {
        UploadedReportPreviewScreen(
            file = activePreviewFile,
            schoolName = schoolSettings.schoolName,
            academicTerm = schoolSettings.academicTerm,
            principalSignature = schoolSettings.principalSignature,
            defaultIntroNote = schoolSettings.customWhatsAppGreeting,
            onBack = { previewFileId = null },
            onRequestAssignParent = { fileForRecipientAssign = activePreviewFile },
            onMarkDispatched = { ids -> viewModel.markUploadedFilesDispatched(ids) },
            onDeleteFile = { id ->
                viewModel.deleteUploadedFiles(listOf(id))
                previewFileId = null
            }
        )
        fileForRecipientAssign?.let { target ->
            AssignParentDialog(
                file = target,
                students = studentsList,
                onDismiss = { fileForRecipientAssign = null },
                onAssign = { stuName, parentName, parentWhatsApp, stuId ->
                    viewModel.assignFileRecipient(target.id, stuName, parentName, parentWhatsApp, stuId)
                    fileForRecipientAssign = null
                }
            )
        }
        return
    }

    // Full-screen WhatsApp-style Parent Chat Conversation & History Screen
    val currentChat = activeChatTarget
    if (currentChat != null) {
        val threadMessages = remember(allChatMessages, currentChat.threadKey) {
            allChatMessages.filter { it.threadKey == currentChat.threadKey }
        }
        ParentChatThreadScreen(
            target = currentChat,
            messages = threadMessages,
            uploadedFiles = uploadedFiles,
            schoolName = schoolSettings.schoolName,
            academicTerm = schoolSettings.academicTerm,
            principalSignature = schoolSettings.principalSignature,
            onBack = { activeChatTarget = null },
            onSendMessage = { messageText, attachedFile ->
                viewModel.sendChatMessage(
                    threadKey = currentChat.threadKey,
                    studentId = currentChat.studentId,
                    studentName = currentChat.studentName,
                    parentName = currentChat.parentName,
                    parentWhatsApp = currentChat.parentWhatsApp,
                    messageText = messageText,
                    attachedFile = attachedFile
                )
            },
            onLogParentReply = { replyText ->
                viewModel.logIncomingParentReply(
                    threadKey = currentChat.threadKey,
                    studentId = currentChat.studentId,
                    studentName = currentChat.studentName,
                    parentName = currentChat.parentName,
                    parentWhatsApp = currentChat.parentWhatsApp,
                    replyText = replyText
                )
            },
            onOpenAttachmentPreview = { file ->
                previewFileId = file.id
            },
            onClearChatHistory = {
                viewModel.deleteChatThread(currentChat.threadKey)
            }
        )
        return
    }

    if (selectedTab == 1 && validSelectedIds.isNotEmpty()) {
        BackHandler { selectedFileIds = emptySet() }
    }

    val dividerColor = if (isSystemInDarkTheme()) Color(0xFF222E35) else WhatsAppDivider
    val isSelectionMode = selectedTab == 1 && validSelectedIds.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selectedFileIds = emptySet() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Clear selection",
                                    tint = Color.White
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "${validSelectedIds.size} selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    selectedFileIds = if (validSelectedIds.size == uploadedFiles.size) {
                                        emptySet()
                                    } else {
                                        uploadedFiles.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.testTag("select_all_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Select All",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    val chosen = uploadedFiles.filter { it.id in validSelectedIds }
                                    if (chosen.size == 1) {
                                        val single = chosen.first()
                                        WhatsAppDispatcher.launchWhatsAppWithUploadedFile(
                                            context = context,
                                            file = single,
                                            schoolName = schoolSettings.schoolName,
                                            academicTerm = schoolSettings.academicTerm,
                                            principalSignature = schoolSettings.principalSignature,
                                            customIntroNote = schoolSettings.customWhatsAppGreeting
                                        )
                                        viewModel.markUploadedFilesDispatched(listOf(single.id))
                                        selectedFileIds = emptySet()
                                    } else if (chosen.size > 1) {
                                        showBatchSelectedSendDialog = true
                                    }
                                },
                                modifier = Modifier.testTag("send_selected_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Selected on WhatsApp",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.deleteUploadedFiles(validSelectedIds.toList())
                                    selectedFileIds = emptySet()
                                },
                                modifier = Modifier.testTag("delete_selected_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Selected",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = WhatsAppTealDark
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = schoolSettings.schoolName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = schoolSettings.academicTerm,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            if (selectedTab != 3) {
                                IconButton(
                                    onClick = {
                                        searchVisible = !searchVisible
                                        if (!searchVisible) searchQuery = ""
                                    },
                                    modifier = Modifier.testTag("top_search_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    AnimatedVisibility(visible = searchVisible && selectedTab != 3) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search chats, report files, or parents...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("search_reports_input")
                            )
                        }
                    }
                }
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_tab_chats")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folder") },
                        label = { Text("Folder") },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_tab_folder")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Groups, contentDescription = "Parents") },
                        label = { Text("Parents") },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_tab_students")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "School") },
                        label = { Text("School") },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_tab_school")
                    )
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = { showStartNewChatDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = WhatsAppGreenAccent,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_new_parent_chat")
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = "New Parent Chat")
                    }
                }
                1 -> {
                    FloatingActionButton(
                        onClick = {
                            multiDocPicker.launch(arrayOf("application/pdf", "image/*"))
                        },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = WhatsAppGreenAccent,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_upload_report_cards")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload Report Cards")
                    }
                }
                2 -> {
                    FloatingActionButton(
                        onClick = { showCreateStudentDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = WhatsAppGreenAccent,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_add_student")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Parent Contact")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = schoolSettings.statusBannerMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = schoolSettings.statusBannerMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearBannerMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss message",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> WhatsAppChatsTab(
                    threads = chatThreads,
                    students = studentsList,
                    searchQuery = searchQuery,
                    onOpenThread = { target -> activeChatTarget = target },
                    onStartNewChatClick = { showStartNewChatDialog = true }
                )
                1 -> UploadedFolderTab(
                    files = uploadedFiles,
                    selectedFileIds = validSelectedIds,
                    searchQuery = searchQuery,
                    schoolName = schoolSettings.schoolName,
                    academicTerm = schoolSettings.academicTerm,
                    principalSignature = schoolSettings.principalSignature,
                    customIntroNote = schoolSettings.customWhatsAppGreeting,
                    isBusy = schoolSettings.isSaving || schoolSettings.isSeeding,
                    onUploadFilesClick = {
                        multiDocPicker.launch(arrayOf("application/pdf", "image/*"))
                    },
                    onImportFolderClick = {
                        folderTreePicker.launch(null)
                    },
                    onGenerateSamplePdfs = {
                        viewModel.generateSamplePdfReports()
                    },
                    onOpenFilePreview = { file ->
                        previewFileId = file.id
                    },
                    onToggleSelectFile = { fileId ->
                        selectedFileIds = if (fileId in selectedFileIds) {
                            selectedFileIds - fileId
                        } else {
                            selectedFileIds + fileId
                        }
                    },
                    onRequestAssignRecipient = { file ->
                        fileForRecipientAssign = file
                    },
                    onMarkFilesDispatched = { ids ->
                        viewModel.markUploadedFilesDispatched(ids)
                    }
                )
                2 -> NeatStudentsDirectoryTab(
                    studentsState = studentsState,
                    searchQuery = searchQuery,
                    onAddStudent = { showCreateStudentDialog = true },
                    onOpenChatForStudent = { stu ->
                        activeChatTarget = ActiveChatTarget(
                            threadKey = normalizeThreadKey(stu.parentWhatsApp, stu.id),
                            studentId = stu.id,
                            studentName = stu.fullName,
                            parentName = stu.parentName,
                            parentWhatsApp = stu.parentWhatsApp
                        )
                    },
                    onDeleteStudent = { viewModel.deleteStudent(it) }
                )
                3 -> NeatSchoolSettingsTab(
                    settings = schoolSettings,
                    userDisplayName = userDisplayName,
                    onSaveSettings = { school, term, sig, greeting ->
                        viewModel.updateSchoolConfig(school, term, sig, greeting)
                    },
                    onSeedSampleRoster = { viewModel.seedSampleRoster() },
                    onGenerateSamplePdfs = { viewModel.generateSamplePdfReports() }
                )
            }
        }
    }

    if (showStartNewChatDialog) {
        StartNewChatDialog(
            students = studentsList,
            onDismiss = { showStartNewChatDialog = false },
            onStartChat = { target ->
                showStartNewChatDialog = false
                activeChatTarget = target
            }
        )
    }

    if (showCreateStudentDialog) {
        CreateStudentDialog(
            onDismiss = { showCreateStudentDialog = false },
            onConfirm = { fullName, code, gradeClass, parentName, parentWhatsApp, attended, total ->
                viewModel.addStudent(
                    fullName = fullName,
                    studentCode = code,
                    gradeClass = gradeClass,
                    parentName = parentName,
                    parentWhatsApp = parentWhatsApp,
                    attendanceDays = attended,
                    totalTermDays = total,
                    onComplete = { showCreateStudentDialog = false }
                )
            }
        )
    }

    fileForRecipientAssign?.let { target ->
        AssignParentDialog(
            file = target,
            students = studentsList,
            onDismiss = { fileForRecipientAssign = null },
            onAssign = { stuName, parentName, parentWhatsApp, stuId ->
                viewModel.assignFileRecipient(target.id, stuName, parentName, parentWhatsApp, stuId)
                fileForRecipientAssign = null
            }
        )
    }

    if (showBatchSelectedSendDialog) {
        val chosenFiles = uploadedFiles.filter { it.id in validSelectedIds }
        BatchSelectedSendDialog(
            selectedFiles = chosenFiles,
            schoolName = schoolSettings.schoolName,
            academicTerm = schoolSettings.academicTerm,
            principalSignature = schoolSettings.principalSignature,
            customIntroNote = schoolSettings.customWhatsAppGreeting,
            onDismiss = {
                showBatchSelectedSendDialog = false
                selectedFileIds = emptySet()
            },
            onMarkDispatched = { ids ->
                viewModel.markUploadedFilesDispatched(ids)
            }
        )
    }
}

@Composable
private fun NeatStudentsDirectoryTab(
    studentsState: UiState<List<Student>>,
    searchQuery: String,
    onAddStudent: () -> Unit,
    onOpenChatForStudent: (Student) -> Unit,
    onDeleteStudent: (String) -> Unit
) {
    val allStudents = (studentsState as? UiState.Success)?.data ?: emptyList()
    val filteredStudents = remember(allStudents, searchQuery) {
        allStudents.filter { stu ->
            searchQuery.isBlank() ||
                stu.fullName.contains(searchQuery, ignoreCase = true) ||
                stu.parentName.contains(searchQuery, ignoreCase = true) ||
                stu.gradeClass.contains(searchQuery, ignoreCase = true) ||
                stu.parentWhatsApp.contains(searchQuery, ignoreCase = true)
        }
    }
    val dividerColor = if (isSystemInDarkTheme()) Color(0xFF222E35) else WhatsAppDivider

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "Tap any parent to open their WhatsApp chat history, or upload report cards in Folder to auto-match student names.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (filteredStudents.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No parent contacts yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onAddStudent,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Add Student & Parent WhatsApp")
                    }
                }
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                val initials = remember(student.fullName) {
                    student.fullName.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.take(1).uppercase() }
                        .ifBlank { "S" }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChatForStudent(student) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${student.parentName} • ${student.parentWhatsApp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${student.gradeClass} • ID ${student.studentCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onOpenChatForStudent(student) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Open Parent Chat",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onDeleteStudent(student.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Student",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                        color = dividerColor,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun NeatSchoolSettingsTab(
    settings: SchoolSettingsState,
    userDisplayName: String?,
    onSaveSettings: (String, String, String, String) -> Unit,
    onSeedSampleRoster: () -> Unit,
    onGenerateSamplePdfs: () -> Unit
) {
    var schoolName by remember(settings.schoolName) { mutableStateOf(settings.schoolName) }
    var academicTerm by remember(settings.academicTerm) { mutableStateOf(settings.academicTerm) }
    var principalSignature by remember(settings.principalSignature) { mutableStateOf(settings.principalSignature) }
    var customGreeting by remember(settings.customWhatsAppGreeting) { mutableStateOf(settings.customWhatsAppGreeting) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "School Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = settings.schoolName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Signed in as ${userDisplayName ?: "Educator"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("School Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_school_name_input")
                )

                OutlinedTextField(
                    value = academicTerm,
                    onValueChange = { academicTerm = it },
                    label = { Text("Current Academic Term & Year") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_academic_term_input")
                )

                OutlinedTextField(
                    value = principalSignature,
                    onValueChange = { principalSignature = it },
                    label = { Text("Principal Signatory") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_principal_signature_input")
                )

                OutlinedTextField(
                    value = customGreeting,
                    onValueChange = { customGreeting = it },
                    label = { Text("Default WhatsApp Caption Note (Optional)") },
                    placeholder = {
                        Text("Leave blank to auto-personalize per parent")
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_whatsapp_greeting_input")
                )

                Button(
                    onClick = {
                        onSaveSettings(schoolName, academicTerm, principalSignature, customGreeting)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_school_settings_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreenAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save School Settings", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onGenerateSamplePdfs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sample_pdfs_button"),
                    shape = CircleShape
                ) {
                    Text("Add Sample PDF Report Cards to Folder")
                }

                OutlinedButton(
                    onClick = onSeedSampleRoster,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("seed_sample_roster_button"),
                    shape = CircleShape
                ) {
                    Text("Load Sample Parent Contacts")
                }
            }
        }
    }
}
