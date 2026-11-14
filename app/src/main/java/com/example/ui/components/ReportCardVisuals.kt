package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReportCard
import com.example.data.model.buildWhatsAppReportMessage
import com.example.ui.theme.WhatsAppBlueTick
import com.example.ui.theme.WhatsAppBubbleLight
import com.example.ui.theme.WhatsAppChatBg
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.ui.theme.WhatsAppMutedGray
import com.example.ui.theme.WhatsAppTealDark
import com.example.util.WhatsAppDispatcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportCardDetailDispatchScreen(
    report: ReportCard,
    defaultIntroNote: String,
    onBack: () -> Unit,
    onMarkDispatched: (ReportCard) -> Unit,
    onDeleteReport: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var previewTab by remember { mutableIntStateOf(0) } // 0 = WhatsApp Chat View, 1 = Official Certificate
    var customParentNote by remember(report.id, defaultIntroNote) {
        mutableStateOf(defaultIntroNote)
    }

    val isSent = report.whatsappStatus == "sent" || report.dispatchCount > 0L
    val isDark = isSystemInDarkTheme()
    val chatBackground = if (isDark) Color(0xFF0B141A) else WhatsAppChatBg
    val cardSurface = if (isDark) Color(0xFF1F2C34) else Color.White
    val primaryText = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val mutedText = if (isDark) Color(0xFF8696A0) else WhatsAppMutedGray

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
            // WhatsApp-style Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Report Cards"
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
                                text = report.studentName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.parentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${report.studentName} • ${report.parentWhatsApp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                WhatsAppDispatcher.copyReportMessageToClipboard(
                                    context = context,
                                    report = report,
                                    customIntroNote = customParentNote
                                )
                                onNotify("Copied formatted report card to clipboard")
                            },
                            modifier = Modifier.testTag("copy_whatsapp_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy WhatsApp Text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                onDeleteReport(report.id)
                                onBack()
                            },
                            modifier = Modifier.testTag("delete_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Report Card",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Neat Pill Switcher inside header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = previewTab == 0,
                            onClick = { previewTab = 0 },
                            label = { Text("WhatsApp Chat") },
                            shape = CircleShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("tab_whatsapp_preview")
                        )
                        FilterChip(
                            selected = previewTab == 1,
                            onClick = { previewTab = 1 },
                            label = { Text("Certificate Card") },
                            shape = CircleShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("tab_certificate_preview")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        WhatsAppStatusBadge(status = report.whatsappStatus, count = report.dispatchCount)
                    }
                }
            }

            // Chat / Certificate Content Canvas
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Centered Date / Term Pill like WhatsApp
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
                            text = "${report.schoolName} • ${report.academicTerm}",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedText,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                if (previewTab == 0) {
                    // Compact Certificate Attachment Bubble (Share PNG)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 4.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        ),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WhatsAppTealDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = report.overallGrade,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${report.studentName} • ${report.overallScore}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = primaryText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${report.gradeClass} • Rank: ${report.classPosition}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedText
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    WhatsAppDispatcher.shareReportCardCertificateImage(
                                        context = context,
                                        report = report,
                                        customIntroNote = customParentNote
                                    )
                                    onMarkDispatched(report)
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, WhatsAppTealDark),
                                modifier = Modifier.testTag("share_certificate_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Share Certificate Image",
                                    tint = WhatsAppTealDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Send PNG",
                                    color = WhatsAppTealDark,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Outgoing WhatsApp Bubble Preview
                    WhatsAppMessageBubbleOnly(
                        report = report,
                        customIntroNote = customParentNote,
                        isSent = isSent
                    )
                } else {
                    OfficialReportCardCertificateCard(report = report)
                    OutlinedButton(
                        onClick = {
                            WhatsAppDispatcher.shareReportCardCertificateImage(
                                context = context,
                                report = report,
                                customIntroNote = customParentNote
                            )
                            onMarkDispatched(report)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_certificate_image_button"),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, WhatsAppTealDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Share Certificate Image",
                            tint = WhatsAppTealDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Certificate Image on WhatsApp",
                            color = WhatsAppTealDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Iconic WhatsApp Bottom Composer Bar
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
                                text = "Add optional note to ${report.parentName}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = customParentNote,
                            onValueChange = { customParentNote = it },
                            textStyle = TextStyle(
                                color = primaryText,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_parent_note_input")
                        )
                    }
                }

                // Circular WhatsApp Green Send Button
                Surface(
                    onClick = {
                        WhatsAppDispatcher.launchWhatsAppDirectText(
                            context = context,
                            report = report,
                            customIntroNote = customParentNote
                        )
                        onMarkDispatched(report)
                    },
                    shape = CircleShape,
                    color = WhatsAppGreenAccent,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_whatsapp_direct_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Report Card via WhatsApp",
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
private fun WhatsAppMessageBubbleOnly(
    report: ReportCard,
    customIntroNote: String,
    isSent: Boolean
) {
    val isDark = isSystemInDarkTheme()
    val bubbleColor = if (isDark) Color(0xFF005C4B) else WhatsAppBubbleLight
    val bubbleTextColor = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val bubbleMetaColor = if (isDark) Color(0xFFA8C7BE) else WhatsAppMutedGray
    val formattedMessage = remember(report, customIntroNote) {
        buildWhatsAppReportMessage(report, customIntroNote)
    }

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
                text = formattedMessage,
                style = MaterialTheme.typography.bodySmall,
                color = bubbleTextColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (report.lastSentAt.isNotBlank()) report.lastSentAt else "Ready to send",
                    style = MaterialTheme.typography.labelSmall,
                    color = bubbleMetaColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "WhatsApp Status Ticks",
                    tint = if (isSent) WhatsAppBlueTick else bubbleMetaColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun OfficialReportCardCertificateCard(
    report: ReportCard,
    modifier: Modifier = Modifier
) {
    val subjects = remember(report.subjectsSummary) { report.parsedSubjects }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = Color(0xFF008069).copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF008069))
                    .padding(vertical = 16.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "School Crest",
                        tint = Color(0xFFD8FDD2),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = report.schoolName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "OFFICIAL TERMINAL REPORT CARD",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFD8FDD2),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = report.academicTerm,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F2F5)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetadataColumn(
                            label = "STUDENT NAME",
                            value = report.studentName,
                            modifier = Modifier.weight(1.2f)
                        )
                        MetadataColumn(
                            label = "STUDENT ID",
                            value = report.studentCode,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetadataColumn(
                            label = "CLASS / FORM",
                            value = report.gradeClass,
                            modifier = Modifier.weight(1.2f)
                        )
                        MetadataColumn(
                            label = "ATTENDANCE",
                            value = report.attendanceSummary,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF008069))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SUBJECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.6f)
                )
                Text(
                    text = "SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.6f)
                )
                Text(
                    text = "GRADE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.6f)
                )
                Text(
                    text = "REMARK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.1f)
                )
            }

            subjects.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (idx % 2 == 0) Color(0xFFF7F8FA) else Color.Transparent
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111B21),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1.6f)
                    )
                    Text(
                        text = "${row.score}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF008069),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.6f)
                    )
                    Text(
                        text = row.grade,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111B21),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.6f)
                    )
                    Text(
                        text = row.remark,
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppMutedGray,
                        modifier = Modifier.weight(1.1f)
                    )
                }
                HorizontalDivider(color = Color(0xFFE9EDEF), thickness = 0.5.dp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFD8FDD2)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "OVERALL AVERAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF063B27)
                        )
                        Text(
                            text = "${report.overallScore}% • Grade ${report.overallGrade}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF008069),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CLASS POSITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF063B27)
                        )
                        Text(
                            text = report.classPosition,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF063B27),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "EDUCATOR'S REMARKS",
                style = MaterialTheme.typography.labelSmall,
                color = WhatsAppMutedGray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\"${report.teacherRemarks}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF111B21),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            HorizontalDivider(color = Color(0xFFE9EDEF))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parent: ${report.parentName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WhatsAppMutedGray
                )
                Text(
                    text = "Signed: ${report.principalSignature}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF008069),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetadataColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WhatsAppMutedGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF111B21),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun WhatsAppStatusBadge(
    status: String,
    count: Long,
    modifier: Modifier = Modifier
) {
    val isSent = status == "sent" || count > 0L
    val bgColor = if (isSent) Color(0xFFD8FDD2) else Color(0xFFF0F2F5)
    val contentColor = if (isSent) Color(0xFF063B27) else WhatsAppMutedGray
    val tickColor = if (isSent) WhatsAppBlueTick else WhatsAppMutedGray
    val label = if (isSent) {
        if (count > 1L) "Sent (${count}x)" else "Sent"
    } else {
        "Pending"
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = label,
                tint = tickColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
