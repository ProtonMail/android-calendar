package me.proton.android.calendar.domain.model

data class ImportCalendarMapping(
    var importCalendar: Boolean,

    // Source
    val sourceId: String,
    val sourceName: String,
    val sourceEmail: String,
    val sourceDescription: String,

    // Destination
    var createDestinationCalendar: Boolean,
    var destinationId: String?, // Value is null when calendar hasn't been created yet
    var destinationName: String,
    var destinationEmail: String,
    val destinationDescription: String,
    var destinationColor: String
) {
    val mergeCalendar: Boolean get() = !createDestinationCalendar
}
