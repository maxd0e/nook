@file:Suppress("MaxLineLength")

package io.maxdoe.nook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun toggleInlineMarkdown(
    value: TextFieldValue,
    marker: String,
): TextFieldValue {
    val selection = value.selection
    return if (selection.collapsed) {
        value.replace(selection.start, selection.end, "$marker$marker", TextRange(selection.start + marker.length))
    } else {
        val selected = value.text.substring(selection.min, selection.max)
        value.replace(
            selection.min,
            selection.max,
            "$marker$selected$marker",
            TextRange(
                selection.min,
                selection.max + marker.length * 2,
            ),
        )
    }
}

internal fun insertHeading(value: TextFieldValue): TextFieldValue = prefixCurrentLine(value, "## ")

internal fun insertBullet(value: TextFieldValue): TextFieldValue = prefixCurrentLine(value, "- ")

private fun prefixCurrentLine(
    value: TextFieldValue,
    prefix: String,
): TextFieldValue {
    val lineStart = value.text.lastIndexOf('\n', value.selection.start - 1).let { if (it < 0) 0 else it + 1 }
    return value.replace(lineStart, lineStart, prefix, TextRange(value.selection.start + prefix.length))
}

private fun TextFieldValue.replace(
    start: Int,
    end: Int,
    replacement: String,
    selection: TextRange,
): TextFieldValue =
    copy(
        text = text.substring(0, start) + replacement + text.substring(end),
        selection = selection,
    )
