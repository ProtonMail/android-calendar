package me.proton.android.calendar.domain.api

import me.proton.android.calendar.domain.model.EncryptedPackage
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.EmailReceipt
import me.proton.core.mailmessage.domain.entity.EncryptedEmail

// TODO move to core when we fix EncryptedPackage class
interface EmailMessageRepository {
    suspend fun sendEmailDirect(
        userId: UserId,
        emailMessage: me.proton.core.mailmessage.data.api.request.EmailMessage,
        encryptedPackages: List<EncryptedPackage>,
        attachmentKeys: List<String>?
    ): EmailReceipt
}
