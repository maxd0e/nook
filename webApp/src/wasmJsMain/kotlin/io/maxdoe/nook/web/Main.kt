@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:max-line-length")

package io.maxdoe.nook.web

import io.maxdoe.nook.nostr.RelaySyncHealth
import io.maxdoe.nook.nostr.RelaySyncStatus
import io.maxdoe.nook.notes.ChecklistItem
import io.maxdoe.nook.notes.ChecklistPayload
import io.maxdoe.nook.notes.NookListItem
import io.maxdoe.nook.notes.NotePayload
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise

private val scope = MainScope()
private val controller = WebLoginController()
private var observer: Job? = null
private var route = "#/notes"
private var query = ""
private var confirm: Confirm? = null
private var draft: Draft? = null

private sealed interface Draft {
    var id: String?
    var title: String
    var editing: Boolean

    fun isDirty(): Boolean

    fun markSaved()

    data class Note(
        override var id: String?,
        override var title: String,
        var body: String,
        override var editing: Boolean,
        private var savedTitle: String = title,
        private var savedBody: String = body,
    ) : Draft {
        override fun isDirty() = title != savedTitle || body != savedBody

        override fun markSaved() {
            savedTitle = title
            savedBody = body
        }
    }

    data class Checklist(
        override var id: String?,
        override var title: String,
        var items: List<ChecklistItem>,
        override var editing: Boolean,
        private var savedTitle: String = title,
        private var savedItems: List<ChecklistItem> = items,
    ) : Draft {
        override fun isDirty() = title != savedTitle || items != savedItems

        override fun markSaved() {
            savedTitle = title
            savedItems = items
        }
    }
}

private data class Confirm(
    val title: String,
    val message: String,
    val action: suspend () -> Unit,
)

fun main() {
    applyTheme(WebUiBridge.loadTheme())
    window.onhashchange = {
        val changedRoute = window.location.hash.ifBlank { "#/notes" }
        if (route != changedRoute) {
            route = changedRoute
            draft = null
        }
        render()
    }
    route = window.location.hash.ifBlank { "#/notes" }
    render()
}

private fun render() {
    requireNotNull(document.getElementById("nook-root")).innerHTML =
        when (val screen = controller.screen) {
            WebLoginScreen.Welcome -> welcomeMarkup().replace("</main>", "${compatibilityMarkup()}</main>")
            WebLoginScreen.PasteKey -> pasteMarkup()
            is WebLoginScreen.BackupKey -> backupMarkup(screen)
            WebLoginScreen.SignedIn -> workspaceMarkup()
        }
    bind()
}

private fun bind() {
    click("theme-toggle") {
        applyTheme(
            if (document.documentElement?.classList?.contains("dark") ==
                true
            ) {
                "light"
            } else {
                "dark"
            },
        )
        render()
    }
    when (val screen = controller.screen) {
        WebLoginScreen.Welcome -> {
            if (WebUiBridge.compatibilityError().isNotBlank()) {
                document.getElementById("create-key")?.setAttribute("disabled", "disabled")
                document.getElementById("paste-key")?.setAttribute("disabled", "disabled")
                document.getElementById("extension-login")?.setAttribute("disabled", "disabled")
                return
            }
            click("create-key") {
                controller.createKey()
                render()
            }
            click("paste-key") {
                controller.showPasteKey()
                render()
            }
            click("extension-login") {
                scope.launch {
                    controller.signInWithExtension()
                    observe()
                    render()
                }
            }
        }
        WebLoginScreen.PasteKey -> {
            click("back") {
                controller.showWelcome()
                render()
            }
            click("continue") {
                scope.launch {
                    controller.signInLocal(value("nsec"))
                    observe()
                    render()
                }
            }
        }
        is WebLoginScreen.BackupKey -> {
            click("back") {
                controller.showWelcome()
                render()
            }
            document.getElementById("saved-key")?.addEventListener("change", {
                controller.setBackupConfirmed(
                    (it.target as? HTMLInputElement)?.checked == true,
                )
                ; render()
            })
            click("copy-key") { scope.launch { copy(screen.nsec) } }
            click("continue") {
                scope.launch {
                    controller.completeBackup()
                    observe()
                    render()
                }
            }
        }
        WebLoginScreen.SignedIn -> bindWorkspace()
    }
}

private fun bindWorkspace() {
    click("logout") {
        scope.launch {
            observer?.cancel()
            controller.logout()
            draft = null
            route = "#/notes"
            render()
            window.history.replaceState(null, "", window.location.pathname + window.location.search)
        }
    }
    click("new-note") { nav("#/note/new") }
    click("confirm-cancel") {
        confirm = null
        render()
    }
    click("confirm-ok") {
        scope.launch {
            confirm?.action?.invoke()
            confirm = null
            render()
        }
    }
    (document.getElementById("notes-search") as? HTMLInputElement)?.addEventListener("input", {
        query =
            value("notes-search")
        ; render()
    })
    bindAll("[data-open-id]") { node ->
        node.getAttribute("data-open-id")?.let(::openItem)
    }
    bindEditor()
}

private fun bindEditor() {
    val current = draft ?: return
    (document.getElementById("editor-title") as? HTMLInputElement)?.addEventListener("input", {
        current.title =
            value("editor-title")
    })
    (document.getElementById("editor-body") as? HTMLTextAreaElement)?.addEventListener("input", {
        (current as? Draft.Note)?.body =
            text("editor-body")
    })
    click("close-editor") {
        scope.launch {
            if (!current.isDirty() || save()) {
                nav("#/notes")
            }
        }
    }
    click("editor-edit") {
        current.editing = true
        render()
    }
    click("editor-save") {
        scope.launch {
            save()
            render()
        }
    }
    click("editor-pin") {
        scope.launch {
            save()
            selected()?.let { controller.workspace?.repository?.setPinned(it.id, !it.pinned()) }
            render()
        }
    }
    click("editor-delete") {
        selected()?.let { item ->
            confirm =
                Confirm(
                    "Delete ${if (item.payload is NotePayload) "note" else "checklist"}?",
                    "This item will be permanently deleted.",
                ) {
                    controller.workspace?.repository?.delete(item.id)
                    nav("#/notes")
                }
            ; render()
        }
    }
    click("format-bold") { format("**") }
    click("format-italic") { format("_") }
    click("format-heading") { prefix("## ") }
    click("format-bullet") { prefix("- ") }
    click("format-numbered") { prefix("1. ") }
    click("switch-to-checklist") {
        val note = current as? Draft.Note ?: return@click
        if (note.id == null && note.title.isBlank() && note.body.isBlank()) {
            draft = Draft.Checklist(null, "", emptyList(), editing = true)
            render()
        }
    }
    if (current is Draft.Checklist) bindChecklist(current)
}

private fun bindChecklist(current: Draft.Checklist) {
    val itemTextInputs = document.querySelectorAll("[data-item-text]")
    for (index in 0 until itemTextInputs.length) {
        (itemTextInputs.item(index) as? HTMLInputElement)?.let { input ->
            input.addEventListener("input", {
                input.getAttribute("data-item-text")?.let { id ->
                    current.items =
                        current.items.map { item ->
                            if (item.id == id) item.copy(text = input.value) else item
                        }
                }
            })
        }
    }
    click("checklist-add") {
        value("new-item").trim().takeIf(String::isNotEmpty)?.let {
            current.items =
                current.items + ChecklistItem(randomId(), it, false)
            ; scope.launch {
                save()
                render()
            }
        }
    }
    click("clear-completed") {
        confirm =
            Confirm("Clear completed?", "All completed checklist items will be permanently deleted.") {
                current.items =
                    current.items.filterNot(ChecklistItem::done)
                ; save()
            }
        ; render()
    }
    bindAll("[data-toggle]") { node ->
        node.getAttribute("data-toggle")?.let { id ->
            current.items =
                current.items.map { if (it.id == id) it.copy(done = !it.done) else it }
            ; scope.launch {
                save()
                render()
            }
        }
    }
    bindAll("[data-delete-item]") { node ->
        node.getAttribute("data-delete-item")?.let { id ->
            confirm =
                Confirm("Delete item?", "This checklist item will be permanently deleted.") {
                    current.items =
                        current.items.filterNot { it.id == id }
                    ; save()
                }
            ; render()
        }
    }
}

private fun workspaceMarkup(): String {
    val workspace = controller.workspace ?: return ""
    val selected = selected()
    ensureDraft(selected)
    return "<main class=\"workspace ${if (detail()) "has-detail" else ""}\"><aside class=\"notes-pane\">${listMarkup(
        workspace.repository.search(query),
        workspace.syncStatus.value,
        workspace.syncHealth.value,
    )}</aside><section class=\"detail-pane\">${detailMarkup(selected)}</section></main>${confirmMarkup()}"
}

private fun listMarkup(
    items: List<NookListItem>,
    status: RelaySyncStatus,
    health: RelaySyncHealth,
): String {
    val pinned = items.filter { it.pinned() }
    val rest = items.filterNot { it.pinned() }
    val logoutIcon = icon("logout")
    val empty = if (query.isBlank()) "<div class=empty><h2>Create your first note or checklist</h2><p>Use + to get started.</p></div>" else "<div class=empty><h2>No matching notes</h2><p>Try a different search.</p></div>"
    val cards =
        if (items.isEmpty()) {
            empty
        } else {
            "${if (pinned.isNotEmpty()) {
                "<h2 class=section-label>PINNED</h2>${pinned.joinToString(
                    "",
                ) {
                    card(
                        it,
                    )
                }}"
            } else {
                ""
            }}${if (rest.isNotEmpty()) {
                "<h2 class=section-label>ALL NOTES</h2>${rest.joinToString(
                    "",
                ) { card(it) }}"
            } else {
                ""
            }}"
        }
    val theme = themeButton()
    val statusMarkup = sync(status)
    val healthMarkup = relayHealth(health)
    val searchIcon = icon("search")
    val header =
        "<header class=list-header><div class=app-brand><img class=app-brand-mark src=nook.svg alt=\"\"/><h1 class=notes-title>Nook</h1></div><div class=list-actions>$theme<button id=logout class=logout-button aria-label=\"Log out\">$logoutIcon</button></div></header>"
    val search =
        "<label class=sr-only for=notes-search>Search notes</label><div class=search-field>$searchIcon<input id=notes-search class=search placeholder=\"Search\" value=\"${query.escape()}\"/></div>"
    val addIcon = icon("add")
    return "$header<div class=side-status>$statusMarkup$healthMarkup</div>$search<button id=new-note class=new-note-button>$addIcon<span>New note</span></button>$cards"
}

private fun card(item: NookListItem): String =
    when (val payload = item.payload) {
        is NotePayload -> "<button class=note-card data-open-id=\"${item.id.escape()}\"><span><strong>${payload.title.ifBlank {
            "Untitled"
        }.escape()}</strong><small>${date(
            payload.updatedAt,
        )}</small></span><p>${preview(payload.body).escape()}</p></button>"
        is ChecklistPayload -> "<button class=note-card data-open-id=\"${item.id.escape()}\"><span><strong>${payload.title.ifBlank {
            "Untitled checklist"
        }.escape()}</strong><small>${date(
            payload.updatedAt,
        )}</small></span><p>${payload.items.count(ChecklistItem::done)} of ${payload.items.size} done</p></button>"
        else -> ""
    }

private fun detailMarkup(item: NookListItem?): String {
    if (!detail()) return "<div class=desktop-empty><h1>Select a note</h1><p>Your encrypted notes appear here.</p></div>"
    val current =
        draft
            ?: return "<div class=desktop-empty><h1>Note unavailable</h1><p>It may still be loading.</p></div>"
    val editorAction = if (current.editing) "Save" else "Edit"
    val editorIcon = icon(if (current.editing) "save" else "edit")
    val pinIcon = icon(if (item?.pinned() == true) "pin-filled" else "pin-outline")
    val deleteIcon = icon("delete")
    val closeIcon = icon("close")
    val top = "<header class=editor-header><span class=editor-header-spacer></span><button id=\"editor-${editorAction.lowercase()}\" class=icon-button aria-label=\"$editorAction\">$editorIcon</button><button id=editor-pin class=icon-button aria-label=Pin>$pinIcon</button><button id=editor-delete class=icon-button aria-label=Delete>$deleteIcon</button><button id=close-editor class=icon-button aria-label=\"Close note\">$closeIcon</button></header>"
    return top +
        when (current) {
            is Draft.Note -> noteEditorMarkup(current)
            is Draft.Checklist -> checklistEditor(current)
        }
}

private fun noteEditorMarkup(note: Draft.Note): String =
    if (note.editing) {
        val typeSwitch =
            if (note.id == null && note.title.isBlank() && note.body.isBlank()) {
                "<button id=switch-to-checklist class=switch-type-button>Create checklist instead</button>"
            } else {
                ""
            }
        "<div class=editor>" +
            "<input id=editor-title class=title-input placeholder=Untitled value=\"${note.title.escape()}\"/>" +
            "<textarea id=editor-body placeholder=\"Start writing\">${note.body.escape()}</textarea>" +
            "<nav class=format-toolbar aria-label=Formatting>" +
            "<button id=format-bold aria-label=\"Bold\"><b>B</b></button>" +
            "<button id=format-italic aria-label=\"Italic\"><i>I</i></button>" +
            "<button id=format-heading aria-label=\"Heading\">H</button>" +
            "<button id=format-bullet aria-label=\"Bullet list\">${icon("bullet-list")}</button>" +
            "<button id=format-numbered aria-label=\"Numbered list\">${icon("numbered-list")}</button>" +
            "</nav>$typeSwitch</div>"
    } else {
        val title = note.title.ifBlank { "Untitled" }.escape()
        "<article class=read-view><h1>$title</h1><div class=markdown>${markdown(note.body)}</div></article>"
    }

private fun checklistEditor(list: Draft.Checklist): String {
    val active = list.items.filterNot(ChecklistItem::done)
    val done = list.items.filter(ChecklistItem::done)
    val deleteIcon = icon("delete")

    fun row(item: ChecklistItem) =
        "<li><button data-toggle=\"${item.id.escape()}\" class=\"check ${if (item.done) "done" else ""}\" aria-label=\"Toggle ${item.text.escape()}\">${if (item.done) "✓" else ""}</button>${if (list.editing) "<input data-item-text=\"${item.id.escape()}\" class=checklist-item aria-label=\"Checklist item\" value=\"${item.text.escape()}\"/>" else "<span>${item.text.escape()}</span>"}${if (list.editing) "<button data-delete-item=\"${item.id.escape()}\" class=icon-button aria-label=\"Delete ${item.text.escape()}\">$deleteIcon</button>" else ""}</li>"
    return "<article class=\"checklist-view ${if (list.editing) "editing" else ""}\">${if (list.editing) {
        "<input id=editor-title class=title-input placeholder=\"Untitled checklist\" value=\"${list.title.escape()}\"/>"
    } else {
        "<h1>${list.title.ifBlank {
            "Untitled checklist"
        }.escape()}</h1>"
    }}<p class=progress>${done.size} of ${list.items.size} packed</p><ul>${active.joinToString(
        "",
    ) {
        row(
            it,
        )
    }}</ul>${if (list.editing) "<div class=add-item><input id=new-item placeholder=\"Add item\"/><button id=checklist-add>Add</button></div>" else ""}${if (done
            .isNotEmpty()
    ) {
        "<h2 class=section-label>COMPLETED ${done.size}</h2><ul class=completed>${done.joinToString(
            "",
        ) {
            row(
                it,
            )
        }}</ul>"
    } else {
        ""
    }}${if (list.editing && done.isNotEmpty()) "<button id=clear-completed class=clear-button>Clear completed</button>" else ""}</article>"
}

private fun ensureDraft(item: NookListItem?) {
    if (draft != null || !detail()) {
        return
    }
    val parts = route.removePrefix("#/").split("/")
    val fresh = parts.getOrNull(1) == "new"
    draft =
        when (parts.firstOrNull()) {
            "note" ->
                (item?.payload as? NotePayload)?.let { Draft.Note(item.id, it.title, it.body, false) }
                    ?: if (fresh) Draft.Note(null, "", "", true) else null
            "checklist" ->
                (item?.payload as? ChecklistPayload)?.let { Draft.Checklist(item.id, it.title, it.items, false) }
                    ?: if (fresh) Draft.Checklist(null, "", emptyList(), true) else null
            else -> null
        }
}

private fun openItem(id: String) {
    val item =
        controller.workspace
            ?.repository
            ?.notes
            ?.value
            ?.firstOrNull { it.id == id }
            ?: return
    val destination =
        when (item.payload) {
            is NotePayload -> "#/note/${item.id}"
            is ChecklistPayload -> "#/checklist/${item.id}"
            else -> return
        }
    draft = item.toDraft()
    navigate(destination)
}

private fun NookListItem.toDraft(): Draft =
    when (val currentPayload = payload) {
        is NotePayload -> Draft.Note(id, currentPayload.title, currentPayload.body, editing = false)
        is ChecklistPayload -> Draft.Checklist(id, currentPayload.title, currentPayload.items, editing = false)
        else -> error("Deleted items cannot be opened")
    }

private suspend fun save(): Boolean {
    val current = draft ?: return false
    val repository = controller.workspace?.repository ?: return false
    return try {
        when (current) {
            is Draft.Note -> {
                val isNew = current.id == null
                val id = current.id ?: randomId()
                if (current.title.isBlank() && current.body.isBlank()) return false
                if (isNew) {
                    repository.createNote(
                        id,
                        current.title,
                        current.body,
                    )
                    current.id = id
                } else {
                    repository.updateNote(id, current.title, current.body)
                }
            }
            is Draft.Checklist -> {
                val isNew = current.id == null
                val id = current.id ?: randomId()
                if (current.title.isBlank() && current.items.isEmpty()) return false
                if (isNew) {
                    repository.createChecklist(
                        id,
                        current.title,
                        current.items,
                    )
                    current.id = id
                } else {
                    repository.updateChecklist(id, current.title, current.items)
                }
            }
        }
        current.markSaved()
        current.editing = false
        true
    } catch (_: Throwable) {
        false
    }
}

private fun selected(): NookListItem? =
    controller.workspace?.repository?.notes?.value?.firstOrNull {
        it.id ==
            route.substringAfterLast('/').takeUnless { x -> x == "new" }
    }

private fun NookListItem.pinned() = (payload as? NotePayload)?.pinned ?: (payload as? ChecklistPayload)?.pinned ?: false

private fun detail() = route.startsWith("#/note/") || route.startsWith("#/checklist/")

private fun nav(to: String) {
    draft = null
    navigate(to)
}

private fun navigate(to: String) {
    route = to
    render()
    window.location.hash = to
}

private fun click(
    id: String,
    action: () -> Unit,
) {
    document.getElementById(id)?.addEventListener("click", { action() })
}

private fun bindAll(
    selector: String,
    action: (org.w3c.dom.Element) -> Unit,
) {
    val nodes = document.querySelectorAll(selector)
    for (index in 0 until nodes.length) {
        (nodes.item(index) as? org.w3c.dom.Element)?.let { node ->
            node.addEventListener("click", { action(node) })
        }
    }
}

private fun value(id: String) = (document.getElementById(id) as? HTMLInputElement)?.value.orEmpty()

private fun text(id: String) = (document.getElementById(id) as? HTMLTextAreaElement)?.value.orEmpty()

private fun randomId(): String = WebUiBridge.randomId()

private fun browserNow(): Double = WebUiBridge.now()

private fun format(marker: String) {
    (draft as? Draft.Note)?.let {
        it.body = "$marker${it.body}$marker"
        render()
    }
}

private fun prefix(value: String) {
    (draft as? Draft.Note)?.let {
        it.body = value + it.body
        render()
    }
}

private fun date(seconds: Long): String {
    val age =
        (browserNow() / 1000).toLong() - seconds
    return when {
        age < 60 -> "now"
        age < 3600 -> "${age / 60}m"
        age < 86400 -> "${age / 3600}h"
        age <
            604800 -> "${age / 86400}d"
        else -> "${age / 604800}w"
    }
}

private fun preview(value: String) = value.replace(Regex("[#*_`>-]"), "").trim().take(140)

private fun String.escape() =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun markdown(value: String) =
    value.lineSequence().joinToString("") { line ->
        when {
            line.startsWith("## ") -> "<h2>${inline(line.removePrefix("## "))}</h2>"
            Regex("^\\d+\\. ").containsMatchIn(line) -> "<p class=numbered>${inline(line)}</p>"
            line.startsWith("- ") -> "<p class=bullet>• ${inline(line.removePrefix("- "))}</p>"
            line.isBlank() -> "<br>"
            else -> "<p>${inline(line)}</p>"
        }
    }

private fun inline(value: String) =
    value.escape().replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>").replace(Regex("_(.+?)_"), "<em>$1</em>")

private fun sync(status: RelaySyncStatus) =
    "<span class=\"sync ${status.name.lowercase()}\">${when (status) {
        RelaySyncStatus.Synced -> "Synced"
        RelaySyncStatus.Syncing -> "Syncing"
        RelaySyncStatus.Offline -> "Offline — retrying"
    }}</span>"

private fun relayHealth(health: RelaySyncHealth): String {
    if (health.relays.isEmpty()) return ""
    val rows =
        health.relays.joinToString("") { relay ->
            val host = relay.url.removePrefix("wss://").escape()
            "<li><span>$host</span>" +
                "<span>${relay.status.name.lowercase()}</span></li>"
        }
    return "<details class=relay-health><summary>Relay status</summary><ul>$rows</ul></details>"
}

private fun confirmMarkup() =
    confirm
        ?.let {
            "<div class=dialog-scrim><section class=dialog role=dialog aria-modal=true><h2>${it.title}</h2><p>${it.message}</p><div><button id=confirm-cancel class=secondary>Cancel</button><button id=confirm-ok>Delete</button></div></section></div>"
        }.orEmpty()

private fun observe() {
    val ws =
        controller.workspace ?: return
    observer?.cancel()
    observer =
        scope.launch {
            launch { ws.repository.notes.collect { render() } }
            launch { ws.syncStatus.collect { render() } }
            launch { ws.syncHealth.collect { render() } }
        }
}

private suspend fun copy(key: String) {
    try {
        WebUiBridge.copyText(key).await<JsString>()
        document.getElementById("copy-feedback")?.textContent =
            "Copied"
    } catch (_: Throwable) {
        document.getElementById("copy-feedback")?.textContent =
            "Copy failed. Select and copy the key manually."
    }
}

private fun welcomeMarkup() =
    "<main class=welcome>${themeButton()}<div class=welcome-content><img class=nook-mark src=nook.svg alt=\"Nook logo\"/><h1>Nook</h1><p class=promise>Your encrypted notes stay yours.</p>${error()}<div class=actions><button id=create-key>Create a new key</button><button id=paste-key class=secondary>I have a Nostr key</button><button id=extension-login class=text-button>Sign in with a browser extension</button></div></div><p class=privacy>Your private key never leaves this browser tab.</p></main>"

private fun pasteMarkup() =
    "<main class=\"welcome key-flow\"><button id=back class=back aria-label=Back>←</button>${themeButton()}<div class=welcome-content><p class=eyebrow>Sign in</p><h1>Your Nostr key</h1><p class=promise>Paste your private <code>nsec</code>. It remains in this tab only.</p>${error()}<label for=nsec>Private key</label><input id=nsec type=password autocomplete=off/><button id=continue>Continue</button></div></main>"

private fun backupMarkup(screen: WebLoginScreen.BackupKey) =
    "<main class=\"welcome key-flow\"><button id=back class=back aria-label=Back>←</button>${themeButton()}<div class=welcome-content><p class=eyebrow>Save your private key</p><h1>Keep this safe</h1><p class=promise>This is the only time Nook can show this key. Lost keys cannot be recovered.</p><code class=key-value>${screen.nsec}</code><button id=copy-key class=secondary>Copy private key</button><p id=copy-feedback class=copy-feedback aria-live=polite></p><label class=confirmation><input id=saved-key type=checkbox ${if (screen.savedConfirmed) "checked" else ""}/> I saved my private key</label><button id=continue ${if (screen.savedConfirmed) "" else "disabled"}>Continue</button></div></main>"

private fun error() = controller.error?.let { "<p class=error role=alert>${it.escape()}</p>" }.orEmpty()

private fun compatibilityMarkup(): String {
    val compatibility = WebUiBridge.compatibilityError()
    return if (compatibility.isNotBlank()) "<p class=compatibility role=alert>${compatibility.escape()}</p>" else ""
}

private fun themeButton() =
    "<button id=theme-toggle class=theme-toggle aria-label=\"Toggle theme\">${icon(
        if (document.documentElement?.classList?.contains("dark") == true) "sun" else "moon",
    )}</button>"

private fun icon(name: String): String {
    val path =
        when (name) {
            "delete" -> "<path d=\"M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12Zm2-10h8v10H8V9Zm7.5-5-1-1h-5l-1 1H5v2h14V4h-3.5Z\"/>"
            "close" -> "<path d=\"m6.7 5.3 5.3 5.3 5.3-5.3 1.4 1.4-5.3 5.3 5.3 5.3-1.4 1.4-5.3-5.3-5.3 5.3-1.4-1.4 5.3-5.3-5.3-5.3Z\"/>"
            "add" -> "<path d=\"M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z\"/>"
            "bullet-list" -> "<circle cx=\"5\" cy=\"6\" r=\"1.5\"/><circle cx=\"5\" cy=\"12\" r=\"1.5\"/><circle cx=\"5\" cy=\"18\" r=\"1.5\"/><path d=\"M9 5h10v2H9zm0 6h10v2H9zm0 6h10v2H9z\"/>"
            "numbered-list" -> "<path d=\"M4 5h1v4H4V6H3V5h1Zm-1 7h2v1H4v1h1v1H3v-1h1v-1H3v-1Zm0 6h2v1H4v1h1v1H3v-1h1v-1H3v-1Zm6-13h10v2H9zm0 6h10v2H9zm0 6h10v2H9z\"/>"
            "edit" -> "<path d=\"M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25ZM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83Z\"/>"
            "logout" -> "<path d=\"M5 5c0-1.1.9-2 2-2h5v2H7v14h5v2H7c-1.1 0-2-.9-2-2V5Zm11.6 2.6L15.2 9l2 2H9v2h8.2l-2 2 1.4 1.4 4.4-4.4-4.4-4.4Z\"/>"
            "search" -> "<path d=\"m15.5 14h-.8l-.3-.3a6.5 6.5 0 1 0-.7.7l.3.3v.8l5 5L20.5 19l-5-5Zm-6 0A4.5 4.5 0 1 1 9.5 5a4.5 4.5 0 0 1 0 9Z\"/>"
            "moon" -> "<path d=\"M20.5 15.5C15.81 16.55 11.45 13.19 12.5 8.5c.41-1.84 1.4-3.48 2.76-4.71C10.52 2.76 6 6.39 6 11.5 6 15.64 9.36 19 13.5 19c3.08 0 5.73-1.86 7-3.5Z\"/>"
            "pin-filled" -> "<path d=\"M16 9V4l1-1V2H7v1l1 1v5c0 1.1-.9 2-2 2v2h5v7l1 1 1-1v-7h5v-2c-1.1 0-2-.9-2-2Z\"/>"
            "pin-outline" -> "<path fill=\"none\" stroke=\"currentColor\" stroke-linejoin=\"round\" stroke-width=\"1.5\" d=\"M16 9V4l1-1V2H7v1l1 1v5c0 1.1-.9 2-2 2v2h5v7l1 1 1-1v-7h5v-2c-1.1 0-2-.9-2-2Z\"/>"
            "save" -> "<path d=\"M17 3H5c-1.1 0-1.99.9-1.99 2L3 19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4Zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3Zm3-10H5V5h10v4Z\"/>"
            "sun" -> "<path d=\"M12 4a1 1 0 0 0 1-1V1a1 1 0 0 0-2 0v2a1 1 0 0 0 1 1m0 16a1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-2a1 1 0 0 0-1-1M4 12a1 1 0 0 0-1-1H1a1 1 0 0 0 0 2h2a1 1 0 0 0 1-1m19-1h-2a1 1 0 0 0 0 2h2a1 1 0 0 0 0-2M6.34 7.76a1 1 0 0 0 1.42-1.42L6.34 4.93a1 1 0 1 0-1.41 1.41l1.41 1.42Zm11.32 8.48a1 1 0 0 0-1.42 1.42l1.41 1.41a1 1 0 0 0 1.42-1.41l-1.41-1.42Zm0-8.48 1.42-1.42a1 1 0 0 0-1.42-1.41l-1.42 1.41a1 1 0 0 0 1.42 1.42M6.34 16.24l-1.41 1.42a1 1 0 0 0 1.41 1.41l1.42-1.41a1 1 0 0 0-1.42-1.42M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10\"/>"
            else -> error("Unknown icon: $name")
        }
    return "<svg class=app-icon viewBox=\"0 0 24 24\" aria-hidden=true focusable=false>$path</svg>"
}

private fun applyTheme(theme: String) {
    document.documentElement?.classList?.toggle("dark", theme == "dark")
    WebUiBridge.saveTheme(theme)
}

@JsModule("./webUiBridge.mjs")
private external object WebUiBridge {
    fun loadTheme(): String

    fun saveTheme(theme: String)

    fun copyText(value: String): Promise<JsString>

    fun randomId(): String

    fun now(): Double

    fun compatibilityError(): String
}
