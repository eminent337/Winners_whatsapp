package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ReportCard
import com.example.data.model.Student
import com.example.data.model.SubjectGradeRow
import com.example.data.model.calculateLetterGrade
import com.example.data.model.defaultRemarkForScore
import com.example.ui.theme.WhatsAppGreenAccent
import com.example.util.WhatsAppDispatcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateStudentDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String,
        studentCode: String,
        gradeClass: String,
        parentName: String,
        parentWhatsApp: String,
        attendanceDays: Long,
        totalTermDays: Long
    ) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var studentCode by remember { mutableStateOf("GP-2026-${(105..999).random()}") }
    var gradeClass by remember { mutableStateOf("Grade 8 • Emerald") }
    var parentName by remember { mutableStateOf("") }
    var parentWhatsApp by remember { mutableStateOf("+233") }
    var attendanceDaysStr by remember { mutableStateOf("63") }
    var totalTermDaysStr by remember { mutableStateOf("65") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val presetClasses = listOf(
        "Grade 7 • Ruby",
        "Grade 8 • Emerald",
        "Grade 9 • Gold",
        "Form 1 Science",
        "Form 2 Arts",
        "Form 3 Business"
    )
    val dialCodes = listOf("+233", "+234", "+254", "+27", "+44", "+1")

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
                    Text(
                        text = "Add Student & Parent WhatsApp",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Student Full Name *") },
                    placeholder = { Text("e.g., Kofi Mensah") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_student_name")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = studentCode,
                        onValueChange = { studentCode = it },
                        label = { Text("Student ID Code") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_student_code")
                    )
                    OutlinedTextField(
                        value = gradeClass,
                        onValueChange = { gradeClass = it },
                        label = { Text("Class / Form *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("input_student_class")
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetClasses.forEach { preset ->
                        FilterChip(
                            selected = gradeClass == preset,
                            onClick = { gradeClass = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Parent / Guardian Full Name *") },
                    placeholder = { Text("e.g., Mrs. Abena Mensah") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_parent_name")
                )

                OutlinedTextField(
                    value = parentWhatsApp,
                    onValueChange = { parentWhatsApp = it },
                    label = { Text("Parent WhatsApp Number (with country code) *") },
                    placeholder = { Text("e.g., +233241234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_parent_whatsapp")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Country Code:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    dialCodes.forEach { code ->
                        AssistChip(
                            onClick = {
                                val stripped = parentWhatsApp.replace(Regex("^\\+\\d{1,3}"), "")
                                parentWhatsApp = "$code$stripped"
                            },
                            label = { Text(code, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = attendanceDaysStr,
                        onValueChange = { attendanceDaysStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Days Attended") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = totalTermDaysStr,
                        onValueChange = { totalTermDaysStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Total Term Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

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
                            val cleanDigits = parentWhatsApp.filter { it.isDigit() }
                            when {
                                fullName.isBlank() -> validationError = "Please enter the student's full name."
                                parentName.isBlank() -> validationError = "Please enter the parent/guardian's name."
                                cleanDigits.length < 7 -> validationError = "Please enter a valid WhatsApp phone number."
                                else -> {
                                    onConfirm(
                                        fullName.trim(),
                                        studentCode.ifBlank { "GP-2026-100" },
                                        gradeClass.ifBlank { "Grade 8" },
                                        parentName.trim(),
                                        parentWhatsApp.trim(),
                                        attendanceDaysStr.toLongOrNull() ?: 62L,
                                        (totalTermDaysStr.toLongOrNull() ?: 65L).coerceAtLeast(1L)
                                    )
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_save_student_button")
                    ) {
                        Text("Save Student", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateReportCardDialog(
    students: List<Student>,
    preselectedStudent: Student? = null,
    onDismiss: () -> Unit,
    onOpenAddStudent: () -> Unit,
    onConfirm: (
        student: Student,
        subjectRows: List<SubjectGradeRow>,
        classPosition: String,
        teacherRemarks: String
    ) -> Unit
) {
    var selectedStudent by remember(students, preselectedStudent) {
        mutableStateOf(preselectedStudent ?: students.firstOrNull())
    }
    var studentDropdownExpanded by remember { mutableStateOf(false) }

    val subjectRows = remember {
        mutableStateListOf(
            SubjectGradeRow("Mathematics", 90, "A+", "Distinction"),
            SubjectGradeRow("Integrated Science", 86, "A", "Excellent"),
            SubjectGradeRow("English Language", 88, "A", "Excellent"),
            SubjectGradeRow("Computing & ICT", 92, "A+", "Distinction"),
            SubjectGradeRow("Social Studies", 84, "A", "Excellent")
        )
    }

    var newSubjectName by remember { mutableStateOf("") }
    var newSubjectScore by remember { mutableStateOf("85") }
    var classPosition by remember { mutableStateOf("1st of ${students.size.coerceAtLeast(25)}") }
    var teacherRemarks by remember {
        mutableStateOf("Demonstrates exemplary dedication, discipline, and strong analytical mastery across all subjects.")
    }

    val presetRemarks = listOf(
        "Demonstrates exemplary dedication, discipline, and strong analytical mastery across all subjects.",
        "Consistent academic progress this term. Active participant in class discussions and projects.",
        "Well-behaved and hardworking student. Extra focus on quantitative exercises will boost next term's rank."
    )

    val liveAverage = remember(subjectRows.toList()) {
        if (subjectRows.isNotEmpty()) {
            subjectRows.sumOf { it.score } / subjectRows.size
        } else 0
    }
    val liveGrade = calculateLetterGrade(liveAverage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp),
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
                    Column {
                        Text(
                            text = "Create Official Report Card",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live Average: $liveAverage% (Grade $liveGrade)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (students.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No students in roster yet.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onOpenAddStudent) {
                                Text("Add First Student")
                            }
                        }
                    }
                } else {
                    // Student Picker
                    ExposedDropdownMenuBox(
                        expanded = studentDropdownExpanded,
                        onExpandedChange = { studentDropdownExpanded = !studentDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.let { "${it.fullName} (${it.gradeClass}) • ${it.parentWhatsApp}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Student") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("student_selector_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = studentDropdownExpanded,
                            onDismissRequest = { studentDropdownExpanded = false }
                        ) {
                            students.forEach { stu ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stu.fullName, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${stu.gradeClass} • Parent: ${stu.parentName} (${stu.parentWhatsApp})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedStudent = stu
                                        studentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Subject Scores (0 - 100%)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    subjectRows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = row.subjectName,
                                onValueChange = { updatedName ->
                                    subjectRows[index] = row.copy(subjectName = updatedName)
                                },
                                label = { Text("Subject") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = row.score.toString(),
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(3)
                                    val scoreVal = (digits.toIntOrNull() ?: 0).coerceIn(0, 100)
                                    subjectRows[index] = row.copy(
                                        score = scoreVal,
                                        grade = calculateLetterGrade(scoreVal),
                                        remark = defaultRemarkForScore(scoreVal)
                                    )
                                },
                                label = { Text("Score") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.75f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = row.grade,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                )
                            }
                            if (subjectRows.size > 1) {
                                IconButton(
                                    onClick = { subjectRows.removeAt(index) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Subject",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Add another subject row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSubjectName,
                            onValueChange = { newSubjectName = it },
                            label = { Text("Add Subject (e.g., French)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = newSubjectScore,
                            onValueChange = { newSubjectScore = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text("Score") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        IconButton(
                            onClick = {
                                if (newSubjectName.isNotBlank()) {
                                    val sc = (newSubjectScore.toIntOrNull() ?: 85).coerceIn(0, 100)
                                    subjectRows.add(
                                        SubjectGradeRow(
                                            subjectName = newSubjectName.trim(),
                                            score = sc,
                                            grade = calculateLetterGrade(sc),
                                            remark = defaultRemarkForScore(sc)
                                        )
                                    )
                                    newSubjectName = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Subject Row")
                        }
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = classPosition,
                        onValueChange = { classPosition = it },
                        label = { Text("Class Position / Standing") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_class_position")
                    )

                    OutlinedTextField(
                        value = teacherRemarks,
                        onValueChange = { teacherRemarks = it },
                        label = { Text("Educator's Remarks") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_teacher_remarks")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetRemarks.forEachIndexed { idx, remark ->
                            AssistChip(
                                onClick = { teacherRemarks = remark },
                                label = { Text("Template ${idx + 1}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
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
                                val stu = selectedStudent ?: return@Button
                                onConfirm(
                                    stu,
                                    subjectRows.toList(),
                                    classPosition,
                                    teacherRemarks
                                )
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreenAccent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("confirm_save_report_button")
                        ) {
                            Text("Save Report Card", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchWhatsAppQueueDialog(
    pendingReports: List<ReportCard>,
    customIntroNote: String,
    onDismiss: () -> Unit,
    onMarkSent: (ReportCard) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (pendingReports.size - 1).coerceAtLeast(0))
    val currentReport = pendingReports.getOrNull(safeIndex)

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
                        text = "Batch WhatsApp Dispatch Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Queue")
                    }
                }

                if (currentReport == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "All Dispatched",
                            tint = WhatsAppGreenAccent,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Report Cards Dispatched!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Every parent in your queue has received their child's report card on WhatsApp.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                } else {
                    val progress = (safeIndex + 1).toFloat() / pendingReports.size.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Recipient ${safeIndex + 1} of ${pendingReports.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentReport.gradeClass,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentReport.studentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Overall: ${currentReport.overallScore}% (${currentReport.overallGrade}) • Position: ${currentReport.classPosition}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Parent: ${currentReport.parentName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "WhatsApp: ${currentReport.parentWhatsApp}",
                                style = MaterialTheme.typography.titleSmall,
                                color = WhatsAppGreenAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            WhatsAppDispatcher.launchWhatsAppDirectText(
                                context = context,
                                report = currentReport,
                                customIntroNote = customIntroNote
                            )
                            onMarkSent(currentReport)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("batch_send_next_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreenAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send on WhatsApp"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send to ${currentReport.parentName} on WhatsApp",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                WhatsAppDispatcher.shareReportCardCertificateImage(
                                    context = context,
                                    report = currentReport,
                                    customIntroNote = customIntroNote
                                )
                                onMarkSent(currentReport)
                            },
                            shape = CircleShape
                        ) {
                            Text("Share Certificate Image")
                        }

                        if (safeIndex < pendingReports.size - 1) {
                            TextButton(
                                onClick = { currentIndex = safeIndex + 1 }
                            ) {
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
