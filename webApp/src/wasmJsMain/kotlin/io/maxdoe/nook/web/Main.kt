@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:max-line-length")

package io.maxdoe.nook.web

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
private var addOpen = false
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
        route = window.location.hash.ifBlank { "#/notes" }
        draft = null
        render()
        null
    }
    route = window.location.hash.ifBlank { "#/notes" }
    render()
}

private fun render() {
    requireNotNull(document.getElementById("nook-root")).innerHTML =
        when (val screen = controller.screen) {
            WebLoginScreen.Welcome -> welcomeMarkup()
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
    }
    when (val screen = controller.screen) {
        WebLoginScreen.Welcome -> {
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
            route = "#/notes"
            draft = null
            render()
        }
    }
    click("add-toggle") {
        addOpen = !addOpen
        render()
    }
    click("new-note") { nav("#/note/new") }
    click("new-checklist") { nav("#/checklist/new") }
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
    bindAll("[data-open]") { nav(it.getAttribute("data-open") ?: "#/notes") }
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
    click("editor-back") {
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
    )}</aside><section class=\"detail-pane\">${detailMarkup(
        selected,
        workspace.syncStatus.value,
    )}</section></main>${confirmMarkup()}"
}

private fun listMarkup(
    items: List<NookListItem>,
    status: RelaySyncStatus,
): String {
    val pinned = items.filter { it.pinned() }
    val rest = items.filterNot { it.pinned() }
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
    return "<header class=list-header><span></span>${sync(
        status,
    )}${themeButton()}<button id=logout class=icon-button aria-label=\"Log out\">↪</button></header><label class=sr-only for=notes-search>Search notes</label><input id=notes-search class=search placeholder=Search value=\"${query.escape()}\"/>$cards<div class=\"speed-dial ${if (addOpen) "open" else ""}\"><button id=new-checklist class=speed-action>Checklist</button><button id=new-note class=speed-action>Note</button><button id=add-toggle class=add-button aria-label=\"${if (addOpen) "Close add menu" else "Create note or checklist"}\">+</button></div>"
}

private fun card(item: NookListItem): String =
    when (val payload = item.payload) {
        is NotePayload -> "<button class=note-card data-open=\"#/note/${item.id}\"><span><strong>${payload.title.ifBlank {
            "Untitled"
        }.escape()}</strong><small>${date(
            payload.updatedAt,
        )}</small></span><p>${preview(payload.body).escape()}</p></button>"
        is ChecklistPayload -> "<button class=note-card data-open=\"#/checklist/${item.id}\"><span><strong>${payload.title.ifBlank {
            "Untitled checklist"
        }.escape()}</strong><small>${date(
            payload.updatedAt,
        )}</small></span><p>${payload.items.count(ChecklistItem::done)} of ${payload.items.size} done</p></button>"
        else -> ""
    }

private fun detailMarkup(
    item: NookListItem?,
    status: RelaySyncStatus,
): String {
    if (!detail()) return "<div class=desktop-empty><h1>Select a note</h1><p>Your encrypted notes appear here.</p></div>"
    val current = draft ?: return ""
    val top = "<header class=editor-header><button id=editor-back class=icon-button aria-label=Back>←</button>${sync(
        status,
    )}<button id=\"${if (current.editing) "editor-save" else "editor-edit"}\" class=icon-button>${if (current.editing) "Save" else "Edit"}</button><button id=editor-pin class=icon-button aria-label=Pin>⌖</button><button id=editor-delete class=icon-button aria-label=Delete>⌫</button></header>"
    return top +
        when (current) {
            is Draft.Note -> noteEditor(current)
            is Draft.Checklist -> checklistEditor(current)
        }
}

private fun noteEditor(note: Draft.Note): String =
    if (note.editing) {
        "<div class=editor><input id=editor-title class=title-input placeholder=Untitled value=\"${note.title.escape()}\"/><textarea id=editor-body placeholder=\"Start writing…\">${note.body.escape()}</textarea><nav class=format-toolbar aria-label=Formatting><button id=format-bold><b>B</b></button><button id=format-italic><i>I</i></button><button id=format-heading>H</button><button id=format-bullet>•</button></nav></div>"
    } else {
        "<article class=read-view><h1>${note.title.ifBlank {
            "Untitled"
        }.escape()}</h1><div class=markdown>${markdown(
            note.body,
        )}</div></article>"
    }

private fun checklistEditor(list: Draft.Checklist): String {
    val active = list.items.filterNot(ChecklistItem::done)
    val done = list.items.filter(ChecklistItem::done)

    fun row(item: ChecklistItem) =
        "<li><button data-toggle=\"${item.id.escape()}\" class=\"check ${if (item.done) "done" else ""}\" aria-label=\"Toggle ${item.text.escape()}\">${if (item.done) "✓" else ""}</button>${if (list.editing) "<input data-item-text=\"${item.id.escape()}\" class=checklist-item aria-label=\"Checklist item\" value=\"${item.text.escape()}\"/>" else "<span>${item.text.escape()}</span>"}${if (list.editing) "<button data-delete-item=\"${item.id.escape()}\" class=icon-button aria-label=\"Delete ${item.text.escape()}\">×</button>" else ""}</li>"
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
    window.location.hash = to
    if (route == to) {
        route = to
        render()
    }
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
    "<main class=welcome>${themeButton()}<div class=welcome-content><div class=nook-mark>⌂</div><h1>Nook</h1><p class=promise>Your encrypted notes stay yours.</p>${error()}<div class=actions><button id=create-key>Create a new key</button><button id=paste-key class=secondary>I have a Nostr key</button><button id=extension-login class=text-button>Sign in with a browser extension</button></div></div><p class=privacy>Your private key never leaves this browser tab.</p></main>"

private fun pasteMarkup() =
    "<main class=\"welcome key-flow\"><button id=back class=back aria-label=Back>←</button>${themeButton()}<div class=welcome-content><p class=eyebrow>Sign in</p><h1>Your Nostr key</h1><p class=promise>Paste your private <code>nsec</code>. It remains in this tab only.</p>${error()}<label for=nsec>Private key</label><input id=nsec type=password autocomplete=off/><button id=continue>Continue</button></div></main>"

private fun backupMarkup(screen: WebLoginScreen.BackupKey) =
    "<main class=\"welcome key-flow\"><button id=back class=back aria-label=Back>←</button>${themeButton()}<div class=welcome-content><p class=eyebrow>Save your private key</p><h1>Keep this safe</h1><p class=promise>This is the only time Nook can show this key. Lost keys cannot be recovered.</p><code class=key-value>${screen.nsec}</code><button id=copy-key class=secondary>Copy private key</button><p id=copy-feedback class=copy-feedback aria-live=polite></p><label class=confirmation><input id=saved-key type=checkbox ${if (screen.savedConfirmed) "checked" else ""}/> I saved my private key</label><button id=continue ${if (screen.savedConfirmed) "" else "disabled"}>Continue</button></div></main>"

private fun error() = controller.error?.let { "<p class=error role=alert>${it.escape()}</p>" }.orEmpty()

private fun themeButton() = "<button id=theme-toggle class=theme-toggle aria-label=\"Toggle theme\">◐</button>"

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
}
