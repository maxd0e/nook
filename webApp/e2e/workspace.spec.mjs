import { test, expect } from '@playwright/test'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

const output = join(process.cwd(), 'build', 'kotlin-webpack', 'wasmJs', 'developmentExecutable')
const resources = join(process.cwd(), 'src', 'wasmJsMain', 'resources')

let server
let origin

test.beforeAll(async () => {
    server = createServer(async (request, response) => {
        const path = request.url === '/' ? 'index.html' : request.url.slice(1)
        try {
            const body = await readFile(join(output, path)).catch(() => readFile(join(resources, path)))
            const responseBody = path === 'index.html'
                ? body.toString().replace(/<meta http-equiv="Content-Security-Policy"[^>]*>\r?\n/, '')
                : body
            const type = path.endsWith('.js') || path.endsWith('.mjs') ? 'text/javascript' : path.endsWith('.wasm') ? 'application/wasm' : path.endsWith('.css') ? 'text/css' : 'text/html'
            response.writeHead(200, { 'content-type': type }).end(responseBody)
        } catch {
            response.writeHead(404).end()
        }
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    origin = `http://127.0.0.1:${server.address().port}`
})

test.afterAll(() => {
    server.closeAllConnections()
    server.close()
})

async function signIn(page) {
    await page.addInitScript(() => { window.__NOOK_E2E__ = true })
    await page.goto(origin)
    await page.getByRole('button', { name: 'Create a new key' }).click()
    await page.getByRole('checkbox', { name: 'I saved my private key' }).check()
    await page.getByRole('button', { name: 'Continue' }).click()
    await expect(page.getByLabel('Search notes')).toBeVisible()
}

async function createNote(page, title, body) {
    await page.getByRole('button', { name: 'Create note or checklist' }).click()
    await page.getByRole('button', { name: 'Note' }).click()
    await page.locator('#editor-title').fill(title)
    await page.locator('#editor-body').fill(body)
    await page.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await page.getByRole('button', { name: 'Close note' }).click()
    await page.getByRole('button', { name: new RegExp(title) }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
}

async function createChecklist(page, title) {
    await page.getByRole('button', { name: 'Create note or checklist' }).click()
    await page.getByRole('button', { name: 'Checklist' }).click()
    await page.locator('#editor-title').fill(title)
    await page.locator('#new-item').fill('First item')
    await page.getByRole('button', { name: 'Add' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await page.getByRole('button', { name: 'Close note' }).click()
    await page.getByRole('button', { name: new RegExp(title) }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
}

test('welcome is keyboard accessible at desktop and narrow widths', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 })
    await page.goto(origin)
    await expect(page.getByRole('heading', { name: 'Nook' })).toBeVisible()
    await expect(page.locator('img.nook-mark')).toHaveAttribute('src', 'nook.svg')
    await expect(page.locator('.extension-help')).toHaveCount(0)
    await page.getByRole('button', { name: 'Toggle theme' }).press('Enter')
    await expect(page.locator('html')).toHaveClass(/dark/)
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('button', { name: 'I have a Nostr key' })).toBeVisible()
})

test('notes can be created, formatted, pinned, searched, and deleted', async ({ page }) => {
    await signIn(page)
    await createNote(page, 'Garden plan', 'Spring seeds')
    await page.getByRole('button', { name: 'Edit' }).click()
    await page.getByRole('button', { name: 'B', exact: true }).click()
    await page.getByRole('button', { name: 'H', exact: true }).click()
    await page.getByRole('button', { name: 'Save' }).click()
    await expect(page.locator('.markdown h2 strong')).toContainText('Spring seeds')
    await page.getByRole('button', { name: 'Pin' }).click()
    await page.getByRole('button', { name: 'Close note' }).click()
    await expect(page.getByRole('heading', { name: 'PINNED' })).toBeVisible()
    await page.getByLabel('Search notes').fill('garden')
    await expect(page.getByRole('button', { name: /Garden plan/ })).toBeVisible()
    await page.getByLabel('Search notes').fill('missing')
    await expect(page.getByRole('heading', { name: 'No matching notes' })).toBeVisible()
    await page.getByLabel('Search notes').fill('')
    await page.getByRole('button', { name: /Garden plan/ }).click()
    await page.getByRole('button', { name: 'Delete' }).click()
    await page.getByRole('button', { name: 'Cancel' }).click()
    await expect(page.getByRole('heading', { name: 'Garden plan' })).toBeVisible()
    await page.getByRole('button', { name: 'Delete' }).click()
    await page.getByRole('button', { name: 'Delete' }).last().click()
    await expect(page.getByRole('heading', { name: 'Create your first note or checklist' })).toBeVisible()
})

test('checklists keep completed items separate and confirm destructive changes', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signIn(page)
    await createChecklist(page, 'Packing')
    await page.getByRole('button', { name: 'Toggle First item' }).click()
    await expect(page.getByRole('heading', { name: /COMPLETED 1/ })).toBeVisible()
    await page.getByRole('button', { name: 'Edit' }).click()
    await page.getByRole('button', { name: 'Clear completed' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'Cancel' }).click()
    await expect(page.getByRole('heading', { name: /COMPLETED 1/ })).toBeVisible()
    await page.getByRole('button', { name: 'Clear completed' }).click()
    await page.getByRole('button', { name: 'Delete' }).last().click()
    await expect(page.getByText('0 of 0 packed')).toBeVisible()
    await page.getByRole('button', { name: 'Close note' }).click()
    await expect(page.getByLabel('Search notes')).toBeVisible()
})

test('narrow and desktop layouts preserve read-only and back navigation', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 })
    await signIn(page)
    await expect(page.locator('.notes-pane').getByRole('heading', { name: 'Nook' })).toBeVisible()
    await expect(page.locator('.app-brand-mark')).toHaveAttribute('src', 'nook.svg')
    await expect(page.getByRole('button', { name: 'Toggle theme' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Log out' })).toHaveText('')
    await expect(page.locator('.search-field .app-icon')).toBeVisible()
    await expect(page.getByLabel('Search notes')).toHaveAttribute('placeholder', 'Search')
    await createNote(page, 'Read only', 'No changes yet')
    expect(await page.locator('.editor-header').evaluate(header =>
        [...header.querySelectorAll('button')].map(button => button.id),
    )).toEqual(['editor-edit', 'editor-pin', 'editor-delete', 'close-editor'])
    await expect(page.locator('.detail-pane .sync')).toHaveCount(0)
    await expect(page.locator('.detail-pane .relay-health')).toHaveCount(0)
    await expect(page.locator('.notes-pane .sync')).toHaveCount(1)
    await expect(page.locator('#editor-body')).toHaveCount(0)
    await page.getByRole('button', { name: 'Close note' }).click()
    await expect(page.locator('.notes-pane')).toBeVisible()
    await page.setViewportSize({ width: 390, height: 844 })
    await page.getByRole('button', { name: /Read only/ }).click()
    await expect(page.locator('.workspace.has-detail .notes-pane')).toBeHidden()
    await page.getByRole('button', { name: 'Close note' }).click()
    await expect(page.getByLabel('Search notes')).toBeVisible()
})

test('browser storage remains ciphertext-only and logout clears the active account', async ({ page }) => {
    await signIn(page)
    await createNote(page, 'Sensitive title', 'Sensitive body')
    const records = await page.evaluate(async () => new Promise((resolve, reject) => {
        const request = indexedDB.open('nook-encrypted-events')
        request.onerror = () => reject(request.error)
        request.onsuccess = () => {
            const transaction = request.result.transaction('events', 'readonly')
            const getAll = transaction.objectStore('events').getAll()
            getAll.onsuccess = () => resolve(JSON.stringify(getAll.result))
            getAll.onerror = () => reject(getAll.error)
        }
    }))
    expect(records).not.toContain('Sensitive title')
    expect(records).not.toContain('Sensitive body')
    expect(JSON.parse(records)).not.toHaveLength(0)
    await page.getByRole('button', { name: 'Log out' }).click()
    await expect(page.getByRole('heading', { name: 'Nook' })).toBeVisible()
    await expect(page).toHaveURL(`${origin}/`)
    await expect.poll(() => page.evaluate(async () => new Promise((resolve, reject) => {
        const request = indexedDB.open('nook-encrypted-events')
        request.onerror = () => reject(request.error)
        request.onsuccess = () => {
            const transaction = request.result.transaction('events', 'readonly')
            const getAll = transaction.objectStore('events').getAll()
            getAll.onsuccess = () => resolve(getAll.result)
            getAll.onerror = () => reject(getAll.error)
        }
    }))).toHaveLength(0)
})
