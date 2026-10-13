import { test, expect } from '@playwright/test'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { join, normalize } from 'node:path'

const output = join(process.cwd(), 'build', 'dist', 'wasmJs', 'productionExecutable')
const csp = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self' wss://relay.damus.io wss://nos.lol wss://relay.primal.net; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

let server
let origin

test.beforeAll(async () => {
    server = createServer(async (request, response) => {
        const requestPath = request.url === '/' ? 'index.html' : request.url.slice(1)
        const file = normalize(join(output, requestPath))
        if (!file.startsWith(output)) {
            response.writeHead(400).end()
            return
        }
        try {
            const body = await readFile(file)
            const type = requestPath.endsWith('.js') || requestPath.endsWith('.mjs') ? 'text/javascript' : requestPath.endsWith('.wasm') ? 'application/wasm' : requestPath.endsWith('.css') ? 'text/css' : requestPath.endsWith('.svg') ? 'image/svg+xml' : 'text/html'
            response.writeHead(200, { 'content-type': type, 'content-security-policy': csp }).end(body)
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

test('production bundle starts under the release content-security-policy', async ({ page }) => {
    const response = await page.goto(origin)
    expect(response?.headers()['content-security-policy']).toBe(csp)
    await expect(page.getByRole('heading', { name: 'Nook' })).toBeVisible()
})
