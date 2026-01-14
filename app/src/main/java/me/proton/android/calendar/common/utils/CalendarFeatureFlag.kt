package me.proton.android.calendar.common.utils

import me.proton.core.featureflag.domain.entity.FeatureId

enum class CalendarFeatureFlag(val featureId: FeatureId, val fallbackValue: Boolean, val isLocalFlag: Boolean) {

    /** Remote flags **/
    // Proton Admin panel
    RatingAndroidCalendar(FeatureId("RatingAndroidCalendar"), false, false),
    CalendarAndroidServerDownBanner(FeatureId("CalendarAndroidServerDownBanner"), false, false),

    // Unleash
    ColorPerEventAndroid(FeatureId("ColorPerEventAndroid"), false, false),
    EventSearchAndroid(FeatureId("EventSearchAndroid"), false, false),
    SplitViewVerticalScrollingAndroid(FeatureId("SplitViewVerticalScrollingAndroid"), false, false),
    FetchedEventsCacheAndroid(FeatureId("FetchedEventsCacheAndroid"), false, false),
    ZoomIntegrationAndroid(FeatureId("ZoomIntegrationAndroid"), false, false),
    ProtonMeet(FeatureId("MeetIntegrationAndroid"), false, false),
    RsvpCommentsAndroid(FeatureId("RsvpCommentsAndroid"), false, false),
    NewCalendarEventListenerAndroid(FeatureId("NewCalendarEventListenerAndroid"), false, false),
    RefreshButton(FeatureId("CalendarAndroidRefreshButton"), false, false),
    RefreshDeletions(FeatureId("CalendarAndroidRefreshDeletions"), false, false),

    /** Local only flag (unknown to remote API) **/
    // Enabled
    AddAttendees(FeatureId("AddAttendees"), true, true),
    ChangeAnswer(FeatureId("ChangeAnswer"), true, true),
    OpenInvitation(FeatureId("OpenInvitation"), true, true),
    AppLinks(FeatureId("AppLinks"), true, true),
    DeleteCalendar(FeatureId("DeleteCalendar"), true, true),
    ChangeCalendarSimpleEvent(FeatureId("ChangeCalendarSimpleEvent"), true, true),
    UseEventDecryptor(FeatureId("UseEventDecryptor"), true, true),
    ChangeLanguage(FeatureId("ChangeLanguage"), true, true),
    MonthView(FeatureId("MonthView"), true, true),
    Feedback(FeatureId("Feedback"), true, true),
    AutoInvitesSetting(FeatureId("AutoInvitesSetting"), true, true),
    ImportAssistant(FeatureId("ImportAssistant"), true, true),
    ThreeDaysView(FeatureId("ThreeDaysView"), true, true),
    WeekView(FeatureId("WeekView"), true, true),
    ImportIcs(FeatureId("ImportIcs"), true, true),
    EditingSharedCalendars(FeatureId("EditingSharedCalendars"), true, true),
    EditAttendeesAsOrganizer(FeatureId("EditAttendeesAsOrganizer"), true, true),
    EditInvitationAsOrganizer(FeatureId("EditInvitationAsOrganizer"), true, true),

    // Disabled
    ShowSignatureVerificationBadges(FeatureId("ShowSignatureVerificationBadges"), false, true),
    DragAndDrop(FeatureId("DragAndDrop"), false, true),
    ClearCalendar(FeatureId("ClearCalendar"), false, true),
    Payments(FeatureId("Subscription"), false, true)
}

object CalendarUnleashVariants {
    val FetchedEventsValidityMin = FeatureId("CalendarAndroidFetchedWindowValidityMin")
}
