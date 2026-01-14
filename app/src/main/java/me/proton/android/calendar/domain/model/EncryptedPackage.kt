package me.proton.android.calendar.domain.model

import me.proton.core.mailmessage.data.api.request.EmailPackage
import me.proton.core.util.kotlin.toInt

data class EncryptedPackage(
    val addresses: Map<String, Address>,
    val mimeType: String,
    val body: String,
    val type: Int,
    val attachmentKeys: List<Key>? = null,
    val bodyKey: Key? = null,
) {

    sealed class Address(val packageType: PackageType, val signed: Boolean) {

        data class Internal(
            val bodyKeyPacket: String,
            val attachmentKeyPackets: List<String>
        ) : Address(PackageType.ProtonMail, true)

        data class ExternalEncrypted(
            val bodyKeyPacket: String,
        ) : Address(PackageType.PgpMime, true)

        object ExternalSigned : Address(PackageType.ClearMime, true)

        object ExternalPlaintext : Address(PackageType.Cleartext, false)
    }

    data class Key(
        val key: String,
        val algorithm: String,
    )

}

fun EncryptedPackage.toEmailPackage(): EmailPackage = EmailPackage(
    addresses = addresses.mapValues { entry ->
        when (val address = entry.value) {
            is EncryptedPackage.Address.ExternalEncrypted -> EmailPackage.Address(
                type = address.packageType.type,
                signature = address.signed.toInt(),
                bodyKeyPacket = address.bodyKeyPacket,
            )
            EncryptedPackage.Address.ExternalPlaintext -> EmailPackage.Address(
                type = address.packageType.type,
                signature = address.signed.toInt()
            )
            EncryptedPackage.Address.ExternalSigned -> EmailPackage.Address(
                type = address.packageType.type,
                signature = address.signed.toInt()
            )
            is EncryptedPackage.Address.Internal -> EmailPackage.Address(
                type = address.packageType.type,
                signature = address.signed.toInt(),
                bodyKeyPacket = address.bodyKeyPacket,
                attachmentKeyPackets = address.attachmentKeyPackets
            )

        }
    },
    mimeType = mimeType,
    body = body,
    type = type,
    attachmentKeys = attachmentKeys?.map { EmailPackage.Key(it.key, it.algorithm) },
    bodyKey = bodyKey?.let { EmailPackage.Key(it.key, it.algorithm) }
)
