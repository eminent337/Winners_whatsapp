package com.example.data

/**
 * Parses pasted roster text into importable student rows.
 *
 * Accepts the formats a school actually has on hand:
 *
 *  1. Tab / comma separated with a header row (spreadsheet copy-paste):
 *     S/N  SURNAME  FIRST NAME  OTHER NAME  GENDER  DATE OF BIRTH  GH. LANG.  PARENTS CONTACT
 *
 *  2. One student per line, contact last:
 *     ABRAHAM EZAN NYAMEAYE, 0248907473
 *
 *  3. Loose text with a name then a phone number somewhere on the line.
 *
 * Ghanaian numbers are normalised to E.164: 0XXXXXXXXX -> +233XXXXXXXXX
 */
object RosterParser {

    /** Ghana mobile prefixes, with the national trunk 0. */
    private val GHANA_MOBILE = setOf(
        "020", "023", "024", "025", "050",
        "053", "054", "055", "056", "057", "059"
    )

    data class ParsedStudent(
        val fullName: String,
        val parentName: String,
        val parentWhatsApp: String,
        val studentCode: String,
        val gradeClass: String
    )

    data class Result(
        val students: List<ParsedStudent>,
        val skipped: List<String>
    )

    /**
     * Normalises a Ghanaian phone number to E.164 digits with a leading +.
     * Returns null when the value cannot be a valid mobile number.
     */
    fun normalizeGhanaNumber(raw: String?): String? {
        if (raw == null) return null
        val digits = raw.filter { it.isDigit() }
        return when {
            // 0XXXXXXXXX -> +233XXXXXXXXX
            digits.length == 10 && digits.startsWith("0") && digits.take(3) in GHANA_MOBILE ->
                "+233" + digits.drop(1)
            // 233XXXXXXXXX -> +233XXXXXXXXX
            digits.length == 12 && digits.startsWith("233") && ("0" + digits.drop(3).take(2)) in GHANA_MOBILE ->
                "+$digits"
            // XXXXXXXXX (lost leading zero) -> +233XXXXXXXXX
            digits.length == 9 && ("0" + digits.take(3)) in GHANA_MOBILE ->
                "+233$digits"
            else -> null
        }
    }

    private val phoneRegex = Regex("""(?:\+?233|0)\s?[\d\s\-]{7,12}""")

    fun parse(rawText: String, gradeClass: String): Result {
        val lines = rawText.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val students = mutableListOf<ParsedStudent>()
        val skipped = mutableListOf<String>()

        // Detect and drop the header row, and use it to locate special columns.
        val headerIndex = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            !line.any { it.isDigit() } &&
                (lower.contains("surname") || lower.contains("name") || lower.contains("contact") ||
                    lower.contains("phone") || lower.contains("gender"))
        }
        val start = if (headerIndex >= 0) headerIndex + 1 else 0

        val headerCells = if (headerIndex >= 0) {
            lines[headerIndex].split(Regex("""\t|,|;|\||\s{2,}""")).map { it.trim().lowercase() }
        } else {
            emptyList()
        }

        // Columns to pull out of the student name: the parent/guardian name and
        // the phone. Detected from the header when present, else inferred per row.
        val parentNameIdx = headerCells.indexOfFirst { isParentNameHeader(it) }.takeIf { it >= 0 }
        val phoneIdx = headerCells.indexOfFirst { isContactHeader(it) }.takeIf { it >= 0 }
        val serialIdx = headerCells.indexOfFirst { c -> c in setOf("s/n", "sn", "no.", "no") }
            .takeIf { it >= 0 }

        for (line in lines.drop(start)) {
            // Split on tabs/commas/semicolons/pipes, or runs of 2+ spaces (space-aligned
            // columns copied from a document/Excel paste).
            val cells = line.split(Regex("""\t|,|;|\||\s{2,}"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (cells.all { it.isEmpty() }) continue

            // Find the phone: prefer the header's contact column, else any cell that
            // normalises, else a phone pattern anywhere on the line.
            var phone: String? = null
            if (phoneIdx != null) {
                normalizeGhanaNumber(cells.getOrNull(phoneIdx))?.let { phone = it }
            }
            if (phone == null) {
                for (cell in cells) {
                    val normalized = normalizeGhanaNumber(cell)
                    if (normalized != null) {
                        phone = normalized
                        break
                    }
                }
            }
            // Fallback: scan the whole line for a phone pattern (space-aligned pastes
            // where the number was glued to other text).
            var phoneMatch: String? = null
            if (phone == null) {
                phoneRegex.find(line)?.let { match ->
                    normalizeGhanaNumber(match.value)?.let {
                        phone = it
                        phoneMatch = match.value
                    }
                }
            }

            // The parent/guardian name: the header's parent column, else blank.
            val parentName = parentNameIdx
                ?.let { idx -> cells.getOrNull(idx) }
                ?.takeIf { it.any { c -> c.isLetter() } }
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?.removePrefix("0")
                ?.trim()
                .orEmpty()

            // Student name = every cell that is not the parent, not the phone, not
            // a serial number / gender / date-of-birth label.
            val nameParts = cells.withIndex().mapNotNull { (idx, cell) ->
                if (idx == parentNameIdx) return@mapNotNull null
                if (idx == phoneIdx) return@mapNotNull null
                if (idx == serialIdx) return@mapNotNull null
                if (cell.isBlank()) return@mapNotNull null
                // Drop a cell that *is* the phone (inferred case).
                if (normalizeGhanaNumber(cell) != null) return@mapNotNull null
                if (isStaticLabel(cell)) return@mapNotNull null
                cell
            }

            var name = nameParts.joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .removePrefix("0")   // stray serial number
                .trim()

            // If the phone was glued onto the name (space-aligned fallback), strip it.
            if (phoneMatch != null) {
                name = name.replace(phoneMatch!!, "").replace(Regex("""\s+"""), " ").trim()
            }

            // If the parent name was inferred (no header column) but still ended up
            // glued onto the student name, strip it out.
            if (parentName.isNotBlank() && parentNameIdx == null &&
                name.contains(parentName, ignoreCase = true)
            ) {
                name = name.replace(parentName, "").replace(Regex("""\s+"""), " ").trim()
            }

            if (name.isBlank() || name.length < 3) {
                skipped += line.take(60)
                continue
            }
            if (phone == null) {
                skipped += "$name (no valid phone)"
                continue
            }

            students += ParsedStudent(
                fullName = name,
                parentName = parentName.ifBlank { "Parent/Guardian" },
                parentWhatsApp = phone,
                studentCode = "",
                gradeClass = gradeClass
            )
        }

        return Result(students, skipped)
    }

    /** True when a header cell names a parent/guardian column. */
    private fun isParentNameHeader(cell: String): Boolean {
        val c = cell.lowercase()
        return c.contains("parent") || c.contains("guardian")
    }

    /** True when a header cell names a phone/contact column. */
    private fun isContactHeader(cell: String): Boolean {
        val c = cell.lowercase()
        return c.contains("contact") || c.contains("phone") || c.contains("mobile") ||
            c.contains("whatsapp") || c.contains("tel")
    }

    /** Cells that carry no student name and must never join the name. */
    private fun isStaticLabel(cell: String): Boolean {
        val lower = cell.lowercase()
        return lower in setOf(
            "s/n", "sn", "no.", "no", "gender", "male", "female",
            "gh. lang. type", "date of birth", "parents contact", "parent contact"
        ) || lower.matches(Regex("""\d{1,2}\s*[-/]\s*\d{1,2}\s*[-/]\s*\d{2,4}"""))
    }
}
