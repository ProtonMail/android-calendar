package me.proton.android.calendar.test.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import me.proton.android.calendar.common.CryptoImpl
import me.proton.android.calendar.common.logger.TestsLogger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class CryptoImplTest {

    private val crypto = CryptoImpl(TestsLogger)

    @Test
    fun salted_user_passphrase_is_generated_correctly() {
        val userPassphrase =
            crypto.generateUserPassphrase("123".toByteArray(), "QDaXk39fSJY9ZHJQKrj8yA==")
        assertEquals("7NgO4d0h72zt4XuFLOUbg352vhrn.tu", String(userPassphrase))
    }

    @Test
    fun check_passphrase_for_a_key() {
        assertTrue(
            crypto.checkPassphrase(
                privateKey,
                "7NgO4d0h72zt4XuFLOUbg352vhrn.tu".toByteArray()
            )
        )
        assertFalse(crypto.checkPassphrase(privateKey, "incorrect passphrase".toByteArray()))
    }

    @Test
    fun encrypt_text_with_keypacket() {

        val plainText = "Proton"
        val encodedKeyPacket = "wV4DP+KI9zrxP8QSAQdAiDyH8M+RGVtpZSOXJUvV+EpRvLk8PdmolbxMX83MYFowMr3hUsQyNQzUZt7SzgDzWpA9+hd3eUp6gfUIfDHH6xreSRa6ALfhVKkEjuhGxrtv"
        val sessionKey = crypto.decryptSessionKey(encodedKeyPacket, listOf(calendarPrivateKey), calendarPrivateKeyPassphrase.toByteArray())!!

        val encrypted = crypto.encryptText(plainText, sessionKey)

        assertNotNull(encrypted)
    }

    @Test
    fun decrypt_session_key() {
        val encodedKeyPacket = "wV4DP+KI9zrxP8QSAQdAiDyH8M+RGVtpZSOXJUvV+EpRvLk8PdmolbxMX83MYFowMr3hUsQyNQzUZt7SzgDzWpA9+hd3eUp6gfUIfDHH6xreSRa6ALfhVKkEjuhGxrtv"

        assertNotNull(crypto.decryptSessionKey(encodedKeyPacket, listOf(calendarPrivateKey), calendarPrivateKeyPassphrase.toByteArray()))
    }

    val calendarPrivateKey = """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: ProtonMail

        xYYEXkqt7xYJKwYBBAHaRw8BAQdA8Bpv0uvXzbDe16Y4QPWWv/4WItKxVrS6
        IkTkqSk0A9T+CQMIsQeIfS3SNcdgNtjZQxirGLGKRRN1D5FU6W+tqHc7bLVu
        A2Us8ui0iTffZAiMZU7Y4ogksyZqr5XEfvr2PmgenkaLuM17ls2cZbjRZ9Id
        cc0MQ2FsZW5kYXIga2V5wngEEBYKACAFAl5Kre8GCwkHCAMCBBUICgIEFgIB
        AAIZAQIbAwIeAQAKCRBfdh714DIHPLGmAP4t+H/WYAJ0LH4ifeOkxEfcdH9d
        F4YaIvlPgOxS+0hcoAD/a379Gwu067TQUMMmZsX3yI7NR7H7KJ2RKcbIwADy
        FgDHiwReSq3vEgorBgEEAZdVAQUBAQdAtKc2+1uULJFFH+YIgbfSsbVttX8k
        4n8+Mti6OYEf6HMDAQgH/gkDCNo/D7eNisONYDItg79Y5LiTrNhlJMumDdQL
        mTvhnd85RHxAZiiRjMrnbDiCfZNi+4/JVeu3hArCYQNWHb9+XAizpsMuqP2J
        zdsV57M20XPCYQQYFggACQUCXkqt7wIbDAAKCRBfdh714DIHPClCAP4/L6EM
        NuC6z7/lBZjczr+zDf6W3d2qxPiNhOLAdmDH/wD+KZaqtTN2/Nh00CmOxEXb
        COe4F9qbz3m5vDy8ZhYTmAY=
        =zCfh
        -----END PGP PRIVATE KEY BLOCK-----
    """.trimIndent()

    val calendarPrivateKeyPassphrase = "NDQy8eVB/+qQMJagmkDR2iQOsiHSI3k8XummTgguRWI="

    val privateKey =
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: ProtonMail\n\n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\ndlEt7f8XNvX3HxQw9w==\n=FW0u\n-----END PGP PRIVATE KEY BLOCK-----\n"

}
