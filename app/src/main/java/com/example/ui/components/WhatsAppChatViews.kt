package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Student
import com.example.data.model.UploadedReportFile
import com.example.data.model.normalizeThreadKey
import com.example.ui.theme.WhatsAppBlueTick
import com.example.ui.theme.WhatsAppBubbleLight
import com.example.ui.theme.WhatsAppChatBg
import com.example.ui.theme.WhatsAppDivider
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppMutedGray
import com.example.util.WhatsAppDispatcher

data class ActiveChatTarget(
    val threadKey: String,
    val studentId: String,
    val studentName: String,
    val parentName: String,
    val parentWhatsApp: String
)

@Composable
fun WhatsAppChatsTab(
    threads: List<ChatThread>,
    students: List<Student>,
    searchQuery: String,
    onOpenThread: (ActiveChatTarget) -> Unit,
    onStartNewChatClick: () -> Unit
) {
    val dividerColor = if (isSystemInDarkTheme()) Color(0xFF222E35) else WhatsAppDivider

    val filteredThreads = remember(threads, searchQuery) {
        if (searchQuery.isBlank()) threads
        else threads.filter {
            it.parentName.contains(searchQuery, ignoreCase = true) ||
                it.studentName.contains(searchQuery, ignoreCase = true) ||
                it.lastMessageText.contains(searchQuery, ignoreCase = true) ||
                it.parentWhatsApp.contains(searchQuery, ignoreCase = true)
        }
    }

    val activeThreadKeys = remember(threads) { threads.map { it.threadKey }.toSet() }
    val unchattedParents = remember(students, activeThreadKeys, searchQuery) {
        students.filter { stu ->
            val key = normalizeThreadKey(stu.parentWhatsApp, stu.id)
            key !in activeThreadKeys && (
                searchQuery.isBlank() ||
                    stu.parentName.contains(searchQuery, ignoreCase = true) ||
                    stu.fullName.contains(searchQuery, ignoreCase = true) ||
                    stu.parentWhatsApp.contains(searchQuery, ignoreCase = true)
                )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        if (filteredThreads.isEmpty() && unchattedParents.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 48.dp),
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
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Text(
                        text = "No Parent Chats Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Send a report card from your Folder or start a new WhatsApp chat with any parent. Full delivery & message history will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onStartNewChatClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Start Parent Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(filteredThreads, key = { it.threadKey }) { thread ->
                WhatsAppThreadListRow(
                    thread = thread,
                    dividerColor = dividerColor,
                    onClick = {
                        onOpenThread(
                            ActiveChatTarget(
                                threadKey = thread.threadKey,
                                studentId = thread.studentId,
                                studentName = thread.studentName,
                                parentName = thread.parentName,
                                parentWhatsApp = thread.parentWhatsApp
                            )
                        )
                    }
                )
            }

            if (unchattedParents.isNotEmpty()) {
                item {
                    Text(
                        text = if (filteredThreads.isEmpty()) "START A CHAT WITH PARENTS" else "MORE PARENTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                    )
                }
                items(unchattedParents, key = { "contact_${it.id}" }) { stu ->
                    val initials = remember(stu.parentName) {
                        stu.parentName.split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.take(1).uppercase() }.ifBlank { "P" }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenThread(
                                    ActiveChatTarget(
                                        threadKey = normalizeThreadKey(stu.parentWhatsApp, stu.id),
                                        studentId = stu.id,
                                        studentName = stu.fullName,
                                        parentName = stu.parentName,
                                        parentWhatsApp = stu.parentWhatsApp
                                    )
                                )
                            }
                            .testTag("start_chat_parent_${stu.studentCode}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stu.parentName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Parent of ${stu.fullName} • ${stu.parentWhatsApp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                            color = dividerColor,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppThreadListRow(
    thread: ChatThread,
    dividerColor: Color,
    onClick: () -> Unit
) {
    val initials = remember(thread.parentName) {
        thread.parentName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { "P" }
    }
    val previewSummary = remember(thread.lastAttachmentName, thread.lastMessageText) {
        if (thread.lastAttachmentName.isNotBlank()) {
            "📎 ${thread.lastAttachmentName} • ${thread.lastMessageText.replace("\n", " ")}"
        } else {
            thread.lastMessageText.replace("\n", " ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_thread_row_${thread.threadKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.parentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = thread.lastTimeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thread.lastDirection == "outgoing") {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Sent",
                            tint = WhatsAppBlueTick,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = previewSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = WhatsAppGreenAccent
                    ) {
                        Text(
                            text = thread.messageCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
fun ParentChatThreadScreen(
    target: ActiveChatTarget,
    messages: List<ChatMessage>,
    uploadedFiles: List<UploadedReportFile>,
    schoolName: String,
    academicTerm: String,
    principalSignature: String,
    onBack: () -> Unit,
    onSendMessage: (messageText: String, attachedFile: UploadedReportFile?) -> Unit,
    onLogParentReply: (replyText: String) -> Unit,
    onOpenAttachmentPreview: (UploadedReportFile) -> Unit,
    onClearChatHistory: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val chatBackground = if (isDark) Color(0xFF0B141A) else WhatsAppChatBg
    val cardSurface = if (isDark) Color(0xFF1F2C34) else Color.White
    val primaryText = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val mutedText = if (isDark) Color(0xFF8696A0) else WhatsAppMutedGray
    val outgoingBubble = if (isDark) Color(0xFF005C4B) else WhatsAppBubbleLight
    val incomingBubble = if (isDark) Color(0xFF1F2C34) else Color.White
    val bubbleMetaColor = if (isDark) Color(0xFFA8C7BE) else WhatsAppMutedGray

    var draftMessage by remember(target.threadKey) { mutableStateOf("") }
    var attachedFile by remember(target.threadKey) { mutableStateOf<UploadedReportFile?>(null) }
    var showPickFileDialog by remember { mutableStateOf(false) }
    var showLogReplyDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    val quickTemplates = listOf(
        "Please confirm receipt of the report card.",
        "Reminder: Parent-Teacher conference this week.",
        "Thank you for supporting ${target.studentName} this term."
    )

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
            // WhatsApp Conversation Top Bar
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Chats"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = target.parentName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.parentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${target.studentName} • ${target.parentWhatsApp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showPickFileDialog = true },
                        modifier = Modifier.testTag("chat_attach_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach Report Card from Folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showLogReplyDialog = true },
                        modifier = Modifier.testTag("chat_log_reply_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Record Parent Reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearChatHistory) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Scrollable Chat History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardSurface.copy(alpha = 0.94f),
                            shadowElevation = 0.5.dp
                        ) {
                            Text(
                                text = "$schoolName • $academicTerm",
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardSurface.copy(alpha = 0.94f),
                                shadowElevation = 0.5.dp
                            ) {
                                Text(
                                    text = "Send a report card from your Folder or type a WhatsApp message below. Every sent report card and message is saved here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isOutgoing = msg.direction != "incoming"
                        val linkedFile = remember(msg.attachmentFileId, uploadedFiles) {
                            if (msg.attachmentFileId.isNotBlank()) {
                                uploadedFiles.find { it.id == msg.attachmentFileId }
                            } else null
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = if (isOutgoing) {
                                    RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                                } else {
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
                                },
                                color = if (isOutgoing) outgoingBubble else incomingBubble,
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(min = 120.dp, max = 315.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    if (msg.attachmentFileName.isNotBlank()) {
                                        val isPdf = msg.attachmentMimeType.contains("pdf", ignoreCase = true) ||
                                            msg.attachmentFileName.endsWith(".pdf", ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = cardSurface.copy(alpha = 0.72f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .let { mod ->
                                                    if (linkedFile != null) {
                                                        mod.clickable { onOpenAttachmentPreview(linkedFile) }
                                                    } else mod
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = if (isPdf) Color(0xFFD93025) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = msg.attachmentFileName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = primaryText,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (linkedFile != null) "Tap to preview report card" else "Dispatched attachment",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = mutedText
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = msg.messageText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryText
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.align(Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg.sentTimeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOutgoing) bubbleMetaColor else mutedText
                                        )
                                        if (isOutgoing) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = "Sent on WhatsApp",
                                                tint = WhatsAppBlueTick,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Message Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickTemplates) { template ->
                    AssistChip(
                        onClick = { draftMessage = template },
                        label = {
                            Text(
                                text = template,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = CircleShape
                    )
                }
            }

            // Selected Attachment Chip
            if (attachedFile != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = cardSurface,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (attachedFile!!.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                            contentDescription = null,
                            tint = if (attachedFile!!.isPdf) Color(0xFFD93025) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attached: ${attachedFile!!.fileName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { attachedFile = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // WhatsApp Pill Composer + Circular Green Send Button
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draftMessage.isEmpty()) {
                                Text(
                                    text = "Message ${target.parentName}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = draftMessage,
                                onValueChange = { draftMessage = it },
                                textStyle = TextStyle(color = primaryText, fontSize = 15.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_message_input")
                            )
                        }
                        IconButton(onClick = { showPickFileDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach Report Card",
                                tint = mutedText
                            )
                        }
                    }
                }

                val canSend = draftMessage.isNotBlank() || attachedFile != null
                Surface(
                    onClick = {
                        if (!canSend) {
                            showPickFileDialog = true
                            return@Surface
                        }
                        val fileToSend = attachedFile
                        val textToSend = draftMessage.trim()
                        if (fileToSend != null) {
                            val targetedFile = fileToSend.copy(
                                studentName = target.studentName,
                                parentName = target.parentName,
                                parentWhatsApp = target.parentWhatsApp
                            )
                            WhatsAppDispatcher.launchWhatsAppWithUploadedFile(
                                context = context,
                                file = targetedFile,
                                schoolName = schoolName,
                                academicTerm = academicTerm,
                                principalSignature = principalSignature,
                                customIntroNote = textToSend
                            )
                            onSendMessage(textToSend, fileToSend)
                        } else {
                            WhatsAppDispatcher.launchWhatsAppDirectPlainText(
                                context = context,
                                parentWhatsApp = target.parentWhatsApp,
                                parentName = target.parentName,
                                message = textToSend
                            )
                            onSendMessage(textToSend, null)
                        }
                        draftMessage = ""
                        attachedFile = null
                    },
                    shape = CircleShape,
                    color = WhatsAppGreenAccent,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send on WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPickFileDialog) {
        PickReportFromFolderDialog(
            files = uploadedFiles,
            onDismiss = { showPickFileDialog = false },
            onSelectFile = { picked ->
                attachedFile = picked
                showPickFileDialog = false
            }
        )
    }

    if (showLogReplyDialog) {
        LogParentReplyDialog(
            parentName = target.parentName,
            onDismiss = { showLogReplyDialog = false },
            onSaveReply = { reply ->
                onLogParentReply(reply)
                showLogReplyDialog = false
            }
        )
    }
}

@Composable
private fun PickReportFromFolderDialog(
    files: List<UploadedReportFile>,
    onDismiss: () -> Unit,
    onSelectFile: (UploadedReportFile) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attach from Report Folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (files.isEmpty()) {
                    Text(
                        text = "No uploaded report cards in your folder yet. Upload files in the Folder tab first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        files.forEach { file ->
                            Surface(
                                onClick = { onSelectFile(file) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (file.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = if (file.isPdf) Color(0xFFD93025) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.displayStudentOrFile,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.fileName} • ${file.formattedSize}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogParentReplyDialog(
    parentName: String,
    onDismiss: () -> Unit,
    onSaveReply: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Record Reply from $parentName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Parent's WhatsApp Reply") },
                    placeholder = { Text("e.g., Received with thanks!") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (replyText.isNotBlank()) onSaveReply(replyText) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save Reply")
                    }
                }
            }
        }
    }
}

@Composable
fun StartNewChatDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onStartChat: (ActiveChatTarget) -> Unit
) {
    var parentName by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var parentWhatsApp by remember { mutableStateOf("+233") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 660.dp),
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
                    Text(
                        text = "New Parent WhatsApp Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (students.isNotEmpty()) {
                    Text(
                        text = "Saved Parents",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    students.forEach { stu ->
                        Surface(
                            onClick = {
                                onStartChat(
                                    ActiveChatTarget(
                                        threadKey = normalizeThreadKey(stu.parentWhatsApp, stu.id),
                                        studentId = stu.id,
                                        studentName = stu.fullName,
                                        parentName = stu.parentName,
                                        parentWhatsApp = stu.parentWhatsApp
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stu.parentName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${stu.fullName} • ${stu.parentWhatsApp}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Chat",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                Text(
                    text = "Or Message New Number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Parent / Guardian Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = studentName,
                    onValueChange = { studentName = it },
                    label = { Text("Student Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parentWhatsApp,
                    onValueChange = { parentWhatsApp = it },
                    label = { Text("WhatsApp Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (parentWhatsApp.filter { it.isDigit() }.length < 7) {
                                errorMsg = "Enter a valid WhatsApp phone number."
                            } else {
                                onStartChat(
                                    ActiveChatTarget(
                                        threadKey = normalizeThreadKey(parentWhatsApp),
                                        studentId = "",
                                        studentName = studentName.ifBlank { "Student" },
                                        parentName = parentName.ifBlank { "Parent / Guardian" },
                                        parentWhatsApp = parentWhatsApp.trim()
                                    )
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open Chat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
