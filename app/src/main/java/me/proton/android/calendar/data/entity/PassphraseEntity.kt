package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.MemberPassphrase
import me.proton.android.calendar.domain.model.Passphrase

// this decrypts Calendar Key, it is a "Calendar Passphrase"
@Entity(tableName = AppDatabase.TABLE_PASSPHRASES,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class PassphraseEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Flags")
    val flags: Int, // 0: Inactive, 1: Active
    @SerialName("MemberPassphrases")
    val memberPassphrases: List<JsonElement>,
    @SerialName("CalendarID")
    val calendarId: String
) {

    fun toPassphrase(json: Json): Passphrase {
        return Passphrase(
            id = this.id,
            flags = this.flags,
            memberPassphrases = this.memberPassphrases.map {
                json.decodeFromJsonElement<MemberPassphrase>(it)
            },
            calendarId = this.calendarId
        )
    }

}
