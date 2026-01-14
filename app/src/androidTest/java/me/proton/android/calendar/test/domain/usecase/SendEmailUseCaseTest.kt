package me.proton.android.calendar.test.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import me.proton.android.calendar.domain.usecase.SendEmailDirect
import me.proton.android.calendar.domain.usecase.SendEmailUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpgradeEventUseCase
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.user.domain.UserAddressManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SendEmailUseCaseTest {

    private val logger = TestsLogger
    private val sendEmailDirectMockUseCase: SendEmailDirect = mockk()
    private val userAddressManagerMock: UserAddressManager = mockk()
    private val json = Json { this.ignoreUnknownKeys = true }
    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val valuesStoreProviderMock: ValueStoreProvider = mockk()
    private val cryptoMock: Crypto = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val resourceProviderMock: ResourceProvider = mockk()
    private val cryptoContextMock: CryptoContext = mockk()
    private val upgradeEventUseCaseMock: UpgradeEventUseCase = mockk()

    private val sendEmailUseCase = SendEmailUseCase(
        logger,
        sendEmailDirectUseCase = sendEmailDirectMockUseCase,
        userAddressManager = userAddressManagerMock,
        json = json,
        transformEventUseCase = transformEventUseCaseMock,
        calendarsRepository = calendarsRepositoryMock,
        valueStoreProvider = valuesStoreProviderMock,
        crypto = cryptoMock,
        editCreateEventUseCase = editCreateEventUseCaseMock,
        resourceProvider = resourceProviderMock,
        cryptoContext = cryptoContextMock,
        upgradeEventUseCase = upgradeEventUseCaseMock
    )

    @Before
    fun beforeEach() {
        clearAllMocks()
    }

    @Test
    fun test_getInviteMailBody_with_all_day_event_that_spans_single_day_and_actual_end_date_is_false() {
        val event = Event.from(
            id = "id",
            calendar = Calendar(
                id = "id",
                name = "name",
                email = "email",
                ownerEmail = "ownerEmail",
                description = "description",
                color = "color",
                priority = 0,
                addressId = "addressId",
                memberId = "memberId",
                flags = 1,
                display = true,
                type = 0,
                permissions = 127,
                defaultEventDuration = 30,
                emptyList(),
                emptyList()
            ),
            iCalendar = calendarAllDaySingleDayActualEndDate!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true

        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)

        val expectedResult = "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_multiple,
                formattedDateStart.first,
                formattedDateEnd.first
            )
        } returns expectedResult

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        } returns "Test Email Body"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains("Test Email Body"))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_multiple,
                formattedDateStart.first,
                formattedDateEnd.first
            )
        }

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        }
    }

    @Test
    fun test_getInviteMailBody_with_all_day_event_that_spans_single_day_and_actual_end_date_is_true() {
        val event = Event.from(
            id = "id",
            calendar = Calendar(
                id = "id",
                name = "name",
                email = "email",
                ownerEmail = "ownerEmail",
                description = "description",
                color = "color",
                priority = 0,
                addressId = "addressId",
                memberId = "memberId",
                flags = 1,
                display = true,
                type = 0,
                permissions = 127,
                defaultEventDuration = 30,
                emptyList(),
                emptyList()
            ),
            iCalendar = calendarAllDaySingleDay!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true

        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)

        val expectedResult = "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first,
            )
        } returns expectedResult

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        } returns "Test Email Body"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains("Test Email Body"))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first,
            )
        }

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        }
    }

    val calendarAllDaySingleDay = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200403
    SUMMARY:All-day event on 2nd April
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDayActualEndDate = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200402
    SUMMARY:All-day event on 2nd April, actual end date
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())
}