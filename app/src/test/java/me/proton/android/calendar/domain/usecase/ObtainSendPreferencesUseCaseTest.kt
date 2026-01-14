package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.getUserOrNull
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.MailSettingsApiResponse
import me.proton.android.calendar.domain.api.MailSettingsApi
import me.proton.android.calendar.domain.model.PackageType
import me.proton.core.contact.domain.entity.Contact
import me.proton.core.contact.domain.entity.ContactCard
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.contact.domain.entity.ContactEmailId
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.contact.domain.entity.ContactWithCards
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicAddressKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.entity.key.Recipient
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ObtainSendPreferencesUseCaseTest {

    private val logger = TestsLogger
    private val contactEmailsRepositoryMock: ContactRepository = mockk()
    private val userManagerMock: UserManager = mockk()
    private val mailSettingsApiMock: MailSettingsApi = mockk()
    private val cryptoContextMock: CryptoContext = mockk()
    private val getRecipientPublicAddressesMock: GetRecipientPublicAddresses = mockk()

    private val json = Json { this.ignoreUnknownKeys = true }
    private val userId = UserId("test-user-id")
    private val userMock: User = mockk()

    private var sut = ObtainSendPreferencesUseCase(
        logger,
        contactEmailsRepositoryMock,
        userManagerMock,
        mailSettingsApiMock,
        cryptoContextMock,
        getRecipientPublicAddressesMock
    )

    @BeforeEach
    fun `before each`() {
        clearAllMocks()

        sut = ObtainSendPreferencesUseCase(
            logger,
            contactEmailsRepositoryMock,
            userManagerMock,
            mailSettingsApiMock,
            cryptoContextMock,
            getRecipientPublicAddressesMock
        )

        coEvery { mailSettingsApiMock.getMailSettings(userId) } returns mailSettingsSignTrue

        coEvery { contactEmailsRepositoryMock.getAllContactEmails(userId, refresh = true) } returns listOf(
            ContactEmail(
                userId,
                ContactEmailId("1"),
                "External Contact with pinned key",
                "contact_external_pinned_key+alias@email.com",
                defaults = 0,
                1,
                contactId = ContactId("contact_1"),
                "contact_external_pinned_key@email.com",
                labelIds = emptyList(),
                isProton = null,
                lastUsedTime = 0
            )
        )

        coEvery { contactEmailsRepositoryMock.getContactWithCards(userId, ContactId("contact_1"), refresh = true) } returns externalContactWithPinnedKeyBrokenSignature

        coEvery { getRecipientPublicAddressesMock.invoke(userId, any()) } returns mapOf(
            "disabled_address@pm.me" to null, // address is disabled
            "unknown_external_no_keys@email.com" to unknownExternalRecipientNoKeysPublicAddress,
            "unknown_external_with_keys@email.com" to unknownExternalRecipientWithKeysPublicAddress,
            "unknown_internal@pm.me" to unknownInternalRecipientWithKeysPublicAddress,
            "unknown_internal_no_keys@pm.me" to unknownInternalRecipientNoKeysPublicAddress,
            "contact_external_pinned_key@email.com" to contactExternalPinnedKeyPublicAddress
        )

        coEvery { userManagerMock.getUserOrNull(userId, logger) } returns userMock
    }

    @Test
    fun `handle disabled addresses`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "disabled_address@pm.me" to "disabled_address@pm.me"
            )

            val result = sut.execute(userId, canonicalEmails)

            assertThat(result["disabled_address@pm.me"]).isEqualTo(ObtainSendPreferencesUseCase.Result.Error.AddressDisabled)

        }
    }

    @Test
    fun `handle unknown external recipient`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "unknown_external_no_keys@email.com" to "unknown_external_no_keys@email.com",
                "unknown_external_with_keys@email.com" to "unknown_external_with_keys@email.com",
            )

            // default sign = true

            coEvery { mailSettingsApiMock.getMailSettings(userId) } returns mailSettingsSignTrue

            val resultDefaultSignTrue = sut.execute(userId, canonicalEmails)

            assertThat(resultDefaultSignTrue["unknown_external_no_keys@email.com"] is ObtainSendPreferencesUseCase.Result.Success).isTrue()

            with((resultDefaultSignTrue["unknown_external_no_keys@email.com"] as ObtainSendPreferencesUseCase.Result.Success).sendPreferences) {
                assertThat(encrypt).isFalse()
                assertThat(sign).isTrue()
            }

            assertThat(resultDefaultSignTrue["unknown_external_with_keys@email.com"] is ObtainSendPreferencesUseCase.Result.Success).isTrue()

            with((resultDefaultSignTrue["unknown_external_with_keys@email.com"] as ObtainSendPreferencesUseCase.Result.Success).sendPreferences) {
                assertThat(encrypt).isTrue()
                assertThat(sign).isTrue()
            }

            // default sign = false

            coEvery { mailSettingsApiMock.getMailSettings(userId) } returns mailSettingsSignFalse

            val resultDefaultSignFalse = sut.execute(userId, canonicalEmails)

            assertThat(resultDefaultSignFalse["unknown_external_no_keys@email.com"] is ObtainSendPreferencesUseCase.Result.Success).isTrue()

            with((resultDefaultSignFalse["unknown_external_no_keys@email.com"] as ObtainSendPreferencesUseCase.Result.Success).sendPreferences) {
                assertThat(encrypt).isFalse()
                assertThat(sign).isFalse()
            }

            assertThat(resultDefaultSignFalse["unknown_external_with_keys@email.com"] is ObtainSendPreferencesUseCase.Result.Success).isTrue()

            with((resultDefaultSignFalse["unknown_external_with_keys@email.com"] as ObtainSendPreferencesUseCase.Result.Success).sendPreferences) {
                assertThat(encrypt).isTrue()
                assertThat(sign).isTrue()
            }

        }
    }

    @Test
    fun `handle unknown internal recipient`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "unknown_internal@pm.me" to "unknown_internal@pm.me"
            )

            // default sign = false but we'll sign anyway

            coEvery { mailSettingsApiMock.getMailSettings(userId) } returns mailSettingsSignFalse

            val resultDefaultSignFalse = sut.execute(userId, canonicalEmails)

            with((resultDefaultSignFalse["unknown_internal@pm.me"] as ObtainSendPreferencesUseCase.Result.Success).sendPreferences) {
                assertThat(encrypt).isTrue()
                assertThat(sign).isTrue()
                assertThat(pgpScheme).isEqualTo(PackageType.ProtonMail)
            }

        }
    }

    @Test
    fun `handle unknown internal recipient, incorrect state with PublicAddress with no public keys`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "unknown_internal_no_keys@pm.me" to "unknown_internal_no_keys@pm.me"
            )

            // default sign = false but we'll sign anyway

            coEvery { mailSettingsApiMock.getMailSettings(userId) } returns mailSettingsSignFalse

            val resultDefaultSignFalse = sut.execute(userId, canonicalEmails)

            assertThat(resultDefaultSignFalse["unknown_internal_no_keys@pm.me"] is ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences).isTrue()

        }
    }

    @Test
    fun `handle createDefaultSendPreferences for unknown external recipient with keys`() {

        val result = (sut.createDefaultSendPreferences(
            mailSettingsSignFalse.data.mailSettings.toMailSettings()!!,
            unknownExternalRecipientWithKeysPublicAddress
        ) as ObtainSendPreferencesUseCase.SendPreferencesOrError.Success).sendPreferences

        with(result) {
            assertThat(encrypt).isTrue()
            assertThat(sign).isTrue()
            assertThat(pgpScheme).isEqualTo(PackageType.PgpMime)
            assertThat(publicKey).isNotNull()
        }

    }

    @Test
    fun `external contact with pinned key and email alias should be fetched, VCard extracted`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "contact_external_pinned_key+alias@email.com" to "contact_external_pinned_key@email.com"
            )

            val map = sut.execute(userId, canonicalEmails)

            coVerify(exactly = 1) { contactEmailsRepositoryMock.getContactWithCards(userId, ContactId("contact_1"), refresh = true) }
            val result = map["contact_external_pinned_key+alias@email.com"]
            assertInstanceOf(ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys::class.java, result)
        }
    }

    @Test
    fun `error verifying broken VCard signature`() {
        runBlocking {

            val canonicalEmails = mapOf(
                "contact_external_pinned_key+alias@email.com" to "contact_external_pinned_key@email.com"
            )

            val map = sut.execute(userId, canonicalEmails)

            coVerify(exactly = 1) { contactEmailsRepositoryMock.getContactWithCards(userId, ContactId("contact_1"), refresh = true) }

            val result = map["contact_external_pinned_key+alias@email.com"]
            assertInstanceOf(ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys::class.java, result)
        }
    }

    // TODO extractSignedVCard test

    private val mailSettingsSignTrue = ApiResponse.Success<MailSettingsApiResponse>(
        json.decodeFromString(
            """
                {"Code":1000,"MailSettings":{"LastLoginTime":0,"AutoSaveContacts":1,"AutoWildcardSearch":1,
                "ComposerMode":0,"MessageButtons":0,"ShowImages":2,"ShowMoved":0,"ViewMode":0,
                "ViewLayout":0,"SwipeLeft":2,"SwipeRight":3,"AlsoArchive":0,"Hotkeys":1,"Shortcuts":1,
                "PMSignature":1,"ImageProxy":0,"TLS":0,"RightToLeft":0,"AttachPublicKey":0,
                "Sign":1,"PGPScheme":16,
                "PromptPin":0,"KT":0,"Autocrypt":0,"StickyLabels":0,"ExpandFolders":0,
                "ConfirmLink":1,"DelaySendSeconds":10,"ThemeType":0,"ThemeVersion":null,
                "Theme":"","DisplayName":"","Signature":"",
                "AutoResponder":{"StartTime":0,"EndTime":0,"DaysSelected":[],"Repeat":0,"Subject":"Auto",
                "Message":"","IsEnabled":false,"Zone":"Europe/Zurich"},"EnableFolderColor":0,
                "InheritParentFolderColor":1,"NumMessagePerPage":50,"RecipientLimit":100,
                "DraftMIMEType":"text/html",
                "ReceiveMIMEType":"text/html","ShowMIMEType":"text/html"}}
            """.trimIndent()
        )
    )

    private val mailSettingsSignFalse = ApiResponse.Success<MailSettingsApiResponse>(
        json.decodeFromString(
            """
                {"Code":1000,"MailSettings":{"LastLoginTime":0,"AutoSaveContacts":1,"AutoWildcardSearch":1,
                "ComposerMode":0,"MessageButtons":0,"ShowImages":2,"ShowMoved":0,"ViewMode":0,
                "ViewLayout":0,"SwipeLeft":2,"SwipeRight":3,"AlsoArchive":0,"Hotkeys":1,"Shortcuts":1,
                "PMSignature":1,"ImageProxy":0,"TLS":0,"RightToLeft":0,"AttachPublicKey":0,
                "Sign":0,"PGPScheme":16,
                "PromptPin":0,"KT":0,"Autocrypt":0,"StickyLabels":0,"ExpandFolders":0,
                "ConfirmLink":1,"DelaySendSeconds":10,"ThemeType":0,"ThemeVersion":null,
                "Theme":"","DisplayName":"","Signature":"",
                "AutoResponder":{"StartTime":0,"EndTime":0,"DaysSelected":[],"Repeat":0,"Subject":"Auto",
                "Message":"","IsEnabled":false,"Zone":"Europe/Zurich"},"EnableFolderColor":0,
                "InheritParentFolderColor":1,"NumMessagePerPage":50,"RecipientLimit":100,
                "DraftMIMEType":"text/html",
                "ReceiveMIMEType":"text/html","ShowMIMEType":"text/html"}}
            """.trimIndent()
        )
    )

    private val unknownExternalRecipientWithKeysPublicAddress = PublicAddress(
        "unknown_external_with_keys@email.com",
        recipientType = Recipient.External.value,
        "text/html",
        listOf(
            PublicAddressKey("unknown_external_with_keys@email.com", 3, PublicKey("armored key", isPrimary = false, true, true, true)),
            PublicAddressKey("unknown_external_with_keys@email.com", 3, PublicKey("armored key", isPrimary = true, true, true, true))
        ),
        null,
        ignoreKT=0
    )

    private val unknownExternalRecipientNoKeysPublicAddress = PublicAddress(
        "unknown_external_no_keys@email.com",
        recipientType = Recipient.External.value,
        "text/html",
        emptyList(),
        null,
        ignoreKT=0
    )

    private val unknownInternalRecipientWithKeysPublicAddress = PublicAddress(
        "unknown_internal@pm.me",
        recipientType = Recipient.Internal.value,
        "text/html",
        listOf(
            PublicAddressKey("unknown_internal@pm.me", 3, PublicKey("armored key", isPrimary = false, true, true, true)),
            PublicAddressKey("unknown_internal@pm.me", 3, PublicKey("armored key", isPrimary = true, true, true, true))
        ),
        null,
        ignoreKT=0
    )

    private val unknownInternalRecipientNoKeysPublicAddress = PublicAddress(
        "unknown_internal_no_keys@pm.me",
        recipientType = Recipient.Internal.value,
        "text/html",
        emptyList(),
        null,
        ignoreKT=0
    )

    private val contactExternalPinnedKeyPublicAddress = PublicAddress(
        "contact_external_pinned_key@pm.me",
        recipientType = Recipient.External.value,
        "text/html",
        listOf(
            PublicAddressKey(
                "contact_external_pinned_key@pm.me", 3, PublicKey(
                    "armored key from public repository",
                    isPrimary = true,
                    true, true, true
                )
            ),
        ),
        null,
        ignoreKT=0
    )

    private val externalContactWithPinnedKeyBrokenSignature: ContactWithCards =
        ContactWithCards(
            contact = Contact(
                userId,
                id = ContactId("1"),
                name = "External Contact with pinned key",
                contactEmails = listOf(
                    ContactEmail(
                        userId,
                        ContactEmailId("1"),
                        name = "External Contact with pinned key",
                        email = "contact_external_pinned_key+alias@email.com",
                        defaults = 0,
                        order = 1,
                        contactId = ContactId("contact_1"),
                        canonicalEmail = null,
                        labelIds = emptyList(),
                        isProton = null,
                        lastUsedTime = 0
                    ) /* this is deliberately null here, API doesn't return it */
                ),
            ),
            contactCards = listOf(
                ContactCard.Encrypted("encrypted and signed data", "signature"),
                ContactCard.Signed(
                    "BEGIN:VCARD\r\nVERSION:4.0\r\nFN;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.EMAIL;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.KEY;PREF=1:data:application/pgp-keys;base64,xjMEYIE/zBYJKwYBBAHaRw8BA\r\n QdAU0kzBdPct+/iReob+92uE1hEJPzoXnrrTqx5p8EoOa7NLWNhbGVuZGFyQHByb3Rvbi5ibGFj\r\n ayA8Y2FsZW5kYXJAcHJvdG9uLmJsYWNrPsKPBBAWCgAgBQJggT/MBgsJBwgDAgQVCAoCBBYCAQA\r\n CGQECGwMCHgEAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP09xeAQD3ioSt4E6SyV\r\n xOeS8xBQvhuEXkqBKKZCkMO10fd0P2LgD/WvtGpRv8JAll0feMgG2y1lufZtJImTeLr0ciYb7AE\r\n gnOOARggT/MEgorBgEEAZdVAQUBAQdAJYTJ0NuH3zSCNxk+gsFNTVHuPDLQQLRsyNermAbrEXID\r\n AQgHwngEGBYIAAkFAmCBP8wCGwwAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP0/e\r\n ZAQC9vSk4lPi9v1dMHsbKCChrYPR2WCMSUXykpNcDuP2TBgEA0jjgSKW351PQTmHU15UcSFY71O\r\n pD+j04Cs4EcONklw0=\r\nUID:proton-web-4e57f941-d1b4-7909-c879-73a2df5513f1\r\nITEM1.X-PM-ENCRYPT:true\r\nITEM1.X-PM-SIGN:true\r\nEND:VCARD",
                    "correct signature"
                )
            )
        )

}


