package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Student
import com.example.data.model.UploadedReportFile
import com.example.data.model.buildUploadedFileWhatsAppCaption
import com.example.ui.theme.WhatsAppBlueTick
import com.example.ui.theme.WhatsAppBubbleLight
import com.example.ui.theme.WhatsAppChatBg
import com.example.ui.theme.WhatsAppDivider
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppMutedGray
import com.example.ui.theme.WhatsAppTealDark
import com.example.util.WhatsAppDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UploadedFolderTab(
    files: List<UploadedReportFile>,
    selectedFileIds: Set<String>,
    searchQuery: String,
    schoolName: String,
    academicTerm: String,
    principalSignature: String,
    customIntroNote: String,
    isBusy: Boolean,
    onUploadFilesClick: () -> Unit,
    onImportFolderClick: () -> Unit,
    onGenerateSamplePdfs: () -> Unit,
    onOpenFilePreview: (UploadedReportFile) -> Unit,
    onToggleSelectFile: (String) -> Unit,
    onRequestAssignRecipient: (UploadedReportFile) -> Unit,
    onMarkFilesDispatched: (List<String>) -> Unit,
    onRequestDeleteFolder: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val dividerColor = if (isSystemInDarkTheme()) Color(0xFF222E35) else WhatsAppDivider
    var selectedFolderFilter by rememberSaveable { mutableStateOf("ALL") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, PENDING, SENT

    val folders = remember(files) {
        files.map { it.folderName }.distinct()
    }

    val filteredFiles = remember(files, searchQuery, selectedFolderFilter, statusFilter) {
        files.filter { file ->
            val matchesFolder = selectedFolderFilter == "ALL" || file.folderName == selectedFolderFilter
            val matchesStatus = when (statusFilter) {
                "PENDING" -> !file.isSent
                "SENT" -> file.isSent
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                file.fileName.contains(searchQuery, ignoreCase = true) ||
                file.studentName.contains(searchQuery, ignoreCase = true) ||
                file.parentName.contains(searchQuery, ignoreCase = true) ||
                file.folderName.contains(searchQuery, ignoreCase = true)
            matchesFolder && matchesStatus && matchesSearch
        }
    }

    val isSelectionMode = selectedFileIds.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Upload & Import Folder Action Strip
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onUploadFilesClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreenAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_report_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload Reports",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onImportFolderClick,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_report_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Load Folder",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (files.isNotEmpty()) {
                        val sentCount = files.count { it.isSent }
                        val pendingCount = (files.size - sentCount).coerceAtLeast(0)
                        Text(
                            text = if (isSelectionMode) {
                                "Tap files to select or unselect, then tap Send at the top"
                            } else {
                                "${files.size} loaded • $sentCount sent • $pendingCount pending • Hold any file to multi-select"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isBusy) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = WhatsAppGreenAccent
                )
            }
        }

        // Folder & Status Filter Pills
        if (files.isNotEmpty()) {
            item {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFolderFilter == "ALL" && statusFilter == "ALL",
                            onClick = {
                                selectedFolderFilter = "ALL"
                                statusFilter = "ALL"
                            },
                            label = { Text("All (${files.size})") },
                            shape = CircleShape,
                            border = null,
                            colors = chipColors
                        )
                    }
                    item {
                        val pendingCount = files.count { !it.isSent }
                        FilterChip(
                            selected = statusFilter == "PENDING",
                            onClick = {
                                statusFilter = if (statusFilter == "PENDING") "ALL" else "PENDING"
                            },
                            label = { Text("Pending ($pendingCount)") },
                            shape = CircleShape,
                            border = null,
                            colors = chipColors
                        )
                    }
                    item {
                        val sentCount = files.count { it.isSent }
                        FilterChip(
                            selected = statusFilter == "SENT",
                            onClick = {
                                statusFilter = if (statusFilter == "SENT") "ALL" else "SENT"
                            },
                            label = { Text("Sent ($sentCount)") },
                            shape = CircleShape,
                            border = null,
                            colors = chipColors
                        )
                    }
                    items(folders) { folder ->
                        val count = files.count { it.folderName == folder }
                        FilterChip(
                            selected = selectedFolderFilter == folder,
                            onClick = {
                                selectedFolderFilter = if (selectedFolderFilter == folder) "ALL" else folder
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text("$folder ($count)") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete folder $folder",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onRequestDeleteFolder(folder) }
                                )
                            },
                            shape = CircleShape,
                            border = null,
                            colors = chipColors
                        )
                    }
                }
            }
        }

        if (files.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "Your Report Cards Folder is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Upload PDF or image report cards from your device, or pick an entire folder. Files load here automatically so you can select and send them on WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onGenerateSamplePdfs,
                        modifier = Modifier.testTag("generate_sample_pdfs_button")
                    ) {
                        Text(
                            text = "Try with 3 Sample Report Card PDFs",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else if (filteredFiles.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No matching report cards in this folder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = {
                        selectedFolderFilter = "ALL"
                        statusFilter = "ALL"
                    }) {
                        Text("Show All Uploaded Files")
                    }
                }
            }
        } else {
            items(filteredFiles, key = { it.id }) { file ->
                val isSelected = file.id in selectedFileIds
                WhatsAppFolderFileRow(
                    file = file,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    dividerColor = dividerColor,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelectFile(file.id)
                        } else {
                            onOpenFilePreview(file)
                        }
                    },
                    onLongClick = {
                        onToggleSelectFile(file.id)
                    },
                    onAvatarClick = {
                        onToggleSelectFile(file.id)
                    },
                    onQuickSend = {
                        // Forward-style: always ask who to send to first.
                        onRequestAssignRecipient(file)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WhatsAppFolderFileRow(
    file: UploadedReportFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    dividerColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onQuickSend: () -> Unit
) {
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("uploaded_file_row_${file.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type badge or WhatsApp check badge (tappable to select!)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> WhatsAppGreenAccent
                                file.isPdf -> Color(0xFFFDECEC)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isSelected -> Icons.Default.Check
                            file.isZip -> Icons.Default.FolderZip
                            file.isPdf -> Icons.Default.PictureAsPdf
                            file.isDocument -> Icons.Default.Description
                            file.isImage -> Icons.Default.Image
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = if (isSelected) "Selected" else file.fileName,
                        tint = when {
                            isSelected -> Color.White
                            file.isZip -> Color(0xFFB26A00)
                            file.isPdf -> Color(0xFFD93025)
                            file.isDocument -> Color(0xFF1A73E8)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.displayStudentOrFile,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${file.typeBadge} • ${file.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = if (file.isSent) "Sent" else "Pending",
                        tint = if (file.isSent) WhatsAppBlueTick else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (file.hasAssignedRecipient) {
                            "${file.parentName.ifBlank { "Parent" }} • ${file.parentWhatsApp}"
                        } else {
                            "Tap send to assign parent WhatsApp"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (file.hasAssignedRecipient) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onQuickSend,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_send_uploaded_${file.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (file.isSent) MaterialTheme.colorScheme.primaryContainer else WhatsAppGreenAccent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send ${file.fileName} on WhatsApp",
                            tint = if (file.isSent) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp, end = 16.dp),
            color = dividerColor,
            thickness = 0.5.dp
        )
    }
}

@Composable
fun UploadedReportPreviewScreen(
    file: UploadedReportFile,
    latestFile: () -> UploadedReportFile?,
    schoolName: String,
    academicTerm: String,
    principalSignature: String,
    defaultIntroNote: String,
    onBack: () -> Unit,
    onRequestSelectParent: () -> Unit,
    onMarkDispatched: (List<String>) -> Unit,
    onDeleteFile: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val currentFile = latestFile() ?: file

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val chatBackground = if (isDark) Color(0xFF0B141A) else WhatsAppChatBg
    val cardSurface = if (isDark) Color(0xFF1F2C34) else Color.White
    val primaryText = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val mutedText = if (isDark) Color(0xFF8696A0) else WhatsAppMutedGray
    val bubbleColor = if (isDark) Color(0xFF005C4B) else WhatsAppBubbleLight
    val bubbleMetaColor = if (isDark) Color(0xFFA8C7BE) else WhatsAppMutedGray

    var customParentNote by remember(file.id, defaultIntroNote) {
        mutableStateOf(defaultIntroNote)
    }
    var currentPage by remember(file.id) { mutableIntStateOf(0) }
    var totalPages by remember(file.id) { mutableIntStateOf(1) }
    var previewBitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember(file.id) { mutableStateOf(true) }

    LaunchedEffect(file.localFilePath, currentPage, file.isPdf) {
        isLoadingPreview = true
        val loaded = withContext(Dispatchers.IO) {
            if (file.isPdf) {
                val (bmp, count) = WhatsAppDispatcher.renderPdfPageToBitmap(file.localFilePath, currentPage)
                bmp to count.coerceAtLeast(1)
            } else {
                WhatsAppDispatcher.decodeImagePreviewBitmap(file.localFilePath) to 1
            }
        }
        previewBitmap = loaded.first
        totalPages = loaded.second
        isLoadingPreview = false
    }

    val captionPreview = remember(file, schoolName, academicTerm, principalSignature, customParentNote) {
        buildUploadedFileWhatsAppCaption(
            file = file,
            schoolName = schoolName,
            academicTerm = academicTerm,
            principalSignature = principalSignature,
            customIntroNote = customParentNote
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = chatBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            // WhatsApp Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Folder"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (file.isPdf) Color(0xFFFDECEC) else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (file.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                            contentDescription = null,
                            tint = if (file.isPdf) Color(0xFFD93025) else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentFile.displayStudentOrFile,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentFile.hasAssignedRecipient) {
                                "${currentFile.parentName} • ${currentFile.parentWhatsApp}"
                            } else {
                                currentFile.fileName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onRequestSelectParent,
                        modifier = Modifier.testTag("assign_parent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Assign Parent WhatsApp",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            onDeleteFile(file.id)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete File",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cardSurface.copy(alpha = 0.94f),
                        shadowElevation = 0.5.dp
                    ) {
                        Text(
                            text = "${file.folderName} • ${file.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // Recipient Assignment Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = cardSurface,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (file.hasAssignedRecipient) "WHATSAPP RECIPIENT" else "ASSIGN PARENT WHATSAPP",
                                style = MaterialTheme.typography.labelSmall,
                                color = WhatsAppTealDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (file.hasAssignedRecipient) {
                                    "${file.parentName.ifBlank { "Parent" }} (${file.parentWhatsApp})"
                                } else {
                                    "Tap Assign to pick a parent or type their number"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onRequestSelectParent,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, WhatsAppTealDark)
                        ) {
                            Text(
                                text = if (file.hasAssignedRecipient) "Change" else "Assign",
                                color = WhatsAppTealDark,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Rendered PDF / Image Document Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardSurface,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (isLoadingPreview) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = WhatsAppTealDark)
                            }
                        } else if (previewBitmap != null) {
                            val bmp = previewBitmap!!
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = file.fileName,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(0.5.dp, WhatsAppDivider, RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ready to send: ${file.fileName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = primaryText
                                )
                            }
                        }

                        if (file.isPdf && totalPages > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (currentPage > 0) currentPage-- },
                                    enabled = currentPage > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "Page ${currentPage + 1} of $totalPages",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryText
                                )
                                IconButton(
                                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                    enabled = currentPage < totalPages - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                            }
                        }
                    }
                }

                // Outgoing WhatsApp Caption Bubble
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 4.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    ),
                    color = bubbleColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = captionPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (file.lastSentAt.isNotBlank()) file.lastSentAt else "Attached ${file.fileName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleMetaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "WhatsApp Status",
                                tint = if (file.isSent) WhatsAppBlueTick else bubbleMetaColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Confirm that the file was actually sent in WhatsApp. Shown only once a
            // parent WhatsApp is assigned, so the teacher can mark it sent after
            // sending (opening WhatsApp alone does not confirm delivery).
            if (currentFile.hasAssignedRecipient && !currentFile.isSent) {
                Surface(
                    onClick = { onMarkDispatched(listOf(currentFile.id)) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("mark_file_as_sent_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentFile.whatsappStatus == "opened") "Confirm sent to ${currentFile.parentName}" else "Mark as sent",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Bottom WhatsApp Pill Composer + Send FAB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = cardSurface,
                    shadowElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (customParentNote.isEmpty()) {
                            Text(
                                text = "Add optional caption note...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = customParentNote,
                            onValueChange = { customParentNote = it },
                            textStyle = TextStyle(color = primaryText, fontSize = 15.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Surface(
                    onClick = {
                        // Forward-style: ask who to send to BEFORE opening WhatsApp,
                        // then hand the chosen recipient straight to the send action.
                        onRequestSelectParent()
                    },
                    shape = CircleShape,
                    color = WhatsAppGreenAccent,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_uploaded_file_whatsapp_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Report Card on WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectParentDialog(
    file: UploadedReportFile,
    students: List<Student>,
    onDismiss: () -> Unit,
    onSelect: (studentName: String, parentName: String, parentWhatsApp: String, studentId: String) -> Unit
) {
    var studentName by remember(file.id) { mutableStateOf(file.studentName.ifBlank { file.displayStudentOrFile }) }
    var parentName by remember(file.id) { mutableStateOf(file.parentName) }
    var parentWhatsApp by remember(file.id) { mutableStateOf(file.parentWhatsApp.ifBlank { "+233" }) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember(file.id) { mutableStateOf("") }
    var selectedTab by remember(file.id) { mutableStateOf("suggested") }

    // Auto-match: compare the file name against student names (tokens length >= 3)
    val normalizedFile = remember(file.id) {
        file.fileName.lowercase().replace(Regex("[^a-z0-9]"), " ")
    }
    val suggestedStudents = remember(file.id, students) {
        if (students.isEmpty()) emptyList() else students.sortedByDescending { stu ->
            val tokens = stu.fullName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            tokens.count { normalizedFile.contains(it) }
        }.filter { stu ->
            val tokens = stu.fullName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            tokens.isNotEmpty() && tokens.any { normalizedFile.contains(it) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Send to Parent",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pick a parent to open WhatsApp with this file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (students.isNotEmpty()) {
                    // Two tab buttons: Suggested (default) and All Students
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == "suggested",
                            onClick = { selectedTab = "suggested" },
                            label = {
                                Text(
                                    "Suggested (${suggestedStudents.size})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = if (selectedTab == "suggested") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedTab == "all",
                            onClick = { selectedTab = "all" },
                            label = {
                                Text(
                                    "All Students (${students.size})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = if (selectedTab == "all") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    // Search field (shown on both tabs)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search student or parent") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val baseList = if (selectedTab == "suggested") suggestedStudents else students
                    val filteredStudents = remember(searchQuery, baseList) {
                        if (searchQuery.isBlank()) baseList
                        else baseList.filter {
                            it.fullName.contains(searchQuery, ignoreCase = true) ||
                                it.parentName.contains(searchQuery, ignoreCase = true) ||
                                it.parentWhatsApp.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { stu ->
                            val isSuggested = selectedTab == "suggested" &&
                                stu.fullName.lowercase().split(Regex("[^a-z0-9]+"))
                                    .filter { it.length >= 3 }
                                    .any { normalizedFile.contains(it) }
                            Surface(
                                onClick = {
                                    // Forward-style: choosing a parent sends immediately.
                                    onSelect(stu.fullName, stu.parentName, stu.parentWhatsApp, stu.id)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSuggested) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSuggested) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Suggested match",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = stu.fullName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${stu.parentName} • ${stu.parentWhatsApp}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Send",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (filteredStudents.isEmpty() && searchQuery.isNotBlank()) {
                            item {
                                Text(
                                    text = "No student matches \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else if (filteredStudents.isEmpty() && selectedTab == "suggested" && searchQuery.isBlank()) {
                            item {
                                Text(
                                    text = "No auto-match found. Try the 'All Students' tab to pick manually.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                Text(
                    text = "Or Enter Parent Number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = studentName,
                    onValueChange = { studentName = it },
                    label = { Text("Student Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Parent / Guardian Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parentWhatsApp,
                    onValueChange = { parentWhatsApp = it },
                    label = { Text("Parent WhatsApp Number *") },
                    placeholder = { Text("+233241234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (parentWhatsApp.filter { it.isDigit() }.length < 7) {
                                validationError = "Enter a valid WhatsApp number with country code."
                            } else {
                                // Forward-style: sending happens straight after picking.
                                onSelect(
                                    studentName.ifBlank { file.displayStudentOrFile },
                                    parentName.ifBlank { "Parent/Guardian" },
                                    parentWhatsApp.trim(),
                                    ""
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send on WhatsApp", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun BatchSelectedSendDialog(
    selectedFiles: List<UploadedReportFile>,
    schoolName: String,
    academicTerm: String,
    principalSignature: String,
    customIntroNote: String,
    onDismiss: () -> Unit,
    onMarkDispatched: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (selectedFiles.size - 1).coerceAtLeast(0))
    val currentFile = selectedFiles.getOrNull(safeIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Send ${selectedFiles.size} Selected Report(s)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (currentFile != null) {
                    val progress = (safeIndex + 1).toFloat() / selectedFiles.size.toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "File ${safeIndex + 1} of ${selectedFiles.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentFile.formattedSize,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = WhatsAppGreenAccent
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = currentFile.displayStudentOrFile,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentFile.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (currentFile.hasAssignedRecipient) {
                                    "To: ${currentFile.parentName} (${currentFile.parentWhatsApp})"
                                } else {
                                    "No specific parent assigned — WhatsApp lets you pick contact"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val launched = WhatsAppDispatcher.launchWhatsAppWithUploadedFile(
                                context = context,
                                file = currentFile,
                                schoolName = schoolName,
                                academicTerm = academicTerm,
                                principalSignature = principalSignature,
                                customIntroNote = customIntroNote
                            )
                            if (launched) {
                                onMarkDispatched(listOf(currentFile.id))
                            }
                            if (safeIndex < selectedFiles.size - 1) {
                                currentIndex = safeIndex + 1
                            } else {
                                onDismiss()
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("send_current_selected_file_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentFile.hasAssignedRecipient) {
                                "Send to ${currentFile.parentName}"
                            } else {
                                "Send File on WhatsApp"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val launched = WhatsAppDispatcher.shareMultipleFilesToWhatsApp(
                                    context = context,
                                    files = selectedFiles,
                                    schoolName = schoolName,
                                    academicTerm = academicTerm,
                                    principalSignature = principalSignature,
                                    customIntroNote = customIntroNote
                                )
                                if (launched) {
                                    onMarkDispatched(selectedFiles.map { it.id })
                                }
                                onDismiss()
                            },
                            shape = CircleShape
                        ) {
                            Text("Share All Together")
                        }

                        if (safeIndex < selectedFiles.size - 1) {
                            TextButton(onClick = { currentIndex = safeIndex + 1 }) {
                                Text("Skip")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.SkipNext, contentDescription = "Skip")
                            }
                        }
                    }
                }
            }
        }
    }
}
