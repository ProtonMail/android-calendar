package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.decryptTextOrNull
import me.proton.core.key.domain.unlockOrNull
import me.proton.core.key.domain.useKeys
import javax.inject.Inject

/**
 * We can decrypt and cache CalendarPassphrase locally, but we need to update it whenever
 * we get Server Event with Passphrase payload.
 */
class CacheCalendarPassphraseUseCase @Inject constructor( // TODO TEST
    private val database: AppDatabase,
    private val json: Json,
    private val cryptoContext: CryptoContext,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {

        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        val member = calendarsRepository.selectCalendarUserMember(calendarId) ?: return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: member was null")
        val calendarPassphrase = calendarsRepository.selectCalendarPassphrases(calendarId).map { it.toPassphrase(json) }.first { it.isActive }
        // Passphrase is linked to Calendar and is used by all CalendarKeys of that Calendar

        // TODO change to multiple members
        // TODO also something to keep in mind is that you can have multiple members for the user in the same calendar
        // you can join a calendar using Address1 and Address2
        // you can have more than one member

        val memberPassphrase = calendarPassphrase.memberPassphrases.find { it.memberId == member.id }
            ?: return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: there is no user address")

        val memberAddress = calendarsRepository.getAddressForMember(userId, member.addressId, member.id, member.canonicalEmail) ?: return UseCase.Result.Error("CacheCalendarPassphraseUseCase: No valid Member Address found")

        // decrypt CalendarPassphrase -- actually a Passphrase for CalendarKey
        // AddressKey used to d/encrypt Passphrase for this Member might not be the primary AddressKey
        val plaintextPassphrase = memberAddress.useKeys(cryptoContext) {

            val publicKeysForVerification = this.publicKeyRing.keys.filter { it.isActive }.map { it.key }

            this.privateKeyRing.keys.firstNotNullOfOrNull { privateKey ->
                val decryptedPassphrase = privateKey.unlockOrNull(cryptoContext)?.decryptTextOrNull(cryptoContext, memberPassphrase.passphrase)

                if (decryptedPassphrase != null) {
                    val isSignatureValid = publicKeysForVerification.any {
                        cryptoContext.pgpCrypto.verifyText(
                            decryptedPassphrase,
                            memberPassphrase.signature,
                            it
                        )
                    }

                    if (isSignatureValid) decryptedPassphrase else null
                } else null
            }
        }

        if (plaintextPassphrase.isNullOrBlank()) {
            return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: could not decrypt passhprase")
        }

        // we cache decrypted CalendarPassphrase under CalendarPassphraseId, but actually this is
        //  Passphrase for CalendarKey, not Calendar
        valueStore.putStringInSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id, plaintextPassphrase)
        return UseCase.Result.Success<Unit>()
    }

}
