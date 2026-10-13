const databaseName = 'nook-encrypted-events'
const storeName = 'events'
let databasePromise
let queue = Promise.resolve()

function database() {
    if (databasePromise) return databasePromise
    databasePromise = new Promise((resolve, reject) => {
        const request = indexedDB.open(databaseName, 2)
        request.onupgradeneeded = () => {
            const store = request.result.objectStoreNames.contains(storeName)
                ? request.transaction.objectStore(storeName)
                : request.result.createObjectStore(storeName, { keyPath: 'id' })
            if (!store.indexNames.contains('publicKey')) store.createIndex('publicKey', 'publicKey')
        }
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
    })
    return databasePromise
}

function transaction(mode, action) {
    return database().then(db => new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, mode)
        let result
        action(tx.objectStore(storeName), value => { result = value }, reject)
        tx.oncomplete = () => resolve(result)
        tx.onerror = () => reject(tx.error)
        tx.onabort = () => reject(tx.error)
    }))
}

function enqueue(action) {
    queue = queue.then(action, action)
    return queue.then(() => '')
}

export function readAll(publicKey) {
    return queue.then(() => transaction('readonly', (store, resolve, reject) => {
        const request = store.index('publicKey').getAll(publicKey)
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

export function clear(publicKey) {
    return enqueue(() => transaction('readwrite', (store, resolve, reject) => {
        const request = store.index('publicKey').openKeyCursor(IDBKeyRange.only(publicKey))
        request.onsuccess = () => {
            const cursor = request.result
            if (cursor) {
                store.delete(cursor.primaryKey)
                cursor.continue()
            } else {
                resolve()
            }
        }
        request.onerror = () => reject(request.error)
    }))
}
