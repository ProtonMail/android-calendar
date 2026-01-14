package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmails
import org.junit.jupiter.api.Test

internal class AndroidUtilsTest {

    @Test
    fun `canonicalize proton emails`() {
        val emails = arrayListOf<String>()
        emails.add("Test.address-1_2+group@protonmail.com")
        emails.add("Test.address-1_2+group@customdomain.com")
        val canonicalEmails = canonicalizeProtonEmails(emails)
        assertThat(canonicalEmails["Test.address-1_2+group@protonmail.com"]).isEqualTo("testaddress12@protonmail.com")
        assertThat(canonicalEmails["Test.address-1_2+group@customdomain.com"]).isEqualTo("test.address-1_2+group@customdomain.com")
    }
}
