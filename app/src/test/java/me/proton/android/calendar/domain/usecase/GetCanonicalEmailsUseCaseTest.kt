package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CanonicalEmailsApiResponse
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetCanonicalEmailsUseCaseTest {

    private val logger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }
    private val userId = UserId("test-user-id")

    private val addressesApi: AddressesApi = mockk()

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
    }

    @Test
    fun `handle correct response`() {
        runBlocking {

            val emails = listOf("aDamtSt+1@protonmail.com", "adamTEST@domain.com")

            val canonicalResponse = json.decodeFromString<CanonicalEmailsApiResponse>(
                """
                {"Code":1001,"Responses":[{"Email":"aDamtSt+1@protonmail.com",
                "Response":{"Code":1000,"CanonicalEmail":"adamtst@protonmail.com"}},
                {"Email":"adamTEST@domain.com","Response":{"Code":1000,"CanonicalEmail":"adamtest@domain.com"}}]}
                """.trimIndent()
            )

            coEvery { addressesApi.getCanonicalEmails(userId, emails) } returns ApiResponse.Success(canonicalResponse)

            val sut = GetCanonicalEmailsUseCase(addressesApi, logger)

            val result = sut.invoke(userId, emails)

            assertThat(result["aDamtSt+1@protonmail.com"]).isEqualTo("adamtst@protonmail.com")
            assertThat(result["adamTEST@domain.com"]).isEqualTo("adamtest@domain.com")

        }
    }

    @Test
    fun `handle error response`() {
        runBlocking {

            val emails = listOf("adamTEST@domain.com")

            coEvery { addressesApi.getCanonicalEmails(userId, emails) } returns ApiResponse.Error(
                500,
                0,
                "internal server error",
                true
            )

            val sut = GetCanonicalEmailsUseCase(addressesApi, logger)

            val result = sut.invoke(userId, emails)

            assertThat(result.containsKey("adamTEST@domain.com")).isFalse()
            assertThat(result.size).isEqualTo(0)

        }
    }

    @Test
    fun `handle malformed email`() {
        runBlocking {

            val emails = listOf("not-an-email")

            val canonicalResponse = json.decodeFromString<CanonicalEmailsApiResponse>(
                """
                {"Code":1001,"Responses":[{"Email":"not-an-email","Response":{"Code":2050,
                "Error":"The email address is malformed","ErrorDescription":"","Details":{}}}]}
                """.trimIndent()
            )

            coEvery { addressesApi.getCanonicalEmails(userId, emails) } returns ApiResponse.Success(canonicalResponse)

            val sut = GetCanonicalEmailsUseCase(addressesApi, logger)

            val result = sut.invoke(userId, emails)

            assertThat(result.containsKey("not-an-email"))
            assertThat(result["not-an-email"]).isNull()

        }
    }

}
