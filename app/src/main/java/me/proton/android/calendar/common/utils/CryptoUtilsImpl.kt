package me.proton.android.calendar.common.utils

import com.proton.gopenpgp.crypto.Crypto
import ezvcard.VCard
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.utils.CryptoUtils
import me.proton.android.calendar.domain.utils.CryptoUtils.*
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.Armored
import me.proton.core.crypto.common.pgp.getFingerprintOrNull
import me.proton.core.crypto.common.pgp.split
import me.proton.core.key.domain.encryptData
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicAddressKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.entity.key.Recipient
import me.proton.core.key.domain.signData
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.entity.UserAddress

object CryptoUtilsImpl : CryptoUtils {

    override fun UserAddress.isValidForEncryption(cryptoContext: CryptoContext, logger: Logger): Boolean {

        return this.useKeys(cryptoContext) {

            val testData = "Test".encodeToByteArray()

            val encryptException = kotlin.runCatching { encryptData(testData).split(cryptoContext.pgpCrypto) }.exceptionOrNull()

            encryptException?.let {
                logger.e("can't encrypt data in isValidForEncryption", it)
            }

            val signException = kotlin.runCatching { signData(testData) }.exceptionOrNull()

            signException?.let {
                logger.e("can't sign data in isValidForEncryption", it)
            }

            encryptException == null && signException == null

        }

    }

    override fun extractPinnedKeys(
        purpose: PinnedKeysPurpose,
        vCardEmail: String,
        vCard: VCard,
        publicAddress: PublicAddress,
        cryptoContext: CryptoContext
    ): PinnedKeysOrError {

        val isInternal = publicAddress.recipient == Recipient.Internal
        val publicAddressKey = publicAddress.keys.firstOrNull { it.publicKey.isPrimary }

        val propertyGroup = vCard.getGroupForEmail(vCardEmail) ?: return PinnedKeysOrError.Error.NoEmailInVCard

        val vCardPublicKeys = vCard.getKeysForGroup(propertyGroup)

        val pinnedKeysOrErrors = vCardPublicKeys.map { pinnedPublicKey ->
            extractPinnedKey(cryptoContext, pinnedPublicKey, publicAddress, isInternal, purpose, publicAddressKey) ?: PinnedKeysOrError.Success(listOf(PublicKey(pinnedPublicKey, true, true, true, true)))
        }

        if (pinnedKeysOrErrors.isEmpty()) return PinnedKeysOrError.Error.NoKeysAvailable

        if (pinnedKeysOrErrors.none { it is PinnedKeysOrError.Success }) return pinnedKeysOrErrors.first()

        val pinnedKeys = when (purpose) {
            PinnedKeysPurpose.Encrypting -> pinnedKeysOrErrors.first { it is PinnedKeysOrError.Success }
            PinnedKeysPurpose.VerifyingSignature -> PinnedKeysOrError.Success(pinnedKeysOrErrors.mapNotNull { it as? PinnedKeysOrError.Success }.flatMap { it.pinnedPublicKeys })
        }

        return pinnedKeys
    }

    private fun extractPinnedKey(
        cryptoContext: CryptoContext,
        pinnedPublicKey: String,
        publicAddress: PublicAddress,
        isInternal: Boolean,
        purpose: PinnedKeysPurpose,
        publicAddressKey: PublicAddressKey?
    ): PinnedKeysOrError.Error? {

        val pinnedKeyFingerprint = cryptoContext.pgpCrypto.getFingerprintOrNull(pinnedPublicKey)
            ?: return PinnedKeysOrError.Error.TrustedKeysInvalid

        val matchingPublicAddressKey =
            publicAddress.keys.find { cryptoContext.pgpCrypto.getFingerprintOrNull(it.publicKey.key) == pinnedKeyFingerprint }

        // pinned key is not in the public key repository
        if (isInternal && matchingPublicAddressKey == null) return PinnedKeysOrError.Error.TrustedKeysInvalid

        // pinned key is compromised
        if (matchingPublicAddressKey?.isCompromised() == true) return PinnedKeysOrError.Error.TrustedKeysInvalid

        // pinned key is obsolete (invalid for encrypting but we can still verify)
        if (matchingPublicAddressKey?.isObsolete() == true && purpose == PinnedKeysPurpose.Encrypting) return PinnedKeysOrError.Error.TrustedKeysInvalid

        // pinned key is expired
        if (isKeyExpired(pinnedPublicKey) == true) return PinnedKeysOrError.Error.TrustedKeysInvalid

        // pinned key is revoked
        if (isKeyRevoked(pinnedPublicKey) == true) return PinnedKeysOrError.Error.TrustedKeysInvalid

        if (publicAddressKey != null && (publicAddressKey.isObsolete() || publicAddressKey.isCompromised())) return PinnedKeysOrError.Error.PublicKeysInvalid

        return null
    }

    /**
     * If true, do not use the key for encrypting, nor for signature verification.
     */
    private fun PublicAddressKey.isCompromised() = !(this.flags and 1 == 1)

    /**
     * If true, do not use the key to encrypt new messages, but can verify signatures.
     */
    private fun PublicAddressKey.isObsolete() = !(this.flags and 2 == 2)

    private fun isKeyExpired(armoredKey: Armored): Boolean? {
        return kotlin.runCatching { Crypto.newKeyFromArmored(armoredKey).isExpired }.getOrNull()
    }

    private fun isKeyRevoked(armoredKey: Armored): Boolean? {
        return kotlin.runCatching { Crypto.newKeyFromArmored(armoredKey).isRevoked }.getOrNull()
    }

}

