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

internal fun insertNumberedList(value: TextFieldValue): TextFieldValue {
    val lineStart = value.text.lastIndexOf('\n', value.selection.start - 1).let { if (it < 0) 0 else it + 1 }
    val previousLine =
        value.text.substring(0, lineStart).removeSuffix("\n").substringAfterLast('\n').trim()
    val nextNumber =
        NUMBERED_LIST_PREFIX.matchEntire(previousLine)?.groupValues?.get(1)?.toIntOrNull()?.plus(1) ?: 1
    return prefixCurrentLine(value, "$nextNumber. ")
}

internal fun continueListOnEnter(
    previous: TextFieldValue,
    proposed: TextFieldValue,
): TextFieldValue {
    val insertion = previous.selection.start
    if (
        !previous.selection.collapsed ||
        !proposed.selection.collapsed ||
        proposed.selection.start != insertion + 1 ||
        proposed.text != previous.text.substring(0, insertion) + "\n" + previous.text.substring(insertion)
    ) {
        return proposed
    }

    val currentLine = previous.text.substring(0, insertion).substringAfterLast('\n')
    val prefix =
        NUMBERED_LIST_PREFIX.matchEntire(currentLine)?.groupValues?.get(1)?.toIntOrNull()?.let { "${it + 1}. " }
            ?: if (currentLine.startsWith("- ")) "- " else return proposed
    return proposed.replace(
        insertion + 1,
        insertion + 1,
        prefix,
        TextRange(insertion + 1 + prefix.length),
    )
}

private val NUMBERED_LIST_PREFIX = Regex("(\\d+)\\. .*")

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
