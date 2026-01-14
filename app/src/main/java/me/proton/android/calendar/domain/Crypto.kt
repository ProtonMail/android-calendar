package me.proton.android.calendar.domain

import com.proton.gopenpgp.crypto.PGPSplitMessage
import com.proton.gopenpgp.crypto.SessionKey

interface Crypto {

    /**
     * Generates BCrypted passphrase using provided Base64-encoded salt.
     */
    fun generateUserPassphrase(passphrase: ByteArray, encodedSalt: String): ByteArray

    /**
     * Checks if this key can be unlocked by this passphrase.
     */
    fun checkPassphrase(armoredKey: String, passphrase: ByteArray): Boolean

    /**
     * Decrypts text using private key.
     */
    fun decryptText(cipherText: String, armoredPrivateKeys: List<String>, passphrase: ByteArray): String?

    /**
     * Encrypts plaintext with armored PublicKey and returns Armored PGPMessage as String. This message contains KeyPacket and DataPacket.
     */
    fun encryptText(plainText: String, armoredPublicKey: String): String?

    /**
     * Encrypts plaintext with SessionKey and returns Armored PGPMessage as String. This message contains DataPacket but no KeyPacket.
     */
    fun encryptText(plainText: String, sessionKey: SessionKey): String?

    /**
     * Encrypts plaintext with array of public keys and return PgpSplitMessage
     */
    fun encryptTextWithSessionKey(plainText: String, publicKeys: List<String>): Pair<String, List<String?>>

    /**
     * Encrypts session key with the given public key and returns Base64 encoded key packet
     */
    fun getKeyPacket(sessionKey: SessionKey, publicKey: String): String?

    /**
     * Extracts public key from supplied key (private or public).
     */
    fun getArmoredPublicKey(armoredKey: String): String?

    /**
     * Decrypts Base64-encoded KeyPacket.
     */
    fun decryptSessionKey(
        encodedKeyPacket: String,
        armoredPrivateKeys: List<String>,
        passphrase: ByteArray
    ): SessionKey?

    /**
     * Generate new X25519 key, returned as locked, armored String.
     */
    fun generateEccKey(name: String, email: String, passphrase: ByteArray): String?

}
