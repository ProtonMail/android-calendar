package me.proton.android.calendar.logging

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.logger.SentryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SentryUtilsTest {
    private val sharedPreferences = mockk<SharedPreferences>()

    @Test
    fun `should return the installation id, if existing`() {
        // Given
        val expectedUUID = UUID.randomUUID().toString()
        every {
            sharedPreferences.getString(SharedPreferencesKeys.APP_INSTALLATION_ID, null)
        } returns expectedUUID

        // When
        val actual = SentryUtils.getInstallationId(sharedPreferences)

        // Then
        assertEquals(expectedUUID,actual)
        verify(exactly = 0) { sharedPreferences.edit() }
    }

    @Test
    fun `should return a new installation id and store it, if not existing`() {
        // Given
        every {
            sharedPreferences.getString(SharedPreferencesKeys.APP_INSTALLATION_ID, null)
        } returns null

        val editor = mockk<SharedPreferences.Editor>()
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(SharedPreferencesKeys.APP_INSTALLATION_ID, any()) } returns editor
        every { editor.commit() } returns true

        // When
        SentryUtils.getInstallationId(sharedPreferences)

        // Then
        val stringSlot = slot<String>()
        verify { editor.putString(SharedPreferencesKeys.APP_INSTALLATION_ID, capture(stringSlot)) }
        val actual = UUID.fromString(stringSlot.captured)
        assertTrue(actual.toString().isNotBlank())
    }
}
