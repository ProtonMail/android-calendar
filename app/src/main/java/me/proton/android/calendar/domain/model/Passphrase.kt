package me.proton.android.calendar.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Passphrase(
    override val id: String,
    val flags: Int, // 0: Inactive, 1: Active
    val memberPassphrases: List<MemberPassphrase>,
    val calendarId: String
): BaseModel() {
    val isActive = flags == 1
}

@Serializable
data class MemberPassphrase(
    @SerialName("MemberID")
    val memberId: String, // TODO this is probably the same as User's AddressID
    @SerialName("Passphrase")
    val passphrase: String, // encrypted passphrase, can be decrypted using the primary AddressKey linked to the member’s address.
    @SerialName("Signature")
    val signature: String // signature of plaintext passphrase, needs to be validated TODO!
)


