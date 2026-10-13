import { test, expect } from '@playwright/test'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { WebSocketServer } from 'ws'

const output = join(process.cwd(), 'build', 'dist', 'wasmJs', 'productionExecutable')

let server
let relay
let origin
const sockets = new Set()

test.beforeAll(async () => {
    server = createServer(async (request, response) => {
        const path = request.url === '/' ? 'index.html' : request.url.slice(1)
        try {
            const body = await readFile(join(output, path))
            const type = path.endsWith('.js') ? 'text/javascript' : path.endsWith('.wasm') ? 'application/wasm' : path.endsWith('.css') ? 'text/css' : 'text/html'
            response.writeHead(200, { 'content-type': type }).end(body)
        } catch {
            response.writeHead(404).end()
        }
    })
    relay = new WebSocketServer({ noServer: true })
    server.on('connection', socket => {
        sockets.add(socket)
        socket.on('close', () => sockets.delete(socket))
    })
    server.on('upgrade', (request, socket, head) => relay.handleUpgrade(request, socket, head, ws => {
        ws.on('message', () => {})
    }))
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    origin = `http://127.0.0.1:${server.address().port}`
})

test.afterAll(async () => {
    relay.clients.forEach(client => client.terminate())
    sockets.forEach(socket => socket.destroy())
    await new Promise(resolve => relay.close(resolve))
    await new Promise(resolve => server.close(resolve))
})

test('welcome is keyboard accessible at desktop and narrow widths', async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 800 })
    await page.goto(origin)
    await expect(page.getByRole('heading', { name: 'Nook' })).toBeVisible()
    await page.getByRole('button', { name: 'Toggle theme' }).press('Enter')
    await expect(page.locator('html')).toHaveClass(/dark/)
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('button', { name: 'I have a Nostr key' })).toBeVisible()
})
