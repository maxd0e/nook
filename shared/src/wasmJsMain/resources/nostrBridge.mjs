import { finalizeEvent, generateSecretKey, getPublicKey, verifyEvent as verifyNostrEvent } from 'nostr-tools/pure'
import { decode, npubEncode, nsecEncode } from 'nostr-tools/nip19'
import { decrypt, encrypt, getConversationKey } from 'nostr-tools/nip44'

function normalized(value, type) {
    if (typeof value !== 'string' || (/[a-z]/.test(value) && /[A-Z]/.test(value))) throw new Error('Invalid Bech32 value')
    const decoded = decode(value.toLowerCase())
    if (decoded.type !== type) throw new Error('Unexpected Bech32 prefix')
    return decoded
}

function secretFromNsec(nsec) {
    const secret = normalized(nsec, 'nsec').data
    if (!(secret instanceof Uint8Array) || secret.length !== 32) throw new Error('Invalid private key')
    return secret
}

function eventForTools(event) {
    return { pubkey: event.publicKey, created_at: event.createdAt, kind: event.kind, tags: event.tags, content: event.content, id: event.id, sig: event.signature }
}

function signedForNook(event) {
    return { id: event.id, publicKey: event.pubkey, createdAt: event.created_at, kind: event.kind, tags: event.tags, content: event.content, signature: event.sig }
}

export function generateNsec() { return nsecEncode(generateSecretKey()) }
export function normalizeNsec(nsec) { normalized(nsec, 'nsec'); return nsec.toLowerCase() }
export function publicKeyFromNsec(nsec) { return getPublicKey(secretFromNsec(nsec)) }
export function npubFromNsec(nsec) { return npubEncode(publicKeyFromNsec(nsec)) }
export function decodeNpub(npub) {
    const value = normalized(npub, 'npub').data
    if (typeof value !== 'string' || !/^[0-9a-f]{64}$/i.test(value)) throw new Error('Invalid public key')
    return value.toLowerCase()
}
export function encryptToSelf(plaintext, nsec) {
    const secret = secretFromNsec(nsec)
    return encrypt(plaintext, getConversationKey(secret, publicKeyFromNsec(nsec)))
}
export function decryptFromSelf(ciphertext, nsec) {
    const secret = secretFromNsec(nsec)
    return decrypt(ciphertext, getConversationKey(secret, publicKeyFromNsec(nsec)))
}
export function signEvent(eventJson, nsec) {
    const event = JSON.parse(eventJson)
    return JSON.stringify(signedForNook(finalizeEvent({ created_at: event.createdAt, kind: event.kind, tags: event.tags, content: event.content }, secretFromNsec(nsec))))
}
export function verifyEvent(eventJson) { return verifyNostrEvent(eventForTools(JSON.parse(eventJson))) }
