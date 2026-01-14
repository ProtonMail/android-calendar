package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MembersDao : BaseDao<MemberEntity> {

    @Query("SELECT * FROM members")
    abstract fun flowMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members")
    suspend abstract fun selectMembers(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE calendarId = :calendarId")
    abstract suspend fun selectCalendarMembers(calendarId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :memberId")
    abstract suspend fun selectById(memberId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE email = :address")
    abstract suspend fun selectByAddress(address: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE calendarId IN (SELECT id FROM calendars WHERE fkUserId = :userId)")
    abstract suspend fun selectByUserId(userId: String): List<MemberEntity>

    @Query("DELETE FROM members WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("UPDATE members SET display = :display WHERE calendarId = :calendarId")
    abstract suspend fun updateDisplay(calendarId: String, display: Int)

    @Query("UPDATE members SET flags = :flags WHERE id = :memberId AND calendarId = :calendarId")
    abstract suspend fun updateFlags(memberId: String, calendarId: String, flags: Int)

    @Query("UPDATE members SET addressId = :addressId WHERE id = :memberId")
    abstract suspend fun updateMemberAddressId(memberId: String, addressId: String)
}
