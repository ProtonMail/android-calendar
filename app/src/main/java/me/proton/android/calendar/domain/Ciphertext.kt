package me.proton.android.calendar.domain

import com.proton.gopenpgp.armor.Armor
import com.proton.gopenpgp.crypto.PGPSplitMessage

data class Ciphertext(
    private val keyPacket: ByteArray?, // TODO multiple?
    private val dataPacket: ByteArray // TODO multiple?
) {

    val encodedKeyPacket: String? = if (keyPacket != null) com.google.crypto.tink.subtle.Base64.encode(keyPacket) else null
    val encodedDataPacket: String = com.google.crypto.tink.subtle.Base64.encode(dataPacket)

    fun asArmoredPGPMessage(): String {
        return Armor.armorWithType(if (keyPacket == null) dataPacket else keyPacket + dataPacket, com.proton.gopenpgp.constants.Constants.PGPMessageHeader)
    }

    companion object {

        /**
         * PGPMessage has to contain both KeyPacket(s) and DataPacket(s).
         */
        fun from(armoredPgpMessage: String): Ciphertext { // TODO catch errors and return null???
            val pgpSplitMessage = PGPSplitMessage(armoredPgpMessage)
            return Ciphertext(pgpSplitMessage.keyPacket, pgpSplitMessage.dataPacket)
        }

        fun from(encodedKeyPacket: String?, encodedDataPacket: String): Ciphertext {
            return Ciphertext(if (encodedKeyPacket != null) com.google.crypto.tink.subtle.Base64.decode(encodedKeyPacket) else null, com.google.crypto.tink.subtle.Base64.decode(encodedDataPacket))
        }

    }
}
