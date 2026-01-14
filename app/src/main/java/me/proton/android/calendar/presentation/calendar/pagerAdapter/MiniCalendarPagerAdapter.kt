package me.proton.android.calendar.presentation.calendar.pagerAdapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import me.proton.android.calendar.presentation.calendar.fragment.ItemMiniCalendarFragment
import java.time.LocalDate

class MiniCalendarPagerAdapter(activity: FragmentActivity, val firstDayOfMonth: LocalDate) :
    FragmentStateAdapter(activity) {

    val startingPosition = itemCount / 2

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    override fun createFragment(position: Int): Fragment {
        return ItemMiniCalendarFragment.newInstance(
            position,
            startingPosition,
            firstDayOfMonth
        )
    }
}
