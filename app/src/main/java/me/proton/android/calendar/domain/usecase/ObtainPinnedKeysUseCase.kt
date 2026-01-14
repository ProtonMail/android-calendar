package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.utils.CryptoUtilsImpl.extractPinnedKeys
import me.proton.android.calendar.common.utils.extractSignedVCard
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.utils.CryptoUtils
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.filterNullValues
import javax.inject.Inject

/**
 * Obtains Public Keys for given email address from User's Contacts VCard
 * and Proton's Public Key API service.
 */
class ObtainPinnedKeysUseCase @Inject constructor(
    private val logger: Logger,
    private val contactEmailsRepository: ContactRepository,
    private val userManager: UserManager,
    private val cryptoContext: CryptoContext,
    private val getRecipientPublicAddresses: GetRecipientPublicAddresses
) : UseCase {

    sealed class Result {
        data class Success(val pinnedPublicKeys: List<PublicKey>) : Result()

        sealed class Error : Result() {
            object AddressDisabled : Error()
            object GettingPinnedKeys : Error()
            object TrustedKeysInvalid : Error()
            object NoCorrectlySignedTrustedKeys : Error()
            object PublicKeysInvalid : Error()
            object NetworkError : Error()
            object EmailNotInContacts : Error()
        }
    }

    suspend fun execute(
        userId: UserId,
        emails: List<Email>
    ): Map<Email, Result> {

        val user = userManager.getUserOrNull(userId, logger)

        if (user == null) {
            logger.i("ObtainPinnedKeysUseCase User is null")
        }

        // get all User's Contact Emails
        val contactEmails =
            kotlin.runCatching { contactEmailsRepository.getAllContactEmails(userId, refresh = true) }.getOrElse {
                logger.i("ObtainPinnedKeysUseCase error getting all contact emails", it)
                null
            }

        val result = HashMap<Email, Result>()

        if (contactEmails == null || user == null) {
            emails.forEach {
                result[it] = Result.Error.NetworkError
            }
            return result
        }

        // get public Addresses, will be needed to check if key is compromised, obsolete, etc.
        val publicAddresses = getRecipientPublicAddresses.invoke(userId, emails)

        publicAddresses.forEach {
            // if we couldn't get Public Keys for email
            if (it.value == null && emails.contains(it.key) && !result.containsKey(it.key)) {
                result[it.key] = Result.Error.AddressDisabled
            }
        }

        // fetch full Contact Info containing VCard
        val fullContacts = emails.filterNot { result.containsKey(it) }.map { email ->

            val emailInContacts = contactEmails.find { email.equalsNoCase(it.email) || email.equalsNoCase(it.canonicalEmail) }

            val fullContact = emailInContacts?.let { contactEmail ->
                kotlin.runCatching { contactEmailsRepository.getContactWithCards(userId, contactEmail.contactId, refresh = true) }.getOrElse {
                    logger.i("ObtainPinnedKeysUseCase error getting full contacts", it)
                    null
                }
            }

            if (emailInContacts == null) {
                if (!result.containsKey(email)) result[email] = Result.Error.EmailNotInContacts
                email to null
            } else {
                email to fullContact
            }
        }.toMap()

        fullContacts.forEach {
            if (it.value == null && !result.containsKey(it.key)) result[it.key] = Result.Error.NetworkError
        }

        // obtain VCards from full Contacts
        val validVCards = fullContacts.filterNullValues().mapValues { entry ->
            entry.value.extractSignedVCard(user, cryptoContext, logger).also {
                if (it == null) result[entry.key] = Result.Error.NoCorrectlySignedTrustedKeys
            }
        }

        // parse VCards to extract valid Public Key matching the email address
        emails.filterNot { result.containsKey(it) }.forEach { email ->

            val vCardEmail = contactEmails.find { email.equalsNoCase(it.email) || email.equalsNoCase(it.canonicalEmail) }?.email
            val vCard = validVCards[email]
            val publicAddress = publicAddresses[email]

            val pinnedKeysOrError = if (vCardEmail != null && vCard != null && publicAddress != null) {
                extractPinnedKeys(CryptoUtils.PinnedKeysPurpose.VerifyingSignature, vCardEmail, vCard, publicAddress, cryptoContext)
            } else CryptoUtils.PinnedKeysOrError.Error.NotEnoughData

            result[email] = when (pinnedKeysOrError) {
                is CryptoUtils.PinnedKeysOrError.Success -> Result.Success(pinnedKeysOrError.pinnedPublicKeys)
                is CryptoUtils.PinnedKeysOrError.Error.PublicKeysInvalid -> Result.Error.PublicKeysInvalid
                is CryptoUtils.PinnedKeysOrError.Error.TrustedKeysInvalid -> Result.Error.TrustedKeysInvalid
                else -> Result.Error.GettingPinnedKeys
            }

        }

        return result
    }

}
