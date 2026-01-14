package me.proton.android.calendar.common

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class KotlinUtilsImplTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun debounceWithZeroSeconds() = runTest(testDispatcher) {
        val results = mutableListOf<Int>()
        val flow = flowOf(1, 2, 3).debounceExceptFirst(0.milliseconds)

        val job = launch {
            flow.collect { results.add(it) }
        }

        advanceTimeBy(2)
        assertEquals(listOf(1, 2, 3), results)

        job.cancel()
    }

    @Test
    fun singleEmission() = runTest(testDispatcher) {
        val results = mutableListOf<String>()
        val flow = flowOf("single item").debounceExceptFirst(100.milliseconds)

        flow.collect { results.add(it) }

        assertEquals(listOf("single item"), results)
    }

    @Test
    fun debounceAfterFirst() = runTest(testDispatcher) {
        val results = mutableListOf<Int>()
        val flow = flow {
            emit(1)
            delay(10)
            emit(2)
            delay(10)
            emit(3)
        }.debounceExceptFirst(50.milliseconds)

        val job = launch {
            flow.collect { results.add(it) }
        }

        // only "1" should be received
        advanceTimeBy(2)
        assertEquals(listOf(1), results)

        // "2" should be skipped
        advanceTimeBy(52)
        assertEquals(listOf(1, 3), results)

        job.cancel()
    }

    @Test
    fun multipleEmissionsWithGaps() = runTest(testDispatcher) {
        val results = mutableListOf<Int>()
        val flow = flow {
            emit(1)
            delay(100)
            emit(2)
            delay(100)
            emit(3)
        }.debounceExceptFirst(50.milliseconds)

        val job = launch {
            flow.collect { results.add(it) }
        }

        // all items should be there
        advanceTimeBy(250)
        assertEquals(listOf(1, 2, 3), results)

        job.cancel()
    }

}