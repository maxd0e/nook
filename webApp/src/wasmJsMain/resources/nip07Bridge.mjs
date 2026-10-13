function nostrOrThrow() {
    if (!window.nostr) throw new Error('No Nostr browser extension was found.')
    return window.nostr
}

function supports(path) {
    let value = window.nostr
    for (const key of path) value = value?.[key]
    return typeof value === 'function'
}

export function capabilityError() {
    if (!window.nostr) return 'No Nostr browser extension was found. Install or enable one to continue.'
    if (!supports(['getPublicKey']) || !supports(['signEvent'])) {
        return 'Your Nostr extension does not support signing events.'
    }
    if (!supports(['nip44', 'encrypt']) || !supports(['nip44', 'decrypt'])) {
        return 'Your Nostr extension does not support NIP-44 encryption required by Nook.'
    }
    return ''
}

export function getPublicKey() {
    const error = capabilityError()
    if (error) return Promise.reject(new Error(error))
    return nostrOrThrow().getPublicKey()
}

export function signEvent(eventJson) {
    const error = capabilityError()
    if (error) return Promise.reject(new Error(error))
    const event = JSON.parse(eventJson)
    return nostrOrThrow().signEvent({
        created_at: event.createdAt,
        kind: event.kind,
        tags: event.tags,
        content: event.content,
    }).then(signed => JSON.stringify({
        id: signed.id,
        publicKey: signed.pubkey,
        createdAt: signed.created_at,
        kind: signed.kind,
        tags: signed.tags,
        content: signed.content,
        signature: signed.sig,
    }))
}

export function encrypt(publicKey, plaintext) {
    const error = capabilityError()
    if (error) return Promise.reject(new Error(error))
    return nostrOrThrow().nip44.encrypt(publicKey, plaintext)
}

export function decrypt(publicKey, ciphertext) {
    const error = capabilityError()
    if (error) return Promise.reject(new Error(error))
    return nostrOrThrow().nip44.decrypt(publicKey, ciphertext)
}
