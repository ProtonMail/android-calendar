package me.proton.android.calendar.domain.usecase

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.worker.SyncAlarmsWorker
import me.proton.android.calendar.test.shared.mocks.userId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class ScheduleSyncAlarmsUseCaseTest {

    private val workManagerMock: WorkManager = mockk()
    private val testsLogger = TestsLogger

    private val workPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE

    private fun getScheduleSyncAlarmsUseCase(): ScheduleSyncAlarmsUseCase {
        return ScheduleSyncAlarmsUseCase(
            testsLogger,
            workManagerMock
        )
    }

    private fun workName(userId: String) = "SYNC_ALARMS_$userId"

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true
    }

    @Test
    fun `ScheduleSyncAlarms with no params applies default values`() = runBlocking {
        val expectedDelay = Duration.ofSeconds(0)
        val expectedForce = false
        val expectedUserId = userId

        every {
            workManagerMock.enqueueUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>()
            )
        } returns mockk(relaxed = true)

        getScheduleSyncAlarmsUseCase().execute(
            userId = userId
        )

        val workRequestSlot = CapturingSlot<OneTimeWorkRequest>()
        coVerify(exactly = 1) {
            workManagerMock.enqueueUniqueWork(
                "SYNC_ALARMS_${expectedUserId.id}",
                workPolicy,
                capture(workRequestSlot)
            )
        }

        assertEquals(expectedUserId.id, workRequestSlot.captured.workSpec.input.getString(SyncAlarmsWorker.INPUT_USER_ID))
        assertEquals(
            expectedForce,
            workRequestSlot.captured.workSpec.input.getBoolean(SyncAlarmsWorker.INPUT_FORCE_SYNC_ALARMS, !expectedForce)
        )
        assertEquals(expectedDelay.toMillis(), workRequestSlot.captured.workSpec.initialDelay)
    }

    @Test
    fun `ScheduleSyncAlarms applies passed values`() = runBlocking {
        val expectedDelay = Duration.ofSeconds(10)
        val expectedForce = true
        val expectedUserId = userId

        every {
            workManagerMock.enqueueUniqueWork(
                any(),
                any(),
                any<OneTimeWorkRequest>()
            )
        } returns mockk(relaxed = true)

        getScheduleSyncAlarmsUseCase().execute(
            userId = userId,
            initialDelay = expectedDelay,
            force = expectedForce
        )

        val workRequestSlot = CapturingSlot<OneTimeWorkRequest>()
        coVerify(exactly = 1) {
            workManagerMock.enqueueUniqueWork(
                workName(userId.id),
                workPolicy,
                capture(workRequestSlot)
            )
        }

        assertEquals(expectedUserId.id, workRequestSlot.captured.workSpec.input.getString(SyncAlarmsWorker.INPUT_USER_ID))
        assertEquals(
            expectedForce,
            workRequestSlot.captured.workSpec.input.getBoolean(SyncAlarmsWorker.INPUT_FORCE_SYNC_ALARMS, !expectedForce)
        )
        assertEquals(expectedDelay.toMillis(), workRequestSlot.captured.workSpec.initialDelay)
    }

}