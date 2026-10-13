const { createServer } = require("node:http")
const { createReadStream, existsSync, readFileSync } = require("node:fs")
const { extname, join, normalize } = require("node:path")

const output = join(__dirname, "build", "kotlin-webpack", "wasmJs", "developmentExecutable")
const resources = join(__dirname, "src", "wasmJsMain", "resources")
const contentTypes = {
    ".css": "text/css",
    ".js": "text/javascript",
    ".mjs": "text/javascript",
    ".svg": "image/svg+xml",
    ".wasm": "application/wasm",
}

createServer((request, response) => {
    const name = request.url === "/" ? "index.html" : decodeURIComponent(request.url.split("?")[0].slice(1))
    if (normalize(name).startsWith("..")) {
        response.writeHead(400).end()
        return
    }
    const generatedFile = join(output, name)
    const resourceFile = join(resources, name)
    const file = existsSync(generatedFile) ? generatedFile : resourceFile
    if (!existsSync(file)) {
        response.writeHead(404).end()
        return
    }
    response.writeHead(200, { "content-type": contentTypes[extname(file)] || "text/html" })
    if (name === "index.html") {
        response.end(readFileSync(file, "utf8").replace(/<meta http-equiv="Content-Security-Policy"[^>]*>\r?\n/, ""))
        return
    }
    createReadStream(file).pipe(response)
}).listen(8080, "127.0.0.1", () => {
    console.log("Nook development server: http://127.0.0.1:8080")
})
