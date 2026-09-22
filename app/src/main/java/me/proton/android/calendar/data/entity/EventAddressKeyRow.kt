package me.proton.android.calendar.data.entity

data class EventAddressKeyRow(
    val id: String,
    val calendarId: String,
    val addressId: String?,
    val hasAddressKeyPacket: Boolean,
) {

    // we can't decrypt an event that was locked with an address key we don't have, mirrors TransformEventUseCase
    fun isUnreadableBy(ownAddressIds: Set<String>) = hasAddressKeyPacket && addressId !in ownAddressIds
}
