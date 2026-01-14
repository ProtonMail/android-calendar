package me.proton.android.calendar.data.db

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.crypto.common.keystore.EncryptedByteArray
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.PlainByteArray
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SearchEventEntity::class
    ],
    version = SearchDatabase.version,
    exportSchema = true
)
abstract class SearchDatabase : RoomDatabase() {

    abstract fun searchDao(): SearchDao

    companion object {

        const val TABLE_SEARCH_EVENTS = "search_events"

        const val name = "proton.calendar.search.db"
        const val version = 1

        fun buildDatabase(context: Context, valueStoreProvider: ValueStoreProvider, keyStoreCrypto: KeyStoreCrypto): SearchDatabase {

            val globalValueStore = valueStoreProvider.provideValueStore(ValueStoreProvider.GLOBAL_VALUE_STORE_USER_ID)
            val persistedPassphraseBase64 = globalValueStore.getString(ValueKey.SEARCH_DATABASE_PASSPHRASE_BASE_64)

            val persistedPassphrase = persistedPassphraseBase64?.let {
                kotlin.runCatching { keyStoreCrypto.decrypt(EncryptedByteArray(Base64.decode(persistedPassphraseBase64, Base64.DEFAULT))) }.getOrNull()?.array
            }

            val passphrase = if (persistedPassphrase == null) {
                val generatedPassphrase = Random.randBytes(64)
                val encryptedPassphrase = kotlin.run { keyStoreCrypto.encrypt(PlainByteArray(generatedPassphrase)) }.array

                globalValueStore.putString(ValueKey.SEARCH_DATABASE_PASSPHRASE_BASE_64, Base64.encodeToString(encryptedPassphrase, Base64.DEFAULT))
                generatedPassphrase
            } else persistedPassphrase

            return Room.databaseBuilder(context, SearchDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

    }
}

