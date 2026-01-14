package me.proton.android.calendar.presentation.calendar.pagerAdapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import me.proton.android.calendar.presentation.calendar.fragment.ItemCalendarAgendaFragment
import java.time.LocalDate

class AgendaPagerAdapter(
    activity: FragmentActivity,
    val startingDate: LocalDate
) : FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        return ItemCalendarAgendaFragment.newInstance(
            position,
            startingDate.plusDays((position - startingPosition).toLong())
        )
    }
}
