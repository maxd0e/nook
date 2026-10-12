const databaseName = 'nook-encrypted-events'
const storeName = 'events'
let databasePromise
let queue = Promise.resolve()

function database() {
    if (databasePromise) return databasePromise
    databasePromise = new Promise((resolve, reject) => {
        const request = indexedDB.open(databaseName, 1)
        request.onupgradeneeded = () => request.result.createObjectStore(storeName, { keyPath: 'id' })
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
    })
    return databasePromise
}

function transaction(mode, action) {
    return database().then(db => new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, mode)
        action(tx.objectStore(storeName), resolve, reject)
        tx.onerror = () => reject(tx.error)
        tx.onabort = () => reject(tx.error)
    }))
}

function enqueue(action) {
    queue = queue.then(action, action)
    return queue.then(() => '')
}

export function readAll() {
    return queue.then(() => transaction('readonly', (store, resolve, reject) => {
        const request = store.getAll()
        request.onsuccess = () => resolve(JSON.stringify(request.result))
        request.onerror = () => reject(request.error)
    }))
}

export function put(value) {
    return enqueue(() => transaction('readwrite', (store, resolve, reject) => {
        const request = store.put(JSON.parse(value))
        request.onsuccess = () => resolve()
        request.onerror = () => reject(request.error)
    }))
}

export function markClean(eventId) {
    return enqueue(() => transaction('readwrite', (store, resolve, reject) => {
        const request = store.get(eventId)
        request.onsuccess = () => {
            if (request.result) store.put({ ...request.result, dirty: false })
            resolve()
        }
        request.onerror = () => reject(request.error)
    }))
}

export function clear() {
    return enqueue(() => transaction('readwrite', (store, resolve, reject) => {
        const request = store.clear()
        request.onsuccess = () => resolve()
        request.onerror = () => reject(request.error)
    }))
}
