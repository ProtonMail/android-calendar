package me.proton.android.calendar.data.api

import me.proton.android.calendar.domain.api.EmailMessageRepository
import me.proton.android.calendar.domain.model.EncryptedPackage
import me.proton.android.calendar.domain.model.toEmailPackage
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.data.api.MailMessageApi
import me.proton.core.mailmessage.data.api.request.SendDirectRequest
import me.proton.core.mailmessage.domain.entity.EmailReceipt
import me.proton.core.network.data.ApiProvider
import me.proton.core.util.kotlin.toInt

class EmailMessageRepositoryImpl(
    private val provider: ApiProvider
) : EmailMessageRepository {

    override suspend fun sendEmailDirect(
        userId: UserId,
        emailMessage: me.proton.core.mailmessage.data.api.request.EmailMessage,
        encryptedPackages: List<EncryptedPackage>,
        attachmentKeys: List<String>?
    ): EmailReceipt =
        provider.get<MailMessageApi>(userId).invoke {
            val response = sendDirect(
                SendDirectRequest(
                    emailMessage = emailMessage,
                    autoSaveContacts = false.toInt(),
                    packages = encryptedPackages.map { it.toEmailPackage() },
                    attachmentKeys = attachmentKeys
                )
            )
            EmailReceipt(response.deliveryTime)
        }.valueOrThrow
}
