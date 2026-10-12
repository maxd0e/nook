@file:Suppress("MaxLineLength", "TooManyFunctions")

package io.maxdoe.nook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.maxdoe.nook.nostr.RelayFailureReason
import io.maxdoe.nook.nostr.RelaySyncHealth
import io.maxdoe.nook.nostr.RelaySyncStatus
import io.maxdoe.nook.notes.ChecklistItem
import io.maxdoe.nook.notes.ChecklistPayload
import io.maxdoe.nook.notes.NookListItem
import io.maxdoe.nook.notes.NotePayload
import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private sealed interface NotesDestination {
    data object List : NotesDestination

    data class Editor(
        val noteId: String?,
        val startsInEditMode: Boolean,
    ) : NotesDestination

    data class ChecklistEditor(
        val checklistId: String?,
        val startsInEditMode: Boolean,
    ) : NotesDestination
}

private val OfflineSyncStatus = MutableStateFlow(RelaySyncStatus.Offline)
private val OfflineRelayHealth = MutableStateFlow(RelaySyncHealth())

private enum class DeleteTarget(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    Note("Delete note?", "This note will be permanently deleted.", "Delete"),
    Checklist("Delete checklist?", "This checklist will be permanently deleted.", "Delete"),
    ChecklistItem("Delete item?", "This checklist item will be permanently deleted.", "Delete"),
    CompletedItems("Clear completed?", "All completed checklist items will be permanently deleted.", "Clear"),
}

@Composable
fun NookNotes(
    repository: NoteRepository,
    syncStatus: StateFlow<RelaySyncStatus> = OfflineSyncStatus,
    syncHealth: StateFlow<RelaySyncHealth> = OfflineRelayHealth,
    onLogout: () -> Unit,
) {
    var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
    when (val current = destination) {
        NotesDestination.List ->
            NotesList(
                repository = repository,
                onNewNote = { destination = NotesDestination.Editor(null, startsInEditMode = true) },
                onNewChecklist = { destination = NotesDestination.ChecklistEditor(null, startsInEditMode = true) },
                onOpenNote = { destination = NotesDestination.Editor(it, startsInEditMode = false) },
                onOpenChecklist = { destination = NotesDestination.ChecklistEditor(it, startsInEditMode = false) },
                syncStatus = syncStatus,
                syncHealth = syncHealth,
                onLogout = onLogout,
            )
        is NotesDestination.Editor ->
            NoteEditor(
                repository = repository,
                existingId = current.noteId,
                startsInEditMode = current.startsInEditMode,
                syncStatus = syncStatus,
                syncHealth = syncHealth,
                onBack = { destination = NotesDestination.List },
            )
        is NotesDestination.ChecklistEditor ->
            ChecklistEditor(
                repository = repository,
                existingId = current.checklistId,
                startsInEditMode = current.startsInEditMode,
                syncStatus = syncStatus,
                syncHealth = syncHealth,
                onBack = { destination = NotesDestination.List },
            )
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
    syncStatus: StateFlow<RelaySyncStatus>,
    syncHealth: StateFlow<RelaySyncHealth>,
    onLogout: () -> Unit,
) {
    val notes by repository.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    val matching = remember(notes, query) { repository.search(query) }
    val pinned = matching.filter { it.payload.isPinned() }
    val unpinned = matching - pinned.toSet()
    var addMenuExpanded by remember { mutableStateOf(false) }
    val currentSyncStatus by syncStatus.collectAsState()
    val currentSyncHealth by syncHealth.collectAsState()

    Box(Modifier.fillMaxSize().testTag("notes_list")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    SyncStatus(currentSyncStatus, currentSyncHealth)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onLogout, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logout),
                            contentDescription = "Log out",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("notes_search"),
                    singleLine = true,
                    leadingIcon = { Text("⌕", fontSize = 32.sp) },
                    placeholder = { Text("Search", fontSize = 16.sp) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            }
            if (matching.isEmpty()) {
                item { EmptyNotesState(hasQuery = query.isNotBlank()) }
            }
            if (pinned.isNotEmpty()) {
                item { NotesListSectionTitle("Pinned") }
                items(pinned, key = NookListItem::id) { NookCard(it, onOpenNote, onOpenChecklist) }
            }
            if (unpinned.isNotEmpty()) {
                item { NotesListSectionTitle("All notes") }
                items(unpinned, key = NookListItem::id) { NookCard(it, onOpenNote, onOpenChecklist) }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
        if (addMenuExpanded) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { addMenuExpanded = false }
                    .testTag("add_menu_scrim"),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 52.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = addMenuExpanded,
                enter =
                    fadeIn(tween(durationMillis = 180, delayMillis = 70)) +
                        scaleIn(tween(durationMillis = 180, delayMillis = 70), initialScale = 0.7f) +
                        slideInVertically(tween(durationMillis = 180, delayMillis = 70)) { it / 2 },
                exit = fadeOut(tween(durationMillis = 120)) + scaleOut(tween(durationMillis = 120)),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        addMenuExpanded = false
                        onNewChecklist()
                    },
                    text = { Text("Checklist") },
                    icon = { Text("✓") },
                    modifier = Modifier.testTag("new_checklist"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(
                visible = addMenuExpanded,
                enter =
                    fadeIn(tween(durationMillis = 180)) +
                        scaleIn(tween(durationMillis = 180), initialScale = 0.7f) +
                        slideInVertically(tween(durationMillis = 180)) { it / 2 },
                exit = fadeOut(tween(durationMillis = 120)) + scaleOut(tween(durationMillis = 120)),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        addMenuExpanded = false
                        onNewNote()
                    },
                    text = { Text("Note") },
                    icon = { Text("+") },
                    modifier = Modifier.testTag("new_note_action"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
            FloatingActionButton(
                onClick = { addMenuExpanded = !addMenuExpanded },
                modifier =
                    Modifier
                        .size(56.dp)
                        .semantics {
                            contentDescription =
                                if (addMenuExpanded) "Close add menu" else "Create note or checklist"
                        }.testTag("new_note"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "+",
                    modifier = Modifier.rotate(if (addMenuExpanded) 45f else 0f),
                    fontSize = 28.sp,
                )
            }
        }
    }
}

@Composable
private fun SyncStatus(
    status: RelaySyncStatus,
    health: RelaySyncHealth,
) {
    val failure = health.failure
    val (label, color) =
        when (status) {
            RelaySyncStatus.Synced -> "Synced" to Color(0xFF2E7D5B)
            RelaySyncStatus.Syncing -> "Syncing" to MaterialTheme.colorScheme.primary
            RelaySyncStatus.Offline ->
                "Offline — retrying" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("sync_status")) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (status == RelaySyncStatus.Offline && failure != null) {
                Text(
                    failure.userMessage(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun RelayFailureReason.userMessage(): String =
    when (this) {
        RelayFailureReason.Network -> "Network unavailable"
        RelayFailureReason.SecureConnection -> "Secure connection failed"
        RelayFailureReason.RelayRejected -> "Relay rejected connection"
        RelayFailureReason.Unknown -> "Connection failed"
    }

@Composable
private fun EmptyNotesState(hasQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasQuery) "No matching notes" else "Create your first note or checklist",
            style = MaterialTheme.typography.displayLarge,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasQuery) "Try a different search." else "Use + to get started.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
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
private fun NotesListSectionTitle(label: String) {
    Text(
        label.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            when (val payload = item.payload) {
                is NotePayload -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            payload.title.ifBlank { "Untitled" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = 28.sp,
                        )
                        Text(
                            relativeDate(payload.updatedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (payload.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            payload.body.replace(Regex("[#*_`>-]"), "").trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 17.sp,
                            maxLines = 2,
                        )
                    }
                }
                is ChecklistPayload -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            payload.title.ifBlank { "Untitled checklist" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = 28.sp,
                        )
                        Text(
                            relativeDate(payload.updatedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${payload.items.count(ChecklistItem::done)} of ${payload.items.size} done",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
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

private fun relativeDate(epochSeconds: Long): String {
    val date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else ->
            date.format(
                DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, uuuu"),
            )
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun NoteEditor(
    repository: NoteRepository,
    existingId: String?,
    startsInEditMode: Boolean,
    syncStatus: StateFlow<RelaySyncStatus>,
    syncHealth: StateFlow<RelaySyncHealth>,
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
    var saveError by remember(existingId) { mutableStateOf<String?>(null) }
    var isEditing by remember(existingId) { mutableStateOf(startsInEditMode) }
    var deleteRequested by remember(existingId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentSyncStatus by syncStatus.collectAsState()
    val currentSyncHealth by syncHealth.collectAsState()

    suspend fun saveIfNeeded(): Boolean {
        if (title == savedTitle && body.text == savedBody) return false
        if (title.isBlank() && body.text.isBlank()) return false
        val id = noteId ?: UUID.randomUUID().toString().also { noteId = it }
        return runCatching {
            if (existingId == null && savedTitle.isBlank() && savedBody.isBlank()) {
                repository.createNote(id, title, body.text)
                if (pinned) repository.setPinned(id, true)
            } else {
                repository.updateNote(id, title, body.text)
            }
        }.fold(
            onSuccess = {
                savedTitle = title
                savedBody = body.text
                saveError = null
                true
            },
            onFailure = {
                saveError = "Could not save: ${it.message ?: "signer request failed."}"
                false
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (isEditing) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(horizontal = 24.dp)
            .testTag("note_editor"),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                modifier =
                    Modifier.clickable {
                        coroutineScope.launch {
                            if ((title == savedTitle && body.text == savedBody) || saveIfNeeded()) onBack()
                        }
                    },
                color = MaterialTheme.colorScheme.primary,
                fontSize = 40.sp,
            )
            Spacer(Modifier.weight(1f))
            SyncStatus(currentSyncStatus, currentSyncHealth)
            Spacer(Modifier.width(20.dp))
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        if (isEditing) {
                            if (saveIfNeeded() || (title == savedTitle && body.text == savedBody)) isEditing = false
                        } else {
                            isEditing = true
                        }
                    }
                },
                modifier = Modifier.size(40.dp).testTag("note_edit_save"),
            ) {
                Icon(
                    painter = painterResource(if (isEditing) R.drawable.ic_save else R.drawable.ic_edit),
                    contentDescription = if (isEditing) "Save note" else "Edit note",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        val hasUnsavedContent = title != savedTitle || body.text != savedBody
                        if (hasUnsavedContent && !saveIfNeeded()) return@launch
                        val id = noteId ?: return@launch
                        runCatching { repository.setPinned(id, !pinned) }
                            .onSuccess { pinned = !pinned }
                            .onFailure {
                                saveError = "Could not save: ${it.message ?: "signer request failed."}"
                            }
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline,
                        ),
                    contentDescription = if (pinned) "Unpin note" else "Pin note",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { deleteRequested = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete note",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        saveError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("note_save_error"),
            )
        }
        if (isEditing) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().testTag("note_title"),
                placeholder = { Text("Untitled", style = MaterialTheme.typography.displayLarge, fontSize = 30.sp) },
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontSize = 30.sp,
                    ),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("note_body"),
                placeholder = { Text("Start writing…", fontSize = 17.sp) },
                textStyle =
                    androidx.compose.ui.text
                        .TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
            )
            MarkdownToolbar(
                onBold = { body = toggleInlineMarkdown(body, "**") },
                onItalic = { body = toggleInlineMarkdown(body, "_") },
                onHeading = { body = insertHeading(body) },
                onBullet = { body = insertBullet(body) },
            )
        } else {
            NoteReadContent(title, body.text)
        }
    }
    if (deleteRequested) {
        DeleteConfirmation(
            target = DeleteTarget.Note,
            onDismiss = { deleteRequested = false },
            onConfirm = {
                deleteRequested = false
                coroutineScope.launch {
                    val id = noteId ?: return@launch
                    runCatching { repository.delete(id) }
                        .onSuccess { onBack() }
                        .onFailure { saveError = "Could not save: ${it.message ?: "signer request failed."}" }
                }
            },
        )
    }
}

@Composable
private fun NoteReadContent(
    title: String,
    body: String,
) {
    Text(
        title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.displayLarge,
        fontSize = 32.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(20.dp))
    body.lineSequence().forEach { line ->
        when {
            line.startsWith("## ") -> {
                Text(
                    line.removePrefix("## "),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            line.startsWith("- ") -> {
                Text(
                    text = buildMarkdownText("•  ${line.removePrefix("- ")}"),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            line.isBlank() -> Spacer(Modifier.height(12.dp))
            else -> Text(buildMarkdownText(line), fontSize = 17.sp, lineHeight = 26.sp)
        }
    }
}

private fun buildMarkdownText(value: String): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        var bold = false
        var italic = false
        while (index < value.length) {
            when {
                value.startsWith("**", index) -> {
                    bold = !bold
                    index += 2
                }
                value[index] == '_' -> {
                    italic = !italic
                    index += 1
                }
                else -> {
                    val next =
                        listOf(value.indexOf("**", index), value.indexOf('_', index))
                            .filter { it >= 0 }
                            .minOrNull() ?: value.length
                    withStyle(
                        SpanStyle(
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        ),
                    ) { append(value.substring(index, next)) }
                    index = next
                }
            }
        }
    }

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun ChecklistEditor(
    repository: NoteRepository,
    existingId: String?,
    startsInEditMode: Boolean,
    syncStatus: StateFlow<RelaySyncStatus>,
    syncHealth: StateFlow<RelaySyncHealth>,
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
    var saveError by remember(existingId) { mutableStateOf<String?>(null) }
    var isEditing by remember(existingId) { mutableStateOf(startsInEditMode) }
    var deleteRequested by remember(existingId) { mutableStateOf(false) }
    var itemPendingDelete by remember(existingId) { mutableStateOf<ChecklistItem?>(null) }
    var clearCompletedRequested by remember(existingId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentSyncStatus by syncStatus.collectAsState()
    val currentSyncHealth by syncHealth.collectAsState()

    suspend fun saveIfNeeded(updatedItems: List<ChecklistItem> = items): Boolean {
        if (title == savedTitle && updatedItems == savedItems) return false
        if (title.isBlank() && updatedItems.isEmpty() && checklistId == null) return false
        val id = checklistId ?: UUID.randomUUID().toString().also { checklistId = it }
        return runCatching {
            if (existingId == null && savedTitle.isBlank() && savedItems.isEmpty()) {
                repository.createChecklist(id, title, updatedItems)
            } else {
                repository.updateChecklist(id, title, updatedItems)
            }
        }.fold(
            onSuccess = {
                savedTitle = title
                savedItems = updatedItems
                saveError = null
                true
            },
            onFailure = {
                saveError = "Could not save: ${it.message ?: "signer request failed."}"
                false
            },
        )
    }

    val activeItems = items.filterNot(ChecklistItem::done)
    val completedItems = items.filter(ChecklistItem::done)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("checklist_editor"),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    modifier =
                        Modifier.clickable {
                            coroutineScope.launch {
                                if ((title == savedTitle && items == savedItems) || saveIfNeeded()) onBack()
                            }
                        },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 40.sp,
                )
                Spacer(Modifier.weight(1f))
                SyncStatus(currentSyncStatus, currentSyncHealth)
                Spacer(Modifier.width(20.dp))
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (isEditing) {
                                if (saveIfNeeded() || (title == savedTitle && items == savedItems)) isEditing = false
                            } else {
                                isEditing = true
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp).testTag("checklist_edit_save"),
                ) {
                    Icon(
                        painter = painterResource(if (isEditing) R.drawable.ic_save else R.drawable.ic_edit),
                        contentDescription = if (isEditing) "Save checklist" else "Edit checklist",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val hasUnsavedContent = title != savedTitle || items != savedItems
                            if (hasUnsavedContent && !saveIfNeeded()) return@launch
                            val id = checklistId ?: return@launch
                            runCatching { repository.setPinned(id, !pinned) }
                                .onSuccess { pinned = !pinned }
                                .onFailure {
                                    saveError = "Could not save: ${it.message ?: "signer request failed."}"
                                }
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline,
                            ),
                        contentDescription = if (pinned) "Unpin checklist" else "Pin checklist",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = { deleteRequested = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete checklist",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("checklist_save_error"))
            }
            if (isEditing) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().testTag("checklist_title"),
                    placeholder = { Text("Untitled", style = MaterialTheme.typography.displayLarge, fontSize = 30.sp) },
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontSize = 30.sp,
                        ),
                    singleLine = true,
                )
                Text(
                    "${completedItems.size} of ${items.size} packed",
                    modifier = Modifier.padding(top = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(18.dp))
                activeItems.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        onToggle = {
                            items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                            coroutineScope.launch { saveIfNeeded(items) }
                        },
                        onChange = { text ->
                            items = items.map { if (it.id == item.id) it.copy(text = text) else it }
                        },
                        onDelete = { itemPendingDelete = item },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    TextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag("checklist_add_item"),
                        placeholder = { Text("Add item", fontSize = 18.sp) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
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
                                        coroutineScope.launch { saveIfNeeded(items) }
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
                                coroutineScope.launch { saveIfNeeded(items) }
                            },
                            onChange = { text ->
                                items = items.map { if (it.id == item.id) it.copy(text = text) else it }
                            },
                            onDelete = { itemPendingDelete = item },
                        )
                    }
                }
            } else {
                ChecklistReadContent(
                    title = title,
                    items = items,
                    onToggle = { item ->
                        items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                        coroutineScope.launch { saveIfNeeded(items) }
                    },
                )
            }
        }
        if (isEditing) {
            Text(
                "Clear completed",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { clearCompletedRequested = true }
                        .padding(vertical = 22.dp)
                        .testTag("clear_completed"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (deleteRequested) {
        DeleteConfirmation(
            target = DeleteTarget.Checklist,
            onDismiss = { deleteRequested = false },
            onConfirm = {
                deleteRequested = false
                coroutineScope.launch {
                    val id = checklistId ?: return@launch
                    runCatching { repository.delete(id) }
                        .onSuccess { onBack() }
                        .onFailure { saveError = "Could not save: ${it.message ?: "signer request failed."}" }
                }
            },
        )
    }
    itemPendingDelete?.let { item ->
        DeleteConfirmation(
            target = DeleteTarget.ChecklistItem,
            onDismiss = { itemPendingDelete = null },
            onConfirm = {
                itemPendingDelete = null
                items = items.filterNot { it.id == item.id }
                coroutineScope.launch { saveIfNeeded(items) }
            },
        )
    }
    if (clearCompletedRequested) {
        DeleteConfirmation(
            target = DeleteTarget.CompletedItems,
            onDismiss = { clearCompletedRequested = false },
            onConfirm = {
                clearCompletedRequested = false
                items = items.filterNot(ChecklistItem::done)
                coroutineScope.launch { saveIfNeeded(items) }
            },
        )
    }
}

@Composable
private fun ChecklistReadContent(
    title: String,
    items: List<ChecklistItem>,
    onToggle: (ChecklistItem) -> Unit,
) {
    val activeItems = items.filterNot(ChecklistItem::done)
    val completedItems = items.filter(ChecklistItem::done)
    Text(
        title.ifBlank { "Untitled checklist" },
        style = MaterialTheme.typography.displayLarge,
        fontSize = 32.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "${completedItems.size} of ${items.size} packed",
        modifier = Modifier.padding(top = 10.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
    )
    Spacer(Modifier.height(18.dp))
    activeItems.forEach { item -> ChecklistReadItem(item, onToggle) }
    if (completedItems.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        SectionTitle("Completed ${completedItems.size}")
        completedItems.forEach { item -> ChecklistReadItem(item, onToggle) }
    }
}

@Composable
private fun ChecklistReadItem(
    item: ChecklistItem,
    onToggle: (ChecklistItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(item) }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (item.done) "✓" else "○",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            item.text,
            fontSize = 18.sp,
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (item.done) "✓" else "○",
            modifier = Modifier.clickable(onClick = onToggle).testTag("checklist_toggle_${item.id}"),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
        )
        Spacer(Modifier.width(16.dp))
        TextField(
            value = item.text,
            onValueChange = onChange,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag("checklist_item_${item.id}"),
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
        Text(
            "×",
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .clickable(onClick = onDelete)
                    .testTag("checklist_delete_${item.id}"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 22.sp,
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
                .windowInsetsPadding(WindowInsets.navigationBars)
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

@Composable
private fun DeleteConfirmation(
    target: DeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = { Text(target.message) },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("delete_cancel")) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_confirm")) {
                Text(target.confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
