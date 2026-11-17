package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RosterParser], focused on parent/guardian-name extraction.
 *
 * These exercise the import path used by "School → Import Student Roster":
 * a header row that names a parent/guardian column should populate
 * [RosterParser.ParsedStudent.parentName] and keep it out of the student name.
 */
class RosterParserTest {

    @Test
    fun `header with parent name column populates parentName`() {
        val text = """
            |SURNAME  FIRST NAME  PARENT NAME  PARENT CONTACT
            |ADDEY  JOSEPHINE  MRS. ADDEY  0538604588
        """.trimMargin()

        val result = RosterParser.parse(text, "BECE 2027")

        assertTrue("expected no skipped rows: ${result.skipped}", result.skipped.isEmpty())
        assertEquals(1, result.students.size)
        val student = result.students.single()
        assertEquals("ADDEY JOSEPHINE", student.fullName)
        assertEquals("MRS. ADDEY", student.parentName)
        assertEquals("+233538604588", student.parentWhatsApp)
    }

    @Test
    fun `header without parent column falls back to default guardian`() {
        val text = """
            |SURNAME  FIRST NAME  PARENT CONTACT
            |ADDEY  JOSEPHINE  0538604588
        """.trimMargin()

        val result = RosterParser.parse(text, "BECE 2027")

        assertTrue(result.skipped.isEmpty())
        val student = result.students.single()
        assertEquals("ADDEY JOSEPHINE", student.fullName)
        assertEquals("Parent/Guardian", student.parentName)
        assertEquals("+233538604588", student.parentWhatsApp)
    }

    @Test
    fun `headerless rows keep name and number and default guardian`() {
        val text = "ABRAHAM EZAN NYAMEAYE, 0248907473"

        val result = RosterParser.parse(text, "BECE 2027")

        assertTrue(result.skipped.isEmpty())
        val student = result.students.single()
        assertEquals("ABRAHAM EZAN NYAMEAYE", student.fullName)
        assertEquals("Parent/Guardian", student.parentName)
        assertEquals("+233248907473", student.parentWhatsApp)
    }

    @Test
    fun `guardian column header is recognised`() {
        val text = """
            |S/N  SURNAME  OTHER NAME  GUARDIAN  CONTACT
            |1  MENSSAH  KWAME  MR. MENSSAH  0244112233
        """.trimMargin()

        val result = RosterParser.parse(text, "BECE 2027")

        assertTrue(result.skipped.isEmpty())
        val student = result.students.single()
        assertEquals("MENSSAH KWAME", student.fullName)
        assertEquals("MR. MENSSAH", student.parentName)
        assertEquals("+233244112233", student.parentWhatsApp)
    }

    @Test
    fun `rows without a valid phone are skipped`() {
        val text = """
            |SURNAME  FIRST NAME  PARENT CONTACT
            |ADDEY  JOSEPHINE  12
        """.trimMargin()

        val result = RosterParser.parse(text, "BECE 2027")

        assertTrue(result.students.isEmpty())
        assertEquals(1, result.skipped.size)
    }
}