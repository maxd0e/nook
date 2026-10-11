@file:Suppress("MaxLineLength")

package io.maxdoe.nook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.maxdoe.nook.notes.ChecklistItem
import io.maxdoe.nook.notes.ChecklistPayload
import io.maxdoe.nook.notes.NookListItem
import io.maxdoe.nook.notes.NotePayload
import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.delay
import java.util.UUID

private sealed interface NotesDestination {
    data object List : NotesDestination

    data class Editor(
        val noteId: String?,
    ) : NotesDestination

    data class ChecklistEditor(
        val checklistId: String?,
    ) : NotesDestination
}

@Composable
fun NookNotes(
    repository: NoteRepository,
    onLogout: () -> Unit,
) {
    var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
    when (val current = destination) {
        NotesDestination.List ->
            NotesList(
                repository = repository,
                onNewNote = { destination = NotesDestination.Editor(null) },
                onNewChecklist = { destination = NotesDestination.ChecklistEditor(null) },
                onOpenNote = { destination = NotesDestination.Editor(it) },
                onOpenChecklist = { destination = NotesDestination.ChecklistEditor(it) },
                onLogout = onLogout,
            )
        is NotesDestination.Editor ->
            NoteEditor(repository, current.noteId, onBack = {
                destination =
                    NotesDestination.List
            })
        is NotesDestination.ChecklistEditor ->
            ChecklistEditor(repository, current.checklistId, onBack = {
                destination = NotesDestination.List
            })
    }
}

@Composable
@Suppress("LongMethod")
private fun NotesList(
    repository: NoteRepository,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenChecklist: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val notes by repository.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    val matching = remember(notes, query) { repository.search(query) }
    val pinned = matching.filter { it.payload.isPinned() }
    val unpinned = matching - pinned.toSet()
    var addMenuExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().testTag("notes_list")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Spacer(Modifier.height(34.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", style = MaterialTheme.typography.displayLarge, fontSize = 68.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Log out",
                        modifier = Modifier.clickable(onClick = onLogout),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("notes_search"),
                    singleLine = true,
                    leadingIcon = { Text("⌕", fontSize = 32.sp) },
                    placeholder = { Text("Search", fontSize = 28.sp) },
                    shape = RoundedCornerShape(24.dp),
                )
            }
            if (pinned.isNotEmpty()) {
                item { SectionTitle("Pinned") }
                items(pinned, key = NookListItem::id) { NookCard(it, onOpenNote, onOpenChecklist) }
            }
            if (unpinned.isNotEmpty()) {
                item { SectionTitle(if (pinned.isEmpty()) "All notes" else "All notes") }
                items(unpinned, key = NookListItem::id) { NookCard(it, onOpenNote, onOpenChecklist) }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
        FloatingActionButton(
            onClick = { addMenuExpanded = true },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(40.dp)
                    .size(96.dp)
                    .testTag("new_note"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Text("+", fontSize = 48.sp) }
        DropdownMenu(
            expanded = addMenuExpanded,
            onDismissRequest = { addMenuExpanded = false },
            modifier = Modifier.testTag("new_item_menu"),
        ) {
            DropdownMenuItem(
                text = { Text("Note") },
                onClick = {
                    addMenuExpanded = false
                    onNewNote()
                },
            )
            DropdownMenuItem(
                text = { Text("Checklist") },
                onClick = {
                    addMenuExpanded = false
                    onNewChecklist()
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(
        label.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
    )
}

@Composable
private fun NookCard(
    item: NookListItem,
    onOpenNote: (String) -> Unit,
    onOpenChecklist: (String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    when (item.payload) {
                        is NotePayload -> onOpenNote(item.id)
                        is ChecklistPayload -> onOpenChecklist(item.id)
                        else -> Unit
                    }
                }.testTag("note_${item.id}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 34.dp, vertical = 26.dp)) {
            when (val payload = item.payload) {
                is NotePayload -> {
                    Text(
                        payload.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 38.sp,
                    )
                    if (payload.body.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            payload.body.replace(Regex("[#*_`>-]"), "").trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 22.sp,
                            maxLines = 2,
                        )
                    }
                }
                is ChecklistPayload -> {
                    Text(
                        payload.title.ifBlank { "Untitled checklist" },
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 38.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${payload.items.count(ChecklistItem::done)} of ${payload.items.size} done",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 22.sp,
                    )
                }
                is io.maxdoe.nook.notes.TombstonePayload -> Unit
            }
        }
    }
}

private fun io.maxdoe.nook.notes.NookPayload.isPinned(): Boolean =
    when (this) {
        is NotePayload -> pinned
        is ChecklistPayload -> pinned
        is io.maxdoe.nook.notes.TombstonePayload -> false
    }

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun NoteEditor(
    repository: NoteRepository,
    existingId: String?,
    onBack: () -> Unit,
) {
    val notes by repository.notes.collectAsState()
    val existing = notes.firstOrNull { it.id == existingId }?.payload as? NotePayload
    var noteId by remember(existingId) { mutableStateOf(existingId) }
    var title by remember(existingId) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existingId) { mutableStateOf(TextFieldValue(existing?.body.orEmpty())) }
    var pinned by remember(existingId) { mutableStateOf(existing?.pinned ?: false) }
    var savedTitle by remember(existingId) { mutableStateOf(existing?.title.orEmpty()) }
    var savedBody by remember(existingId) { mutableStateOf(existing?.body.orEmpty()) }

    fun saveIfNeeded() {
        if (title == savedTitle && body.text == savedBody) return
        if (title.isBlank() && body.text.isBlank()) return
        val id = noteId ?: UUID.randomUUID().toString().also { noteId = it }
        if (existingId == null && savedTitle.isBlank() && savedBody.isBlank()) {
            repository.createNote(id, title, body.text)
            if (pinned) repository.setPinned(id, true)
        } else {
            repository.updateNote(id, title, body.text)
        }
        savedTitle = title
        savedBody = body.text
    }

    LaunchedEffect(title, body.text) {
        delay(500)
        saveIfNeeded()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp).testTag("note_editor")) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                modifier =
                    Modifier.clickable {
                        saveIfNeeded()
                        onBack()
                    },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 52.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (pinned) "Unpin" else "Pin",
                modifier =
                    Modifier.clickable {
                        saveIfNeeded()
                        noteId?.let { id -> repository.setPinned(id, !pinned) }
                        pinned = !pinned
                    },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
            )
            Spacer(Modifier.width(28.dp))
            Text(
                "Delete",
                modifier =
                    Modifier.clickable {
                        noteId?.let(repository::delete)
                        onBack()
                    },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth().testTag("note_title"),
            placeholder = { Text("Untitled", style = MaterialTheme.typography.displayLarge, fontSize = 56.sp) },
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = 56.sp,
                ),
            singleLine = true,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("note_body"),
            placeholder = { Text("Start writing…", fontSize = 24.sp) },
            textStyle =
                androidx.compose.ui.text
                    .TextStyle(fontSize = 25.sp, lineHeight = 38.sp),
        )
        MarkdownToolbar(
            onBold = { body = toggleInlineMarkdown(body, "**") },
            onItalic = { body = toggleInlineMarkdown(body, "_") },
            onHeading = { body = insertHeading(body) },
            onBullet = { body = insertBullet(body) },
        )
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun ChecklistEditor(
    repository: NoteRepository,
    existingId: String?,
    onBack: () -> Unit,
) {
    val notes by repository.notes.collectAsState()
    val existing = notes.firstOrNull { it.id == existingId }?.payload as? ChecklistPayload
    var checklistId by remember(existingId) { mutableStateOf(existingId) }
    var title by remember(existingId) { mutableStateOf(existing?.title.orEmpty()) }
    var items by remember(existingId) { mutableStateOf(existing?.items.orEmpty()) }
    var pinned by remember(existingId) { mutableStateOf(existing?.pinned ?: false) }
    var savedTitle by remember(existingId) { mutableStateOf(existing?.title.orEmpty()) }
    var savedItems by remember(existingId) { mutableStateOf(existing?.items.orEmpty()) }
    var newItemText by remember { mutableStateOf("") }

    fun saveIfNeeded(updatedItems: List<ChecklistItem> = items): Boolean {
        if (title == savedTitle && updatedItems == savedItems) return false
        if (title.isBlank() && updatedItems.isEmpty() && checklistId == null) return false
        val id = checklistId ?: UUID.randomUUID().toString().also { checklistId = it }
        if (existingId == null && savedTitle.isBlank() && savedItems.isEmpty()) {
            repository.createChecklist(id, title, updatedItems)
        } else {
            repository.updateChecklist(id, title, updatedItems)
        }
        savedTitle = title
        savedItems = updatedItems
        return true
    }

    LaunchedEffect(title, items) {
        delay(500)
        saveIfNeeded()
    }

    val activeItems = items.filterNot(ChecklistItem::done)
    val completedItems = items.filter(ChecklistItem::done)
    Column(Modifier.fillMaxSize().testTag("checklist_editor")) {
        Column(Modifier.weight(1f).padding(horizontal = 40.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    modifier =
                        Modifier.clickable {
                            saveIfNeeded()
                            onBack()
                        },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 52.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (pinned) "Unpin" else "Pin",
                    modifier =
                        Modifier.clickable {
                            saveIfNeeded()
                            checklistId?.let { id ->
                                repository.setPinned(id, !pinned)
                                pinned = !pinned
                            }
                        },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(28.dp))
                Text(
                    "Delete",
                    modifier =
                        Modifier.clickable {
                            checklistId?.let(repository::delete)
                            onBack()
                        },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().testTag("checklist_title"),
                placeholder = { Text("Untitled", style = MaterialTheme.typography.displayLarge, fontSize = 56.sp) },
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontSize = 56.sp,
                    ),
                singleLine = true,
            )
            Text(
                "${completedItems.size} of ${items.size} packed",
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(22.dp))
            activeItems.forEach { item ->
                ChecklistItemRow(
                    item = item,
                    onToggle = {
                        items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                        saveIfNeeded(items)
                    },
                    onChange = { text ->
                        items = items.map { if (it.id == item.id) it.copy(text = text) else it }
                    },
                    onDelete = {
                        items = items.filterNot { it.id == item.id }
                        saveIfNeeded(items)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 40.sp)
                Spacer(Modifier.width(18.dp))
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    modifier = Modifier.weight(1f).testTag("checklist_add_item"),
                    placeholder = { Text("Add item", fontSize = 24.sp) },
                    singleLine = true,
                )
                Text(
                    "Add",
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .clickable {
                                val text = newItemText.trim()
                                if (text.isNotEmpty()) {
                                    items += ChecklistItem(UUID.randomUUID().toString(), text, false)
                                    newItemText = ""
                                    saveIfNeeded(items)
                                }
                            }.testTag("checklist_add_button"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (completedItems.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle("Completed ${completedItems.size}")
                completedItems.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        onToggle = {
                            items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                            saveIfNeeded(items)
                        },
                        onChange = { text ->
                            items = items.map { if (it.id == item.id) it.copy(text = text) else it }
                        },
                        onDelete = {
                            items = items.filterNot { it.id == item.id }
                            saveIfNeeded(items)
                        },
                    )
                }
            }
        }
        Text(
            "Clear completed",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        items = items.filterNot(ChecklistItem::done)
                        saveIfNeeded(items)
                    }.padding(vertical = 26.dp)
                    .testTag("clear_completed"),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (item.done) "[x]" else "[ ]",
            modifier = Modifier.clickable(onClick = onToggle).testTag("checklist_toggle_${item.id}"),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 22.sp,
        )
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = item.text,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).testTag("checklist_item_${item.id}"),
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 27.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            singleLine = true,
        )
        Text(
            "×",
            modifier = Modifier.padding(start = 10.dp).clickable(onClick = onDelete),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 28.sp,
        )
    }
}

@Composable
private fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                ).padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolbarAction("B", onBold, FontWeight.Bold)
        ToolbarAction("I", onItalic, FontWeight.Normal)
        ToolbarAction("H", onHeading, FontWeight.Normal)
        ToolbarAction("•≡", onBullet, FontWeight.Normal)
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    action: () -> Unit,
    weight: FontWeight,
) {
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = action).padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 28.sp,
        fontWeight = weight,
    )
}
