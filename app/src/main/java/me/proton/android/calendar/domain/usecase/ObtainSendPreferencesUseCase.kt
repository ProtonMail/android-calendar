package me.proton.android.calendar.domain.usecase

import androidx.annotation.VisibleForTesting
import ezvcard.VCard
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.utils.CryptoUtilsImpl
import me.proton.android.calendar.common.utils.extractSignedVCard
import me.proton.android.calendar.common.utils.getGroupForEmail
import me.proton.android.calendar.common.utils.getProperty
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.MailSettingsApi
import me.proton.android.calendar.domain.model.MailSettings
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.domain.utils.CryptoUtils
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicAddressKey
import me.proton.core.key.domain.entity.key.Recipient
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.filterNullValues
import javax.inject.Inject

/**
 * Combines User's default MailSettings, Contact VCard data and Composer preferences
 * into [SendPreferences] used when sending emails.
 */
class ObtainSendPreferencesUseCase @Inject constructor(
    private val logger: Logger,
    private val contactEmailsRepository: ContactRepository,
    private val userManager: UserManager,
    private val mailSettingsApi: MailSettingsApi,
    private val cryptoContext: CryptoContext,
    private val getRecipientPublicAddresses: GetRecipientPublicAddresses
) : UseCase {

    sealed class Result {
        data class Success(val sendPreferences: SendPreferences) : Result()

        sealed class Error : Result() {
            object AddressDisabled : Error()
            object GettingContactPreferences : Error()
            object TrustedKeysInvalid : Error()
            object NoCorrectlySignedTrustedKeys : Error()
            object PublicKeysInvalid : Error()
            object NetworkError : Error()
        }
    }

    suspend fun execute(
        userId: UserId,
        canonicalEmails: Map<Email, Email>
    ): Map<Email, Result> {

        val user = userManager.getUserOrNull(userId, logger)

        if (user == null) {
            logger.i("ObtainSendPreferencesUseCase User is null")
        }

        // 1. get User's Mail Settings
        val mailSettings = mailSettingsApi.getMailSettings(userId).valueOrNullAndLogErrors(
            logger,
            "ObtainSendPreferencesUseCase, get Mail Settings"
        )?.mailSettings?.toMailSettings()

        // 2. get all User's contacts
        val contactEmails =
            kotlin.runCatching { contactEmailsRepository.getAllContactEmails(userId, refresh = true) }.getOrElse {
                logger.i("ObtainSendPreferencesUseCase error getting all contact emails", it)
                null
            }

        if (mailSettings == null || contactEmails == null || user == null) {
            return canonicalEmails.mapValues { Result.Error.NetworkError }
        }

        val result = HashMap<Email, Result>()

        // 3. get public addresses for recipients
        val publicAddresses = getRecipientPublicAddresses.invoke(userId, canonicalEmails.keys.toList())
        publicAddresses.forEach {
            if (it.value == null && canonicalEmails.keys.contains(it.key) && !result.containsKey(it.key)) result[it.key] = Result.Error.AddressDisabled
        }

        // 4. filter those contacts that have custom Send Preferences
        val contactEmailsWithCustomPreferences = canonicalEmails.mapValues { entry ->
            contactEmails.firstOrNull { it.canonicalEmail == entry.value && it.defaults == 0 }
        }.filterNullValues()

        // 5. fetch full Contact info for those contacts
        val fullContactsWithCustomPreferences = contactEmailsWithCustomPreferences.mapValues { entry ->
            kotlin.runCatching { contactEmailsRepository.getContactWithCards(userId, entry.value.contactId, refresh = true) }.getOrElse {
                logger.i("ObtainSendPreferencesUseCase error getting full contacts", it)
                null
            }
        }
        fullContactsWithCustomPreferences.forEach {
            if (it.value == null && !result.containsKey(it.key)) result[it.key] = Result.Error.NetworkError
        }

        // 6. obtain VCards for those contacts
        val validVCards = fullContactsWithCustomPreferences.filterNullValues().mapValues { entry ->
            entry.value.extractSignedVCard(user, cryptoContext, logger).also {
                if (it == null) result[entry.key] = Result.Error.NoCorrectlySignedTrustedKeys
            }
        }

        // 7. parse VCards to get custom Contact preferences
        // skip emails with errors and create SendPreferences for the rest
        canonicalEmails.filterNot { result.containsKey(it.key) }.forEach { entry ->

            val publicAddress = publicAddresses[entry.key]
            val vCard = validVCards[entry.key]
            val vCardEmail = contactEmailsWithCustomPreferences[entry.key]?.email

            val sendPreferencesOrError = if (vCardEmail != null && publicAddress != null && vCard != null) {
                createCustomSendPreferences(vCardEmail, publicAddress, vCard, mailSettings)
            } else {
                createDefaultSendPreferences(mailSettings, publicAddress)
            }

            result[entry.key] = when (sendPreferencesOrError) {
                is SendPreferencesOrError.Success -> Result.Success(sendPreferencesOrError.sendPreferences)
                SendPreferencesOrError.Error.TrustedKeysInvalid -> Result.Error.TrustedKeysInvalid
                else -> Result.Error.GettingContactPreferences
            }
        }

        return result
    }

    sealed class SendPreferencesOrError {
        data class Success(val sendPreferences: SendPreferences): SendPreferencesOrError()

        sealed class Error: SendPreferencesOrError() {
            object NoKeysAvailable: Error()
            object NoEmailInVCard: Error()
            object TrustedKeysInvalid: Error()
            object PublicKeysInvalid: Error()
        }
    }

    /**
     * @param vCardEmail it has to be contact email in the vCard, not necessarily canonical version (can be aliased)
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun createCustomSendPreferences(
        vCardEmail: String,
        publicAddress: PublicAddress,
        vCard: VCard,
        defaultMailSettings: MailSettings
    ): SendPreferencesOrError {

        val isInternal = publicAddress.recipient == Recipient.Internal
        val publicAddressKey = publicAddress.keys.firstOrNull { it.publicKey.isPrimary }

        val propertyGroup = vCard.getGroupForEmail(vCardEmail) ?: return SendPreferencesOrError.Error.NoEmailInVCard

        val vCardEncrypt = vCard.getProperty(propertyGroup, "x-pm-encrypt")
        val vCardSign = vCard.getProperty(propertyGroup, "x-pm-sign")
        val vCardMime = vCard.getProperty(propertyGroup, "x-pm-mimetype")
        val vCardScheme = vCard.getProperty(propertyGroup, "x-pm-scheme")

        val encrypt = if (vCardEncrypt != null) (vCardEncrypt.value?.equalsNoCase("true") == true) else false
        val sign = if (vCardSign != null) (vCardSign.value?.equalsNoCase("true") == true) else defaultMailSettings.sign
        val scheme = PackageType.fromScheme(vCardScheme?.value ?: "", encrypt, sign) ?: defaultMailSettings.pgpScheme
        val mime = if (vCardMime?.value != null) vCardMime.value else defaultMailSettings.draftMimeType

        val pinnedPublicKeys = when (val pinnedKeysOrError = CryptoUtilsImpl.extractPinnedKeys(CryptoUtils.PinnedKeysPurpose.Encrypting, vCardEmail, vCard, publicAddress, cryptoContext)) {
            is CryptoUtils.PinnedKeysOrError.Success -> pinnedKeysOrError.pinnedPublicKeys
            is CryptoUtils.PinnedKeysOrError.Error.NoKeysAvailable, CryptoUtils.PinnedKeysOrError.Error.NoEmailInVCard -> null // no pinned key found
            is CryptoUtils.PinnedKeysOrError.Error.PublicKeysInvalid -> return SendPreferencesOrError.Error.PublicKeysInvalid
            is CryptoUtils.PinnedKeysOrError.Error.TrustedKeysInvalid -> return SendPreferencesOrError.Error.TrustedKeysInvalid
            else -> return SendPreferencesOrError.Error.TrustedKeysInvalid
        }

        if (publicAddressKey != null && (publicAddressKey.isObsolete() || publicAddressKey.isCompromised())) return SendPreferencesOrError.Error.PublicKeysInvalid

        return if (isInternal) {

            if (pinnedPublicKeys.isNullOrEmpty() && publicAddressKey == null) {
                SendPreferencesOrError.Error.NoKeysAvailable
            } else {
                SendPreferencesOrError.Success(
                    SendPreferences(
                        encrypt = true,
                        sign = true,
                        pgpScheme = PackageType.ProtonMail,
                        mimeType = mime,
                        publicKey = pinnedPublicKeys?.firstOrNull()?.key ?: publicAddressKey?.publicKey?.key
                    )
                )
            }

        } else {

            if (encrypt && pinnedPublicKeys == null && publicAddressKey == null) {
                SendPreferencesOrError.Error.NoKeysAvailable
            } else {
                SendPreferencesOrError.Success(
                    SendPreferences(
                        encrypt = encrypt,
                        sign = if (encrypt) true else sign,
                        pgpScheme = scheme,
                        mimeType = mime,
                        publicKey = pinnedPublicKeys?.firstOrNull()?.key ?: publicAddressKey?.publicKey?.key
                    )
                )
            }

        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun createDefaultSendPreferences(
        defaultMailSettings: MailSettings,
        publicAddress: PublicAddress?
    ): SendPreferencesOrError {

        val isInternal = publicAddress?.recipient == Recipient.Internal
        val publicAddressKey = publicAddress?.keys?.firstOrNull { it.publicKey.isPrimary }

        return if (isInternal) {

            if (publicAddressKey != null) {

                if (publicAddressKey.isObsolete() || publicAddressKey.isCompromised()) return SendPreferencesOrError.Error.PublicKeysInvalid

                SendPreferencesOrError.Success(
                    SendPreferences(
                        encrypt = true,
                        sign = true,
                        pgpScheme = PackageType.ProtonMail,
                        mimeType = defaultMailSettings.draftMimeType,
                        publicKey = publicAddressKey.publicKey.key
                    )
                )
            } else {
                SendPreferencesOrError.Error.NoKeysAvailable
            }

        } else {

            val defaultPgpScheme =
                if (defaultMailSettings.pgpScheme == PackageType.PgpMime) PackageType.PgpMime else PackageType.Cleartext

            val defaultMimeType =
                if (defaultPgpScheme == PackageType.PgpMime) "multipart/mixed" else "text/plain"

            if (publicAddressKey != null) {

                if (publicAddressKey.isObsolete() || publicAddressKey.isCompromised()) return SendPreferencesOrError.Error.PublicKeysInvalid

                SendPreferencesOrError.Success(
                    SendPreferences(
                        encrypt = true,
                        sign = true,
                        pgpScheme = defaultPgpScheme,
                        mimeType = defaultMimeType,
                        publicKey = publicAddressKey.publicKey.key
                    )
                )
            } else {
                SendPreferencesOrError.Success(
                    SendPreferences(
                        encrypt = false,
                        sign = defaultMailSettings.sign,
                        pgpScheme = defaultPgpScheme,
                        mimeType = defaultMimeType,
                        publicKey = null
                    )
                )
            }

        }

    }

    /**
     * If true, do not use the key for encrypting, nor for signature verification.
     */
    private fun PublicAddressKey.isCompromised() = !(this.flags and 1 == 1)

    /**
     * If true, do not use the key to encrypt new messages, but can verify signatures.
     */
    private fun PublicAddressKey.isObsolete() = !(this.flags and 2 == 2)

}
