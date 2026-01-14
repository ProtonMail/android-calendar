package me.proton.android.calendar.domain.model

data class SendPreferences(
        val encrypt: Boolean,
        val sign: Boolean,
        val pgpScheme: PackageType,
        val mimeType: String, // 'text/html' | 'text/plain' | 'multipart/mixed'
        val publicKey: String?
)
