package me.proton.android.calendar.test.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ezvcard.Ezvcard
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.MailSettingsApiResponse
import me.proton.android.calendar.domain.api.MailSettingsApi
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.core.contact.domain.entity.ContactCard
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.PGPCrypto
import me.proton.core.key.domain.entity.key.PublicAddress
import me.proton.core.key.domain.entity.key.PublicAddressKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.entity.key.Recipient
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.user.domain.UserManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ObtainSendPreferencesUseCaseInstrumentalTest {

    private val logger = TestsLogger
    private val contactEmailsRepositoryMock: ContactRepository = mockk()
    private val userManagerMock: UserManager = mockk()
    private val mailSettingsApiMock: MailSettingsApi = mockk()
    private val cryptoContextMock: CryptoContext = mockk()
    private val pgpCryptoMock: PGPCrypto = mockk()
    private val getRecipientPublicAddressesMock: GetRecipientPublicAddresses = mockk()

    private val json = Json { this.ignoreUnknownKeys = true }

    var sut = ObtainSendPreferencesUseCase(
        logger,
        contactEmailsRepositoryMock,
        userManagerMock,
        mailSettingsApiMock,
        cryptoContextMock,
        getRecipientPublicAddressesMock
    )

    @Before
    fun beforeEach() {
        clearAllMocks()

        coEvery { cryptoContextMock.pgpCrypto } returns pgpCryptoMock
    }

    @Test
    fun handle_createCustomSendPreferences_for_contact_with_pinned_key() {

        val vCardEmail = "contact_external_pinned_key+alias@email.com"
        val vCard = Ezvcard.parse((externalContactWithPinnedKeyEncryptTrueCards.first { it is ContactCard.Signed } as ContactCard.Signed).data).first()!!

        coEvery { pgpCryptoMock.getFingerprint(any()) } returns "key fingerprint"

        val result = (sut.createCustomSendPreferences(vCardEmail, contactExternalPinnedKeyPublicAddress, vCard, mailSettingsSignFalse.data.mailSettings.toMailSettings()!!) as ObtainSendPreferencesUseCase.SendPreferencesOrError.Success).sendPreferences

        with (result) {
            assertTrue(encrypt)
            assertTrue(sign)
            assertEquals(pgpScheme.type, PackageType.PgpMime.type)

            assertTrue(publicKey!!.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----"))
            assertNotEquals("armored key from public repository", publicKey!!)
        }

    }

    @Test
    fun handle_createCustomSendPreferences_for_contact_with_pinned_key_but_do_not_encrypt() {

        val vCardEmail = "contact_external_pinned_key+alias@email.com"
        val vCard = Ezvcard.parse((externalContactWithPinnedKeyEncryptFalseCards.first { it is ContactCard.Signed } as ContactCard.Signed).data).first()!!

        coEvery { pgpCryptoMock.getFingerprint(any()) } returns "key fingerprint"

        val result = (sut.createCustomSendPreferences(vCardEmail, contactExternalPinnedKeyPublicAddress, vCard, mailSettingsSignFalse.data.mailSettings.toMailSettings()!!) as ObtainSendPreferencesUseCase.SendPreferencesOrError.Success).sendPreferences

        with (result) {
            assertFalse(encrypt)
            assertTrue(sign)
        }

    }

    @Test
    fun handle_createCustomSendPreferences_for_contact_with_pinned_key_and_no_key_in_public_repo() {

        val vCardEmail = "contact_external_pinned_key+alias@email.com"
        val vCard = Ezvcard.parse((externalContactWithPinnedKeyEncryptFalseCards.first { it is ContactCard.Signed } as ContactCard.Signed).data).first()!!

        coEvery { pgpCryptoMock.getFingerprint(any()) } returns "key fingerprint"

        val result = sut.createCustomSendPreferences(vCardEmail, contactExternalPinnedKeyEmptyPublicAddress, vCard, mailSettingsSignFalse.data.mailSettings.toMailSettings()!!)

        assertTrue(result is ObtainSendPreferencesUseCase.SendPreferencesOrError.Success)

    }

    @Test
    fun handle_createCustomSendPreferences_for_contact_with_pinned_key_but_key_is_obsolete() {

        val vCardEmail = "contact_external_pinned_key+alias@email.com"
        val vCard = Ezvcard.parse((externalContactWithPinnedKeyEncryptFalseCards.first { it is ContactCard.Signed } as ContactCard.Signed).data).first()!!

        coEvery { pgpCryptoMock.getFingerprint(any()) } returns "key fingerprint"

        val result = sut.createCustomSendPreferences(vCardEmail, contactExternalPinnedKeyPublicAddressObsolete, vCard, mailSettingsSignFalse.data.mailSettings.toMailSettings()!!)

        assertTrue(result is ObtainSendPreferencesUseCase.SendPreferencesOrError.Error.TrustedKeysInvalid)

    }

    @Test
    fun handle_createCustomSendPreferences_for_contact_with_pinned_key_but_key_is_compromised() {

        val vCardEmail = "contact_external_pinned_key+alias@email.com"
        val vCard = Ezvcard.parse((externalContactWithPinnedKeyEncryptFalseCards.first { it is ContactCard.Signed } as ContactCard.Signed).data).first()!!

        coEvery { pgpCryptoMock.getFingerprint(any()) } returns "key fingerprint"

        val result = sut.createCustomSendPreferences(vCardEmail, contactExternalPinnedKeyPublicAddressCompromised, vCard, mailSettingsSignFalse.data.mailSettings.toMailSettings()!!)

        assertTrue(result is ObtainSendPreferencesUseCase.SendPreferencesOrError.Error.TrustedKeysInvalid)

    }

    private val mailSettingsSignFalse = ApiResponse.Success<MailSettingsApiResponse>(json.decodeFromString(
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
    ))

    private val contactExternalPinnedKeyEmptyPublicAddress = PublicAddress(
        "contact_external_pinned_key@pm.me",
        recipientType = Recipient.External.value,
        "text/html",
        emptyList(),
        null,
        null
    )

    private val contactExternalPinnedKeyPublicAddress = PublicAddress(
        "contact_external_pinned_key@pm.me",
        recipientType = Recipient.External.value,
        "text/html",
        listOf(
            PublicAddressKey(
                "contact_external_pinned_key@pm.me",
                3,
                PublicKey(
                    "armored key from public repository",
                    isPrimary = true,
                    true,
                    true,
                    true
                )
            ),
        ),
        null, null // TODO
    )

    private val contactExternalPinnedKeyPublicAddressCompromised = PublicAddress(
        "contact_external_pinned_key@pm.me",
        recipientType = Recipient.External.value,
        "text/html",
        listOf(
            PublicAddressKey(
                "contact_external_pinned_key@pm.me",
                0,
                PublicKey("armored key from public repository",
                    isPrimary = true,
                    true,
                    true,
                    true
                )
            ),
        ),
        null, null // TODO
    )

    private val contactExternalPinnedKeyPublicAddressObsolete = PublicAddress(
        "contact_external_pinned_key@pm.me",
        recipientType = Recipient.External.value,
        "text/html",
        listOf(
            PublicAddressKey(
                "contact_external_pinned_key@pm.me",
                1,
                PublicKey(
                    "armored key from public repository",
                    isPrimary = true,
                    true,
                    true,
                    true
                )
            ),
        ),
        null, null, // TODO
    )

    private val externalContactWithPinnedKeyEncryptTrueCards =
        listOf(
            ContactCard.Encrypted(
                "encrypted and signed data",
                "signature"
            ),
            ContactCard.Signed(
                "BEGIN:VCARD\r\nVERSION:4.0\r\nFN;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.EMAIL;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.KEY;PREF=1:data:application/pgp-keys;base64,xjMEYIE/zBYJKwYBBAHaRw8BA\r\n QdAU0kzBdPct+/iReob+92uE1hEJPzoXnrrTqx5p8EoOa7NLWNhbGVuZGFyQHByb3Rvbi5ibGFj\r\n ayA8Y2FsZW5kYXJAcHJvdG9uLmJsYWNrPsKPBBAWCgAgBQJggT/MBgsJBwgDAgQVCAoCBBYCAQA\r\n CGQECGwMCHgEAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP09xeAQD3ioSt4E6SyV\r\n xOeS8xBQvhuEXkqBKKZCkMO10fd0P2LgD/WvtGpRv8JAll0feMgG2y1lufZtJImTeLr0ciYb7AE\r\n gnOOARggT/MEgorBgEEAZdVAQUBAQdAJYTJ0NuH3zSCNxk+gsFNTVHuPDLQQLRsyNermAbrEXID\r\n AQgHwngEGBYIAAkFAmCBP8wCGwwAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP0/e\r\n ZAQC9vSk4lPi9v1dMHsbKCChrYPR2WCMSUXykpNcDuP2TBgEA0jjgSKW351PQTmHU15UcSFY71O\r\n pD+j04Cs4EcONklw0=\r\nUID:proton-web-4e57f941-d1b4-7909-c879-73a2df5513f1\r\nITEM1.X-PM-ENCRYPT:true\r\nITEM1.X-PM-SIGN:true\r\nEND:VCARD",
                "correct signature"
            )
        )

    private val externalContactWithPinnedKeyEncryptFalseCards =
        listOf(
            ContactCard.Encrypted(
                "encrypted and signed data",
                "signature"
            ),
            ContactCard.Signed(
                "BEGIN:VCARD\r\nVERSION:4.0\r\nFN;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.EMAIL;PREF=1:contact_external_pinned_key+alias@email.com\r\nITEM1.KEY;PREF=1:data:application/pgp-keys;base64,xjMEYIE/zBYJKwYBBAHaRw8BA\r\n QdAU0kzBdPct+/iReob+92uE1hEJPzoXnrrTqx5p8EoOa7NLWNhbGVuZGFyQHByb3Rvbi5ibGFj\r\n ayA8Y2FsZW5kYXJAcHJvdG9uLmJsYWNrPsKPBBAWCgAgBQJggT/MBgsJBwgDAgQVCAoCBBYCAQA\r\n CGQECGwMCHgEAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP09xeAQD3ioSt4E6SyV\r\n xOeS8xBQvhuEXkqBKKZCkMO10fd0P2LgD/WvtGpRv8JAll0feMgG2y1lufZtJImTeLr0ciYb7AE\r\n gnOOARggT/MEgorBgEEAZdVAQUBAQdAJYTJ0NuH3zSCNxk+gsFNTVHuPDLQQLRsyNermAbrEXID\r\n AQgHwngEGBYIAAkFAmCBP8wCGwwAIQkQ9LTBFWUbz9MWIQQL9ztQ8o2jSXASlPX0tMEVZRvP0/e\r\n ZAQC9vSk4lPi9v1dMHsbKCChrYPR2WCMSUXykpNcDuP2TBgEA0jjgSKW351PQTmHU15UcSFY71O\r\n pD+j04Cs4EcONklw0=\r\nUID:proton-web-4e57f941-d1b4-7909-c879-73a2df5513f1\r\nITEM1.X-PM-ENCRYPT:false\r\nITEM1.X-PM-SIGN:true\r\nEND:VCARD",
                "correct signature"
            )
        )
}
