package me.proton.android.calendar.uitest.robot

import me.proton.android.calendar.R
import me.proton.android.calendar.uitest.extension.hasChildren
import me.proton.test.fusion.Fusion.view
import kotlin.time.Duration.Companion.seconds

object AgendaRobot : Robot {
    private val agendaRecyclerView = view.withId(R.id.rv_agenda)
    private val textHeader = view.withId(R.id.text_header)
    private val miniCalendarLayout = view.withId(R.id.miniCalendarLayout)

    fun eventIsDisplayed(title: String, count: Int? = null) =
        agendaRecyclerView
            .hasChildren(view.withText(title), count)
            .await(90.seconds) { checkIsDisplayed() }

    fun robotDisplayed() = let {
        agendaRecyclerView.hasChildren(textHeader).await(90.seconds) { checkIsDisplayed() }
        miniCalendarLayout.checkIsDisplayed()
    }
}
