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
