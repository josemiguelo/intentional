package dev.josemiguel.intentional.core.sync

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The encryption between a device and Firebase: everything personal (which
 * apps and sites, for how long; the groups) goes up as AES-256-GCM under the
 * shared key, so Firebase holds blobs it can't read. Groups are named on the
 * server only by [groupId], a keyed hash, so the limits the server enforces
 * don't reveal what they're limits of.
 */
@OptIn(ExperimentalEncodingApi::class)
class Vault(key: String) {

    private val raw = Base64.decode(key).also { require(it.size == 32) { "the sync key must be 32 bytes" } }
    private val provider = CryptographyProvider.Default

    private val cipher by lazy {
        provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, raw).cipher()
    }

    /** A separate key for ids: the HMAC of a fixed label under the shared key. */
    private val idKey by lazy {
        val hmac = provider.get(HMAC)
        val master = hmac.keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, raw)
        val derived = master.signatureGenerator().generateSignatureBlocking("intentional group id".encodeToByteArray())
        hmac.keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, derived)
    }

    /** base64(nonce + ciphertext + tag). */
    fun seal(plain: String): String = Base64.encode(cipher.encryptBlocking(plain.encodeToByteArray()))

    fun open(sealed: String): String = cipher.decryptBlocking(Base64.decode(sealed)).decodeToString()

    /** A group's id on the server: stable for its name, meaningless without the key. */
    fun groupId(name: String): String =
        idKey.signatureGenerator().generateSignatureBlocking(name.encodeToByteArray())
            .take(12).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    companion object {
        /** A fresh key for a new sync setup, from the platform's secure random. */
        fun newKey(): String = Base64.encode(CryptographyRandom.Default.nextBytes(32))
    }
}
