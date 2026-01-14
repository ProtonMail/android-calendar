package me.proton.android.calendar.domain.usecase

import android.util.Log
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import biweekly.parameter.ParticipationStatus
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.MemberPassphrase
import me.proton.android.calendar.test.shared.mocks.CalendarMocks
import me.proton.android.calendar.test.shared.mocks.UserMocks
import me.proton.android.calendar.test.shared.mocks.calendarColor
import me.proton.android.calendar.test.shared.mocks.calendarDisplay
import me.proton.android.calendar.test.shared.mocks.userEmail
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.AddressType
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.toBoolean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TransformEventUseCaseTest {

    private val json = Json
    private val testsLogger = TestsLogger
    private val valueStoreProviderMock: ValueStoreProvider = mockk()
    private val iCal = ICalUtilsImpl
    private val crypto: Crypto = mockk()
    private val obtainPinnedKeysUseCase: ObtainPinnedKeysUseCase = mockk()
    private val cryptoContextMock: CryptoContext = mockk()
    private val userAddressManagerMock: UserAddressManager = mockk()
    private val featureFlagManagerMock: FeatureFlagManager = mockk()
    private lateinit var database: AppDatabase

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true
        database = mockk()
    }

    @AfterEach
    fun `after each`() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `transform event entity to event`() {

        runBlocking {
            val eventEntity = EventEntity(
                "id",
                "calendarId",
                "sharedEventId",
                null,
                0,
                0,
                3,
                addressKeyPacket = null,
                addressId = null,
                "sharedKeyPacket",
                getMockedNonEncryptedSharedEvents(),
                getMockedNonEncryptedCalendarEvents(),
                getMockedNonEncryptedAttendeesEvents(),
                getMockedAttendees(),
                attendeesInfo = getMockedAttendees(),
                null
            )

            // calendarEntity
            val calendarEntity = CalendarEntity(
                "id",
                1,
                owner = Json.decodeFromString("{\"Email\": \"$userEmail\"}"),
                fkUserId = "fkUserId")
            coEvery { database.calendarsDao().selectById(any()) } returns calendarEntity

            // calendarKey
            val calendarKeyEntity = CalendarKeyEntity(
                "id",
                3,
                "privateKey",
                "passphraseId",
                "calendarId")
            coEvery {
                database.calendarKeysDao().select(any())
            } returns listOf(calendarKeyEntity)

            coEvery {
                database.addressDao().getByUserId(any())
            } returns listOf(UserMocks.provideAddressEntity())

            coEvery {
                database.membersDao().selectCalendarMembers(any())
            } returns listOf(CalendarMocks.provideMemberEntity())

            coEvery {
                database.calendarSettingsDao().select(any())
            } returns CalendarMocks.provideCalendarSettingsEntity()

            // calendarPassphrase
            val memberPassphrase = MemberPassphrase(
                "memberId",
                "passphrase",
                "signature")
            val passPhraseEntity = PassphraseEntity(
                "id",
                1,
                listOf(Json.decodeFromString<JsonElement>(Json.encodeToString(memberPassphrase))),
                "calendarId")
            coEvery {
                database.passphrasesDao().select(any())
            } returns listOf(passPhraseEntity)

            // keyPassphrase
            every {
                valueStoreProviderMock.provideValueStore(any()).getStringFromSet(any(), any())
            } returns "keyPassphrase"

            coEvery { userAddressManagerMock.getAddresses(any()) } returns listOf(
                UserAddress(
                    userId = UserId("id"),
                    addressId = AddressId("id"),
                    email = "calendarsingle9@proton.dev",
                    displayName = "calendarsingle9",
                    signature = null,
                    domainId = null,
                    canSend = true,
                    canReceive = true,
                    enabled = true,
                    type = AddressType.Original,
                    order = 1,
                    keys = mockk(),
                    signedKeyList = null
                )
            )

            coEvery {
                featureFlagManagerMock.getOrDefault(
                    UserId("fkUserId"),
                    CalendarFeatureFlag.RsvpCommentsAndroid.featureId,
                    FeatureFlag.default(
                        CalendarFeatureFlag.RsvpCommentsAndroid.featureId.id,
                        CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
                    )
                )
            } returns FeatureFlag.default(
                CalendarFeatureFlag.RsvpCommentsAndroid.featureId.id,
                CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
            )

            val useCase = TransformEventUseCase(
                json,
                database,
                userAddressManagerMock,
                testsLogger,
                valueStoreProviderMock,
                crypto,
                iCal,
                obtainPinnedKeysUseCase,
                cryptoContextMock,
                featureFlagManagerMock
            )
            val event = useCase.execute(eventEntity)
            assertThat(event).isNotNull()
            assertThat(event?.iCalEvent).isNotNull()

            // TODO after decrypton check if everything is the same for attendees info as well
            assertThat(event?.iCalEvent?.attendees.isNullOrEmpty()).isFalse()
            assertThat(event?.iCalEvent?.attendees?.size).isEqualTo(3)

            assertThat(event?.currentUserAttendeeId).isNotNull()
            assertThat(event?.currentUserAttendeeId).isEqualTo("6C5v4OC-Jhs8syzxmNwhyjZi1YG4USc2DLI7i_mnj6Mm6p6CK8s1mHn5RBb4tJ0XFACjVB-c4qXltm1ErQRY8w==")

            // Test cross reference logic between unencrypted Attendees and encrypted AttendeesEvents data
            assertThat(event?.iCalEvent?.attendees!![0].email).isEqualTo("calendarsingle9@proton.dev")
            assertThat(event.iCalEvent.attendees[0].participationStatus).isEqualTo(ParticipationStatus.TENTATIVE)
            assertThat(event.iCalEvent.attendees[1].email).isEqualTo("adamprotonmail@gmail.com")
            assertThat(event.iCalEvent.attendees[1].participationStatus).isEqualTo(ParticipationStatus.DECLINED)
            assertThat(event.iCalEvent.attendees[2].email).isEqualTo("adamtst@protonmail.com")
            assertThat(event.iCalEvent.attendees[2].participationStatus).isEqualTo(ParticipationStatus.NEEDS_ACTION)

            // TODO after decryption check if everything is the same for attendees info as well

            assertThat(event.verificationStatus).isEqualTo(Event.SignatureVerification.SIGNED_BUT_CANT_GET_KEYS)

            assertThat(event.calendar.color).isEqualTo(calendarColor)
            assertThat(event.calendar.display).isEqualTo(calendarDisplay.toBoolean())

        }
    }

    private fun getMockedAttendees(): List<JsonElement> {
        val list = ArrayList<JsonElement>()
        list.add(Json.decodeFromString<JsonElement>("{\"ID\":\"6C5v4OC-Jhs8syzxmNwhyjZi1YG4USc2DLI7i_mnj6Mm6p6CK8s1mHn5RBb4tJ0XFACjVB-c4qXltm1ErQRY8w==\",\"Token\":\"905eb4e54055cdb47d9edf7e8f6a778bca369a97\",\"Status\":1}\n"))
        list.add(Json.decodeFromString<JsonElement>("{\"ID\":\"1dPOFXdenTUVKinAVwaoxfHFVuJYO6VF58YzI6Xn7RZJV208mN5ZZUZj_oG-A4h3q6O6QnH5HibkEymNuROiGg==\",\"Token\":\"5651702a13a167b23fc30583c230d3dfd272a966\",\"Status\":2}\n"))
        list.add(Json.decodeFromString<JsonElement>("{\"ID\":\"8TjWIH0KrE_-9271lO0tPNn8T6UF-0SBCtzuK-31IUp_3y6eOuM_Ysi7Gp78nlM0hmhHrQha51FFwxk2CBORXA==\",\"Token\":\"cfc28ea87394a21df6456bbe059b3805bfc09fb8\",\"Status\":0}\n"))
        return list
    }

    private fun getMockedNonEncryptedAttendeesEvents(): List<JsonElement> {
        val list = ArrayList<JsonElement>()
        list.add(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nATTENDEE;CN=adamprotonmail@gmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-TO\\r\\n KEN=5651702a13a167b23fc30583c230d3dfd272a966:mailto:adamprotonmail@gmail.co\\r\\n m\\r\\nATTENDEE;CN=adamtst@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-TOKE\\r\\n N=cfc28ea87394a21df6456bbe059b3805bfc09fb8:mailto:adamtst@protonmail.com\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
        return list
    }

    private fun getMockedNonEncryptedCalendarEvents(): List<JsonElement> {
        val list = ArrayList<JsonElement>()
        list.add(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nDTSTAMP:20201020T144514Z\\r\\nSTATUS:CONFIRMED\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za6lgAQCzed5ovcz9WykC5+wSFxDCAToI8D5i+p3q0OVPrbIEVAEAvOe6\\r\\nEChxa70XES8QIhF4ddaC5XcU1uiUxNW/zIcTnwA=\\r\\n=pZlu\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
        return list
    }

    private fun getMockedNonEncryptedSharedEvents(): List<JsonElement> {
        val list = ArrayList<JsonElement>()
        list.add(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nDTSTAMP:20201020T144514Z\\r\\nDTSTART:20201020T161500Z\\r\\nDTEND:20201020T171500Z\\r\\nORGANIZER;CN=adamprotonmail@gmail.com:mailto:adamprotonmail@gmail.com\\r\\nSEQUENCE:1\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Zaz7vAP4pe2r4gIvwZq4HXEkyulH4JjNdSY0+v2vj9UZ3G39QIQD6A1+G\\r\\nCzdqvtZuXpPddMR/8SUH4+V+04CbkFHIngoM8wc=\\r\\n=aOeb\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
        list.add(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nDTSTAMP:20201020T144514Z\\r\\nDESCRIPTION:Great description from gcal gitlab\\r\\nSUMMARY:Über-cool event, edited from gmail\\r\\nLOCATION:Geneva, Switzerland\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za+jhAP9F5KqDGwcZdEZsora6BzptoenPn6cCmRNIbI3LAHQTAQD9GxOG\\r\\nlGgtndyaD4rx1xNI5RFLoiNtss1VgtQV9SgWNQs=\\r\\n=rG5S\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
        return list
    }
}
