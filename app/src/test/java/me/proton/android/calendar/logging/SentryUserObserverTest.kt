package me.proton.android.calendar.logging

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.protocol.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.android.calendar.common.logger.SentryUtils
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.test.kotlin.TestCoroutineScopeProvider
import me.proton.core.test.kotlin.TestDispatcherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SentryUserObserverTest {

    private val userId = UserId("user-id")

    private val accountManager = mockk<AccountManager> {
        coEvery { this@mockk.getPrimaryUserId() } returns flowOf(userId)
    }

    private val sharedPrefsProvider = mockk<DefaultSharedPreferencesProvider>()

    private lateinit var sentryUserObserver: SentryUserObserver

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(TestDispatcherProvider().Main)
        mockkStatic(Sentry::class)
        mockkStatic(SentryUtils::class)
        sentryUserObserver = SentryUserObserver(
            scopeProvider = TestCoroutineScopeProvider(),
            accountManager = accountManager,
            defaultSharedPreferencesProvider = sharedPrefsProvider
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `register userId in Sentry for valid primary account`() = runTest {
        // When
        sentryUserObserver.start().join()

        // Then
        val sentryUserSlot = slot<User>()
        verify { Sentry.setUser(capture(sentryUserSlot)) }
        assertEquals(userId.id, sentryUserSlot.captured.id)
    }

    @Test
    fun `register random UUID in Sentry when no primary account available`() = runTest {
        // Given
        every { accountManager.getPrimaryUserId() } returns flowOf(null)
        every {
            SentryUtils.getInstallationId(sharedPrefsProvider.sharedPreferences)
        } returns UUID.randomUUID().toString()

        // When
        sentryUserObserver.start().join()

        // Then
        val sentryUserSlot = slot<User>()
        verify { Sentry.setUser(capture(sentryUserSlot)) }
        val actual = UUID.fromString(sentryUserSlot.captured.id)
        assertTrue(actual.toString().isNotBlank())
    }
}
