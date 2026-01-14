package me.proton.android.calendar.domain.model

// TODO fix kotlin package in core and remove this class
enum class PackageType(val type: Int) {
    ProtonMail(1),
    EncryptedOutside(2),
    Cleartext(4),
    PgpInline(8),
    PgpMime(16), // encrypted and signed
    ClearMime(32); // signed

    companion object {
        fun fromScheme(scheme: String, encrypt: Boolean, sign: Boolean): PackageType? =
            if (scheme == "pgp-mime") {
                if (!encrypt && sign) {
                    ClearMime
                } else if (encrypt) {
                    PgpMime
                } else Cleartext
            } else if (scheme == "pgp-inline") {
                if (encrypt) {
                    PgpInline
                } else Cleartext
            } else null
    }
}
