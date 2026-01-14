package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

private const val MAX_EMAILS_PER_QUERY: Int = 8

// TODO move this class to core

class GetCanonicalEmailsUseCase @Inject constructor(
    private val addressesApi: AddressesApi,
    private val logger: Logger
) {

    /**
     * Returns Map of <Email String, canonical version of Email String> getting rid of address aliases,
     * different capitalisations, etc. according to server rules.
     *
     * @return Email mapped to null if its canonicalization failed, or no Email in the map in case of other error
     */
    suspend operator fun invoke(userId: UserId, emails: List<String>): Map<String, String?> {

        val emailPairs = HashMap<String, String?>()

        // TODO Optimize the chunks to send the most amount of emails per GET call
        val chunkedEmails = emails.chunked(MAX_EMAILS_PER_QUERY)

        chunkedEmails.forEach { smallerEmailList ->
            when (val canonicalResult = addressesApi.getCanonicalEmails(userId, smallerEmailList)) {
                is ApiResponse.Success -> {
                    canonicalResult.data.canonicalEmailsResponses.forEach {
                        emailPairs[it.email] = it.canonicalEmailResponse.canonicalEmail
                    }
                }
                is ApiResponse.Error -> {
                    canonicalResult.logErrorIfNeeded("UsersRepositoryImpl: error getting canonical emails", logger)
                }
                is ApiResponse.Exception -> {
                    canonicalResult.logErrorIfNeeded("UsersRepositoryImpl: exception getting canonical emails", logger)
                }
            }
        }

        return emailPairs

    }

}
