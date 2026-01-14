package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import me.proton.android.calendar.common.SESSION_KEY_ALGO
import me.proton.android.calendar.domain.model.EncryptedPackage
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.key.domain.encryptSessionKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.mailmessage.domain.entity.Email
import javax.inject.Inject

class GenerateEmailPackageUseCase @Inject constructor(
    private val cryptoContext: CryptoContext
) {
    operator fun invoke(
        signedEncryptedBodyMime: Pair<ByteArray, ByteArray>?,
        recipientEmail: Email,
        sendPreferences: SendPreferences,
        decryptedAttachmentSessionKeys: MutableList<SessionKey>,
        decryptedBodySessionKey: SessionKey,
        encryptedBodyDataPacket: ByteArray,
        decryptedMimeBodySessionKey: SessionKey,
        encryptedMimeBodyDataPacket: ByteArray
    ): EncryptedPackage? {

        return if (sendPreferences.encrypt) {

            if (sendPreferences.pgpScheme == PackageType.ProtonMail) { // Internal Proton

                if (sendPreferences.publicKey == null) return null

                // TODO create a factory for PublicKey?
                val publicKey = PublicKey(sendPreferences.publicKey, true, true, true, true)
                val recipientBodyKeyPacket = publicKey.encryptSessionKey(cryptoContext, decryptedBodySessionKey)

                val encryptedAttachmentKeyPackets = decryptedAttachmentSessionKeys.map {
                    Base64.encode(publicKey.encryptSessionKey(cryptoContext, it))
                }

                EncryptedPackage(
                    addresses = mapOf(
                        recipientEmail to EncryptedPackage.Address.Internal(
                            bodyKeyPacket = Base64.encode(recipientBodyKeyPacket),
                            attachmentKeyPackets = encryptedAttachmentKeyPackets
                        )
                    ),
                    mimeType = "text/plain",
                    body = Base64.encode(encryptedBodyDataPacket),
                    type = PackageType.ProtonMail.type
                )

            } else { // PgpMime

                if (signedEncryptedBodyMime == null) return null

                EncryptedPackage(
                    addresses = mapOf(
                        recipientEmail to EncryptedPackage.Address.ExternalEncrypted(
                            bodyKeyPacket = Base64.encode(signedEncryptedBodyMime.first)
                        )
                    ),
                    mimeType = "multipart/mixed",
                    body = Base64.encode(signedEncryptedBodyMime.second),
                    type = PackageType.PgpMime.type
                )

            }

        } else {

            if (sendPreferences.sign) { // ClearMime

                EncryptedPackage(
                    addresses = mapOf(recipientEmail to EncryptedPackage.Address.ExternalSigned),
                    mimeType = "multipart/mixed",
                    body = Base64.encode(encryptedMimeBodyDataPacket),
                    type = PackageType.ClearMime.type,
                    bodyKey = EncryptedPackage.Key(Base64.encode(decryptedMimeBodySessionKey.key), SESSION_KEY_ALGO)
                )

            } else { // Cleartext

                val packageAttachmentKeys = decryptedAttachmentSessionKeys.map {
                    EncryptedPackage.Key(Base64.encode(it.key), SESSION_KEY_ALGO)
                }

                EncryptedPackage(
                    addresses = mapOf(recipientEmail to EncryptedPackage.Address.ExternalPlaintext),
                    mimeType = "text/plain",
                    body = Base64.encode(encryptedBodyDataPacket),
                    type = PackageType.Cleartext.type,
                    attachmentKeys = packageAttachmentKeys,
                    bodyKey = EncryptedPackage.Key(Base64.encode(decryptedBodySessionKey.key), SESSION_KEY_ALGO)
                )

            }

        }

    }
}
