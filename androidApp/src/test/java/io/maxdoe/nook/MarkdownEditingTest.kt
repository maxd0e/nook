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

    @Test
    fun numberedList_continuesThePreviousItemNumber() {
        val second = insertNumberedList(TextFieldValue("1. First\nSecond", TextRange(15)))
        val third = insertNumberedList(TextFieldValue("1. First\n2. Second\nThird", TextRange(24)))

        assertEquals("1. First\n2. Second", second.text)
        assertEquals("1. First\n2. Second\n3. Third", third.text)
    }

    @Test
    fun enterAfterNumberedItem_insertsTheNextNumber() {
        val first = TextFieldValue("1. First", TextRange(8))
        val second = continueListOnEnter(first, TextFieldValue("1. First\n", TextRange(9)))
        val third = continueListOnEnter(second, TextFieldValue("1. First\n2. \n", TextRange(13)))

        assertEquals("1. First\n2. ", second.text)
        assertEquals(TextRange(12), second.selection)
        assertEquals("1. First\n2. \n3. ", third.text)
        assertEquals(TextRange(16), third.selection)
    }

    @Test
    fun enterAfterPlainTextAndMultiLinePaste_areNotReformatted() {
        val plain = TextFieldValue("First", TextRange(5))
        val plainNewLine = continueListOnEnter(plain, TextFieldValue("First\n", TextRange(6)))
        val numbered = TextFieldValue("1. First", TextRange(8))
        val paste = continueListOnEnter(numbered, TextFieldValue("1. First\nSecond\nThird", TextRange(21)))

        assertEquals("First\n", plainNewLine.text)
        assertEquals("1. First\nSecond\nThird", paste.text)
    }

    @Test
    fun enterAfterBulletItem_insertsAnotherBullet() {
        val first = TextFieldValue("- First", TextRange(7))
        val second = continueListOnEnter(first, TextFieldValue("- First\n", TextRange(8)))
        val emptyBullet = continueListOnEnter(second, TextFieldValue("- First\n- \n", TextRange(11)))

        assertEquals("- First\n- ", second.text)
        assertEquals(TextRange(10), second.selection)
        assertEquals("- First\n- \n- ", emptyBullet.text)
        assertEquals(TextRange(13), emptyBullet.selection)
    }
}
