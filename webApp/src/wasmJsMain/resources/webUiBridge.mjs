const themeKey = 'nook-theme'

export function loadTheme() {
    return localStorage.getItem(themeKey) === 'dark' ? 'dark' : 'light'
}

export function saveTheme(theme) {
    localStorage.setItem(themeKey, theme === 'dark' ? 'dark' : 'light')
}

export function copyText(value) {
    return navigator.clipboard.writeText(value).then(() => '')
}

export function randomId() {
    return crypto.randomUUID()
}

export function now() {
    return Date.now()
}

export function compatibilityError() {
    if (!globalThis.isSecureContext) {
        return 'Nook needs a secure HTTPS connection to protect encrypted browser storage.'
    }
    if (!globalThis.WebAssembly) {
        return 'This browser does not support WebAssembly, which Nook needs to run.'
    }
    if (!globalThis.indexedDB) {
        return 'This browser does not support IndexedDB, which Nook needs for encrypted offline notes.'
    }
    if (!globalThis.crypto?.subtle || !globalThis.crypto?.randomUUID) {
        return 'This browser does not provide the secure cryptography APIs Nook needs.'
    }
    return ''
}
