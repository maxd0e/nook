package io.maxdoe.nook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditingTest {
    @Test
    fun bold_wrapsSelectedText() {
        val result = toggleInlineMarkdown(TextFieldValue("garden", TextRange(0, 6)), "**")

        assertEquals("**garden**", result.text)
        assertEquals(TextRange(0, 10), result.selection)
    }

    @Test
    fun italic_withoutSelection_placesCaretInsideMarkers() {
        val result = toggleInlineMarkdown(TextFieldValue("garden", TextRange(3)), "_")

        assertEquals("gar__den", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun headingAndListActions_prefixTheCurrentLine() {
        val heading = insertHeading(TextFieldValue("first\nsecond", TextRange(8)))
        val bullet = insertBullet(TextFieldValue("one\ntwo", TextRange(5)))
        val numbered = insertNumberedList(TextFieldValue("one\ntwo", TextRange(5)))

        assertEquals("first\n## second", heading.text)
        assertEquals("one\n- two", bullet.text)
        assertEquals("one\n1. two", numbered.text)
    }
}
