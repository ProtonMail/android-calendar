package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.data.db.AppDatabase

/**
 * This is a Member of a Calendar.
 */
@Entity(tableName = AppDatabase.TABLE_MEMBERS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class MemberEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Permissions")
    val permissions: Int, // bitmap
    @SerialName("AddressID")
    val addressId: String?,
    @SerialName("Email")
    val email: String, // plaintext email address
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int, // 0: hide, 1: show
    @SerialName("Flags")
    val flags: Int,
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Priority")
    val priority: Int?
    ) {
    enum class Permission(val value: Int) { // TODO see if this is even deserialized
        /** has financial responsibility. There must always be exactly one owner but it can be transferred */
        SUPEROWNER(1),
        /** can edit permissions of all other users, and can acquire super-ownership */
        OWNER(2),
        /** can edit the permissions of all other users except for admins, can add or remove members to the calendar */
        ADMIN(4),
        /** can view who has access to the calendar */
        READ_MEMBER_LIST(8),
        /** can create new events, edit calendar-specific notes, edit events owned by the calendar, delete events, share events owned by that calendar */
        WRITE(16),
        /** can read event information */
        READ(32),
        /** can see when events are, but no further event information. Every user has availability access */
        AVAILABILITY(64)
    }

    fun hasPermission(permission: Permission): Boolean { // TODO add test
        return this.permissions.and(permission.value) >= 1
    }

    val hasIncompleteKeySetup: Boolean get() = flags and 8 == 8

    val canonicalEmail: String get() = canonicalizeProtonEmail(email, forceCanonicalization = true)

    enum class CalendarFlags(val value: Int) {
        /** 0 - Inactive: the calendar keys are not accessible and the current user cannot fix it */
        INACTIVE(0),
        /** 1 - Active: the calendar is all good! */
        ACTIVE(1),
        /** 2 - Update passphrase: a deactivated passphrase is again accessible, you should re-encrypt the linked calendar key using the primary passphrase */
        UPDATE_PASSPHRASE(2),
        /** 4 - Reset needed: the calendar needs to be reset */
        RESET_NEEDED(4),
        /** 8 - Incomplete setup: the calendar setup was not completed, need to setup the key and passphrase */
        INCOMPLETE_SETUP(8),
        /** 16 - Lost access: the user lost access to the calendar but an admin can re-invite him */
        LOST_ACCESS(16),
        /** 32 - Disabled calendar: the calendar is disabled for the current user only (all the addresses linked to the current user's members are disabled) */
        DISABLED(32),
        /** 64 - Super-owner disabled calendar: the calendar is disabled because the super-owner disabled his address. Possibility to transfer super-ownership to reactive the calendar. */
        SUPER_OWNER_DISABLED(64)
    }
}
