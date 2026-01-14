package me.proton.android.calendar.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.EventDecryptorImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class EventDecryptorImplTest {

    private val transform: TransformEventUseCase = mockk()
    private val db: AppDatabase = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decrypt caches, decryptAllowingApiCall bypasses, clearCache resets`() = runTest {
        val cal = calendar("cal-1")
        val e1 = event(cal)
        val e2 = event(cal)
        val e3 = event(cal)
        val entity = entity("ev-1", "cal-1", 42L)

        // Given this sequence of transform results
        coEvery { transform.execute(any(), allowApiCall = false) } returns e1 andThen e3
        coEvery { transform.execute(any(), allowApiCall = true) } returns e2

        val sut = EventDecryptorImpl(transform, db, json)
        sut.setCalendars(listOf(cal))

        // 1st decrypt: uses transform, caches e1
        val r1 = sut.decrypt(entity)
        assertSame(e1, r1)
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = false) }

        // 2nd decrypt: cache hit, no new transform
        val r2 = sut.decrypt(entity)
        assertSame(e1, r2)
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = false) }

        // decryptAllowingApiCall: bypass cache, update with e2
        val r3 = sut.decryptAllowingApiCall(entity)
        assertSame(e2, r3)
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = true) }

        // 4th decrypt: hit updated cache (still no transform)
        val r4 = sut.decrypt(entity)
        assertSame(e2, r4)
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = false) } // still 1

        // clearCache: next decrypt recomputes (returns e3)
        sut.clearCache()
        val r5 = sut.decrypt(entity)
        assertNotNull(r5)
        assertSame(e3, r5)
        coVerify(exactly = 2) { transform.execute(entity, allowApiCall = false) }

        confirmVerified(transform)
    }

    @Test
    fun `concurrent decrypt calls return the same cached instance`() = runTest {
        val cal = calendar("cal-1")
        val entity = entity("ev-1", "cal-1", 42L)
        val n = 8

        // distinct events so we can see which one got returned
        val candidates = List(n) { event(cal) }

        // to make all transforms start together
        val started = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val idx = AtomicInteger(0)

        coEvery { transform.execute(any(), allowApiCall = false) } coAnswers {
            if (started.incrementAndGet() == n) release.complete(Unit)
            release.await()
            candidates[idx.getAndIncrement()]
        }

        val sut = EventDecryptorImpl(transform, db, json)
        sut.setCalendars(listOf(cal))

        // should return the same cached instance (the first one )
        val results = (1..n).map { async { sut.decrypt(entity) } }.awaitAll()
        assertEquals(1, results.toSet().size)
        val canonical = results.first()
        assertSame(canonical, sut.getFromCache(entity.id, entity.calendarId, entity.modifyTime))
        coVerify(exactly = n) { transform.execute(entity, allowApiCall = false) }
        confirmVerified(transform)
    }

    @Test
    fun `decryptAllowingApiCall null does not overwrite cache`() = runTest {
        val cal = calendar("cal-1")
        val entity = entity("ev-1", "cal-1", 42L)
        val cached = event(cal)

        coEvery { transform.execute(entity, allowApiCall = false) } returns cached
        coEvery { transform.execute(entity, allowApiCall = true) } returns null

        val sut = EventDecryptorImpl(transform, db, json)
        sut.setCalendars(listOf(cal))

        // make initial decrypt to prepare the cache
        assertSame(cached, sut.decrypt(entity))

        // api call decrypt returns null, must not overwrite cache
        assertEquals(null, sut.decryptAllowingApiCall(entity))

        // cache still returns the original
        assertSame(cached, sut.decrypt(entity))

        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = false) }
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = true) }
        confirmVerified(transform)
    }

    @Test
    fun `getFromCache returns latest value after api call update`() = runTest {
        val cal = calendar("cal-1")
        val entity = entity("ev-1", "cal-1", 42L)
        val first = event(cal)
        val updated = event(cal)

        coEvery { transform.execute(entity, allowApiCall = false) } returns first
        coEvery { transform.execute(entity, allowApiCall = true) } returns updated

        val sut = EventDecryptorImpl(transform, db, json)
        sut.setCalendars(listOf(cal))

        // make initial decrypt to prepare the cache
        assertSame(first, sut.decrypt(entity))

        // now api call updates the cache
        assertSame(updated, sut.decryptAllowingApiCall(entity))

        // getFromCache should return the updated one
        assertSame(
            updated,
            sut.getFromCache(entity.id, entity.calendarId, entity.modifyTime)
        )

        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = false) }
        coVerify(exactly = 1) { transform.execute(entity, allowApiCall = true) }
        confirmVerified(transform)
    }

    private fun calendar(id: String) = mockk<Calendar> {
        every { this@mockk.id } returns id
    }

    private fun event(withCal: Calendar) = mockk<Event> {
        every { this@mockk.calendar } returns withCal
    }

    private fun entity(id: String, calId: String, modify: Long) = mockk<EventEntity> {
        every { this@mockk.id } returns id
        every { this@mockk.calendarId } returns calId
        every { this@mockk.modifyTime } returns modify
    }
}
