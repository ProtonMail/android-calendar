package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import java.time.Duration

// = calendar subscription
@Entity(tableName = AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class CalendarSubscriptionEntity(
    @PrimaryKey
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("CreateTime")
    val createTime: Int,
    @SerialName("LastUpdateTime")
    val lastUpdateTime: Int, // Unix timestamp of the subscription last update time
    @SerialName("Status")
    val status: Int, // 0: OK, 1: Error, more to come
    @SerialName("URL")
    val url: String
) {
    // Calendar is in sync if status is 0 AND has been updated in the last 12 hours
    val isSynced: Boolean get() = status == CalendarSubscriptionStatus.OK.value &&
            ((System.currentTimeMillis() / 1000) - lastUpdateTime < Duration.ofHours(12).seconds)

    val isSyncing: Boolean get() = status == CalendarSubscriptionStatus.SYNCING.value

    val isLastSyncOld: Boolean get() = lastUpdateTime != 0 &&
            ((System.currentTimeMillis() / 1000) - lastUpdateTime >= Duration.ofHours(12).seconds)
}

enum class CalendarSubscriptionStatus(val value: Int) {
    /** OK */
    OK(0),
    /** Synchronizing */
    SYNCING(7),
    /** Generic Error */
    ERROR(1),
    /** Invalid ICS */
    INVALID_ICS(2),
    /** Calendar soft-deleted */
    CALENDAR_SOFT_DELETED(3),
    /** Calendar not found */
    NOT_FOUND(4),
    /** User does not exist */
    USER_DOES_NOT_EXIST(5),
    /** Remote calendar size exceeded the limit */
    SIZE_EXCEED_LIMIT(6),
    /** Calendar missing primary key */
    MISSING_PRIMARY_KEY(8),
    /** Remote calendar fetching generic error */
    HTTP_REQUEST_FAILED_GENERIC_ERROR(20),
    /** Remote calendar fetching bad request error */
    HTTP_REQUEST_FAILED_BAD_REQUEST(21),
    /** Remote calendar fetching unauthorized error */
    HTTP_REQUEST_FAILED_UNAUTHORIZED(22),
    /** Remote calendar fetching forbidden error */
    HTTP_REQUEST_FAILED_FORBIDDEN(23),
    /** Remote calendar fetching not found error */
    HTTP_REQUEST_FAILED_NOT_FOUND(24),
    /** Remote calendar fetching internal server error */
    HTTP_REQUEST_FAILED_INTERNAL_SERVER_ERROR(25),
    /** Remote calendar provider timeout */
    HTTP_REQUEST_FAILED_TEST(26),
    /** Internal Proton-Proton link not found */
    P2P_LINK_NOT_FOUND(27),
    /** Internal Proton-Proton remote calendar is not decryptable */
    UNABLE_TO_DECRYPT(28)
}
