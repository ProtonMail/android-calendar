package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.PassphraseEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PassphrasesDao : BaseDao<PassphraseEntity> {

    @Query("SELECT * FROM passphrases WHERE calendarId = :calendarId")
    abstract suspend fun select(calendarId: String): List<PassphraseEntity>

    @Query("DELETE FROM passphrases WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM passphrases WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)

}
