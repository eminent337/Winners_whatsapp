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

        // Detect and drop a header row.
        val start = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            !line.any { it.isDigit() } &&
                (lower.contains("surname") || lower.contains("name") || lower.contains("contact") ||
                    lower.contains("phone") || lower.contains("gender"))
        }.let { if (it >= 0) it + 1 else 0 }

        for (line in lines.drop(start)) {
            val cells = line.split('\t', ',', ';', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (cells.isEmpty()) continue

            // Find a phone number: prefer an explicit cell, else scan the line.
            var phone: String? = null
            var nameParts = mutableListOf<String>()

            for (cell in cells) {
                val normalized = normalizeGhanaNumber(cell)
                if (normalized != null && phone == null) {
                    phone = normalized
                } else if (cell.any { it.isLetter() }) {
                    // Drop generic header/label cells.
                    val lower = cell.lowercase()
                    val isLabel = lower in setOf(
                        "s/n", "sn", "no.", "gender", "male", "female",
                        "gh. lang. type", "date of birth", "parents contact",
                        "parent contact", "surname", "first name", "other name"
                    ) || lower.matches(Regex("""\d{1,2}\s*[-/]\s*\d{1,2}\s*[-/]\s*\d{2,4}"""))
                    if (!isLabel) nameParts.add(cell)
                }
            }

            if (phone == null) {
                val match = phoneRegex.find(line)?.value
                if (match != null) {
                    normalizeGhanaNumber(match)?.let { phone = it }
                    if (phone != null) {
                        nameParts = line.replace(match, "")
                            .split('\t', ',', ';', '|')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && it.any { c -> c.isLetter() } }
                            .toMutableList()
                    }
                }
            }

            val name = nameParts.joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .removePrefix("0").trim() // stray leading serial number

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
                parentWhatsApp = phone,
                studentCode = "",
                gradeClass = gradeClass
            )
        }

        return Result(students, skipped)
    }
}
