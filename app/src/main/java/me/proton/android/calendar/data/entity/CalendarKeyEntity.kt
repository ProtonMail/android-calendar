package me.proton.android.calendar.data.entity

import androidx.room.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

// = calendar key
// The calendar key can be decrypted using the token.
@Entity(tableName = AppDatabase.TABLE_CALENDAR_KEYS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class CalendarKeyEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Flags")
    val flags: Int, // Key flags values: - 0: Inactive - 1: Active - 3: Active and primary
    @SerialName("PrivateKey")
    val privateKey: String, // encrypted with a passphrase
    @SerialName("PassphraseID")
    val passphraseId: String,
    @SerialName("CalendarID")
    val calendarId: String
) {
    val isActiveAndPrimary: Boolean get() = flags == 3
    val isActive: Boolean get() = flags and 1 > 0
}
