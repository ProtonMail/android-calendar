@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HandleIcsUseCaseTest {

    private val logger = mockk<Logger>(relaxed = true)
    private val json = mockk<Json>(relaxed = true)
    private val userAddressManager = mockk<UserAddressManager>()
    private val calendarsRepository = mockk<CalendarsRepository>()
    private val transformEventUseCase = mockk<TransformEventUseCase>()
    private val editCreateEventUseCase = mockk<EditCreateEventUseCase>()
    private val updateParticipationStatusUseCase = mockk<UpdateParticipationStatusUseCase>()
    private val updateCalendarUseCase = mockk<UpdateCalendarUseCase>(relaxed = true)
    private val handleDeleteUseCase = mockk<HandleDeleteUseCase>()
    private val canonicalEmailsUseCase = mockk<GetCanonicalEmailsUseCase>()
    private val eventDecryptor = mockk<EventDecryptor>()
    private val updateEventOccurrencesUseCase = mockk<UpdateEventOccurrencesUseCase>(relaxed = true)
    private val featureFlagManager = mockk<FeatureFlagManager>(relaxed = true)

    private lateinit var sut: HandleIcsUseCase

    private val userId = UserId("u-123")
    private val protonAddress = "proton.address@proton.me"
    private val gmailAddress = "external.address@gmail.com"
    private val senderAddress = "sender.address@proton.me"
    private val eventUid = "event-uid-123"

    @BeforeEach
    fun setup() {
        sut = HandleIcsUseCase(
            logger,
            json,
            userAddressManager,
            calendarsRepository,
            transformEventUseCase,
            editCreateEventUseCase,
            updateParticipationStatusUseCase,
            updateCalendarUseCase,
            handleDeleteUseCase,
            canonicalEmailsUseCase,
            eventDecryptor,
            updateEventOccurrencesUseCase,
            featureFlagManager,
        )
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun mockAddresses(vararg addresses: String) {
        val list = addresses.map { addr ->
            mockk<UserAddress> { every { email } returns addr }
        }
        coEvery { userAddressManager.getAddressesOrNull(userId) } returns list
    }

    private fun stubDefaultCalendar() {
        val cal = Calendar(
            id = "cal-1",
            name = "Default",
            email = protonAddress,
            ownerEmail = protonAddress,
            description = "desc",
            color = "color",
            priority = 0,
            addressId = "addr-1",
            memberId = null,
            flags = 0,
            display = true,
            type = 0,
            permissions = 0,
            defaultEventDuration = 60,
            defaultPartDayNotifications = emptyList(),
            defaultFullDayNotifications = emptyList()
        )
        coEvery { calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared = false) } returns cal.id
        coEvery { calendarsRepository.selectCalendar(cal.id) } returns cal
        coEvery { calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id) } returns "UTC"
    }

    private fun stubNoExistingEvents() {
        coEvery { calendarsRepository.getEventsByUid(userId, eventUid) } returns ApiResponse.Success(
            mockk<EventsByUidApiResponse>(relaxed = true) { every { events } returns emptyList() }
        )
    }

    private fun stubCanonicalEmailsUseCaseUsingRealCanonicalization() {
        coEvery { canonicalEmailsUseCase.invoke(userId, any()) } answers {
            val emails = arg<List<String>>(1)
            emails.associateWith { e ->
                ProtonUtilsImpl.canonicalizeProtonEmail(e, forceCanonicalization = true)
                    .takeIf { it.isNotBlank() }
            }
        }
    }

    @Test
    fun `party-crasher when proton organizer sends to non-user BYOE recipient`() = runTest {
        // Given: user has a Proton address
        mockAddresses(protonAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()
        val externalOrganizer = "organizer@proton.me"

        // When: Proton organizer sends an invite
        val res = sut.execute(
            iCalString = icsRequest(organizer = externalOrganizer),
            userId = userId,
            senderEmail = senderAddress,
            recipientEmail = gmailAddress,
        )

        // Then: party crasher
        assertTrue(res is IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher)
        coVerify(exactly = 0) {
            editCreateEventUseCase.execute(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `party-crasher when proton organizer sends to user's BYOE recipient`() = runTest {
        // Given: user has a Proton + BYOE address
        mockAddresses(protonAddress, gmailAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()
        val protonOrganizer = "organizer@proton.me"

        // When: Proton organizer sends an invite
        val res = sut.execute(
            iCalString = icsRequest(organizer = protonOrganizer),
            userId = userId,
            senderEmail = protonOrganizer,
            recipientEmail = gmailAddress,
        )

        // Then: party crasher
        assertTrue(res is IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher)
        coVerify(exactly = 0) {
            editCreateEventUseCase.execute(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `no party-crasher when external organizer sends to user's BYOE recipient`() = runTest {
        // Given: user has a Proton + BYOE address
        mockAddresses(protonAddress, gmailAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()
        val externalOrganizer = "organizer@external.org"

        // When: Proton organizer sends an invite
        val eventSlot = slot<Event>()
        coEvery {
            editCreateEventUseCase.execute(
                userId = userId,
                newEvent = capture(eventSlot),
                oldCalendarId = any(),
                createLinkedEventAsAttendee = any(),
                sendPreferences = any(),
                isImport = any()
            )
        } returns UseCase.Result.Success(listOf("evt-1"))

        val res = sut.execute(
            iCalString = icsRequest(organizer = externalOrganizer),
            userId = userId,
            senderEmail = externalOrganizer,
            recipientEmail = gmailAddress,
        )

        // Then: party crasher
        assertIs<IcsSurgeryUtils.HandleIcsResult.Success>(res)
        assertTrue(res.action == IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT)

        val ev = eventSlot.captured.iCalendar.events.first()

        val attendeeCanon = ev.attendees.mapNotNull { it.extractEmail() }
            .map { ProtonUtilsImpl.canonicalizeProtonEmail(it, true).lowercase() }

        val expectedCanon = ProtonUtilsImpl
            .canonicalizeProtonEmail(protonAddress, true)
            .lowercase()

        assertTrue(expectedCanon in attendeeCanon, "attendees=$attendeeCanon")

        val protonAttendee = ev.attendees.first {
            ProtonUtilsImpl.canonicalizeProtonEmail(it.extractEmail().orEmpty(), true)
                .equals(expectedCanon, true)
        }
        assertEquals(ParticipationStatus.NEEDS_ACTION, protonAttendee.participationStatus)
    }

    @Test
    fun `injects protonEmail when invite was forwarded to BYOE and creates event`() = runTest {
        // Given: user's gmail received the invite
        mockAddresses(protonAddress, gmailAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()

        val eventSlot = slot<Event>()
        coEvery {
            editCreateEventUseCase.execute(
                userId = userId,
                newEvent = capture(eventSlot),
                oldCalendarId = any(),
                createLinkedEventAsAttendee = any(),
                sendPreferences = any(),
                isImport = any()
            )
        } returns UseCase.Result.Success(listOf("evt-1"))

        // When
        val res = sut.execute(
            iCalString = icsRequest(),
            userId = userId,
            senderEmail = senderAddress,
            recipientEmail = gmailAddress,
        )

        // Then
        assertIs<IcsSurgeryUtils.HandleIcsResult.Success>(res)
        assertTrue(res.action == IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT)

        val ev = eventSlot.captured.iCalendar.events.first()

        val attendeeCanon = ev.attendees.mapNotNull { it.extractEmail() }
            .map { ProtonUtilsImpl.canonicalizeProtonEmail(it, true).lowercase() }

        val expectedCanon = ProtonUtilsImpl
            .canonicalizeProtonEmail(protonAddress, true)
            .lowercase()

        assertTrue(expectedCanon in attendeeCanon, "attendees=$attendeeCanon")

        val protonAttendee = ev.attendees.first {
            ProtonUtilsImpl.canonicalizeProtonEmail(it.extractEmail().orEmpty(), true)
                .equals(expectedCanon, true)
        }
        assertEquals(ParticipationStatus.NEEDS_ACTION, protonAttendee.participationStatus)
    }

    @Test
    fun `picks Proton address for reply`() = runTest {
        // Given: gmail first, Proton second
        mockAddresses(gmailAddress, protonAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()

        val evt = slot<Event>()
        coEvery {
            editCreateEventUseCase.execute(
                userId = userId,
                newEvent = capture(evt),
                oldCalendarId = any(),
                createLinkedEventAsAttendee = any(),
                sendPreferences = any(),
                isImport = any()
            )
        } returns UseCase.Result.Success(listOf("evt-x"))

        val res = sut.execute(icsRequest(), userId, senderAddress, gmailAddress)
        assertIs<IcsSurgeryUtils.HandleIcsResult.Success>(res)

        val attendeesCanon = evt.captured.iCalendar.events.first().attendees
            .mapNotNull { it.extractEmail() }
            .map { ProtonUtilsImpl.canonicalizeProtonEmail(it, true) }

        val protonCanon = ProtonUtilsImpl.canonicalizeProtonEmail(protonAddress, true)
        assertTrue(protonCanon in attendeesCanon, "attendees=$attendeesCanon")
    }

    @Test
    fun `does not duplicate P2 if already present`() = runTest {
        // Given: user has Proton and a BYOE address
        mockAddresses(protonAddress, gmailAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()

        val evtArg = slot<Event>()
        coEvery {
            editCreateEventUseCase.execute(
                userId = userId,
                newEvent = capture(evtArg),
                oldCalendarId = any(),
                createLinkedEventAsAttendee = any(),
                sendPreferences = any(),
                isImport = any()
            )
        } returns UseCase.Result.Success(listOf("evt-2"))

        val ics = icsRequest().replace(
            "ATTENDEE;CN=U1:mailto:u1@proton.me",
            "ATTENDEE;CN=U1:mailto:u1@proton.me\r\nATTENDEE;CN=P2:mailto:$protonAddress"
        )

        val res = sut.execute(ics, userId, senderAddress, gmailAddress)

        assertIs<IcsSurgeryUtils.HandleIcsResult.Success>(res)
        val p2Count = evtArg.captured.iCalendar.events.first().attendees.count { it.email.equals(protonAddress, true) }
        assertEquals(1, p2Count)
    }

    @Test
    fun `party-crasher if recipient does not match any of the user's emails`() = runTest {
        // Given: user has no addresses that match recipient
        val recipientEmail = "someone-else@gmail.com"
        mockAddresses(protonAddress)
        stubDefaultCalendar()
        stubNoExistingEvents()
        stubCanonicalEmailsUseCaseUsingRealCanonicalization()

        // When
        val res = sut.execute(
            iCalString = icsRequest(),
            userId = userId,
            senderEmail = senderAddress,
            recipientEmail = recipientEmail,
        )

        // Then
        assertTrue(res is IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher)
        coVerify(exactly = 0) { editCreateEventUseCase.execute(any(), any(), any(), any(), any(), any()) }
    }

    private fun icsRequest(
        uid: String = this.eventUid,
        organizer: String = "o@external.com",
        attendee: String = "u1@proton.me",
        dt: String = "20250101T100000Z"
    ) = """
        BEGIN:VCALENDAR
        PRODID:-//test//ical//EN
        VERSION:2.0
        METHOD:REQUEST
        BEGIN:VEVENT
        UID:$uid
        DTSTAMP:20241201T120000Z
        DTSTART:$dt
        SUMMARY:test
        ORGANIZER:mailto:$organizer
        ATTENDEE;CN=U1:mailto:$attendee
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
