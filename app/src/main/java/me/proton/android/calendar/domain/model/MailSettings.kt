package me.proton.android.calendar.domain.model

data class MailSettings(
    /**
     * whether to sign outgoing messages or not
     */
    val sign: Boolean,
    /**
     * default PGP Scheme
     */
    val pgpScheme: PackageType,
    val autoSaveContacts: Boolean,
    /**
     * default MIME type of outgoing messages
     */
    val draftMimeType: String // 'text/plain' or 'text/html'
)
