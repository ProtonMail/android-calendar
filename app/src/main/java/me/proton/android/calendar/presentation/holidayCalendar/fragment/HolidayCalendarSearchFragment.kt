package me.proton.android.calendar.presentation.holidayCalendar.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.HOLIDAY_SEARCH_MIN_QUERY_LENGTH
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.onTextChange
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.databinding.FragmentHolidayCalendarFormBinding
import me.proton.android.calendar.databinding.FragmentHolidayCalendarSearchBinding
import me.proton.android.calendar.domain.model.Holiday
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.holidayCalendar.adapter.HolidayCalendarListAdapter
import me.proton.android.calendar.presentation.holidayCalendar.viewModel.HolidayCalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.presentation.utils.currentLocale
import org.koin.core.KoinComponent
import java.time.ZoneId

@AndroidEntryPoint
class HolidayCalendarSearchFragment : BaseDialogFragment<FragmentHolidayCalendarSearchBinding>(), KoinComponent {

    override val TAG: String
        get() = "HolidayCalendarSearchFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holiday_calendar_search

    override val navigateUp = true
    override val isScrollable = false

    private val holidayCalendarViewModel: HolidayCalendarViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()

    private lateinit var holidayCalendarListAdapter: HolidayCalendarListAdapter
    private var holidayCalendarList = arrayListOf<HolidayCalendarListAdapter.HolidayItem>()

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentHolidayCalendarSearchBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide app bar
        dialogAppbar.visibleOrGone(false)

        binding.holidayCalendarSearchClose.toolbarActionText.text = getString(R.string.dialog_button_close)
        binding.holidayCalendarSearchClose.toolbarActionText.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }

        with(binding.holidayCalendarSearchClear.imageButton) {
            setImageResource(R.drawable.ic_proton_cross)
            setOnSingleClickListener {
                binding.holidayCalendarSearchNoResult.visibleOrGone(false)
                binding.holidayCalendarSearchInput.text.clear()
                holidayCalendarListAdapter.setSearchQuery("")
                holidayCalendarListAdapter.submitList(holidayCalendarList)
            }
        }

        binding.holidayCalendarCountryList.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        val countryListLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        binding.holidayCalendarCountryList.layoutManager = countryListLayoutManager
        holidayCalendarListAdapter = HolidayCalendarListAdapter {
            lifecycleScope.launch {
                holidayCalendarViewModel.handleCountry(it.country, requireContext().resources.configuration.currentLocale().language.lowercase())
            }
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }
        binding.holidayCalendarCountryList.adapter = holidayCalendarListAdapter

        setupSearch()

        holidayCalendarViewModel.holidayCalendars.observe(viewLifecycleOwner) { holidayCalendars ->
            holidayCalendars ?: return@observe

            // We only show visible calendars
            val visibleHolidayCalendars = holidayCalendars.filter { it.hidden == false }
            lifecycleScope.launch {
                val primaryTimeZoneId = calendarViewModel.getCalendarUserSettingsPrimaryTimezone() ?: ZoneId.systemDefault().id
                holidayCalendarList.clear()
                holidayCalendarList.addAll(visibleHolidayCalendars.toHolidayItems(primaryTimeZoneId))
                holidayCalendarListAdapter.submitList(holidayCalendarList)
            }
        }
    }

    @SuppressLint("DiscouragedApi") // Suppress annotation caused by getIdentifier to get flag drawables
    fun List<ManagedHolidayCalendarEntity>.toHolidayItems(primaryTimeZoneId: String): List<HolidayCalendarListAdapter.HolidayItem> {
        val holidayItems = arrayListOf<HolidayCalendarListAdapter.HolidayItem>()
        var basedOnTimeZoneHeaderAdded = false
        this.sortedBy { it.country }.groupBy { it.country }.forEach {
            val header = HolidayCalendarListAdapter.HolidayItem.Header(
                it.key.first().uppercase(),
                false
            )
            // Add first letter header
            if (!holidayItems.contains(header)) holidayItems.add(header)
            val countryCode = it.value.first().countryCode
            val countryFlagDrawable = resources.getIdentifier(
                "${requireContext().packageName}:drawable/flag_$countryCode",
                "drawable",
                requireContext().packageName
            )
            holidayItems.add(HolidayCalendarListAdapter.HolidayItem.Value(Holiday(it.key, countryFlagDrawable)))

            if (it.value.first().timezones.contains(primaryTimeZoneId)) {
                // Add based on time zone item
                if (!basedOnTimeZoneHeaderAdded) {
                    val basedOnTimeZoneHeaderHeader = HolidayCalendarListAdapter.HolidayItem.Header(
                        it.key.first().uppercase(),
                        it.value.first().timezones.contains(primaryTimeZoneId)
                    )
                    holidayItems.add(0, basedOnTimeZoneHeaderHeader)
                    basedOnTimeZoneHeaderAdded = true
                }
                holidayItems.add(1, HolidayCalendarListAdapter.HolidayItem.Value(Holiday(it.key, countryFlagDrawable)))
            }
        }
        return holidayItems
    }

    private fun setupSearch() {
        // Focus on search field when opening the view and show keyboard
        binding.holidayCalendarSearchInput.requestFocus()
        requireContext().showKeyboard()

        binding.holidayCalendarSearchInput.onTextChange { rawQuery ->

            binding.holidayCalendarSearchClear.root.visibleOrGone(rawQuery.isNotBlank())

            val query = rawQuery.trim().toString()

            if (query.isNotBlank() && query.length >= HOLIDAY_SEARCH_MIN_QUERY_LENGTH) {
                holidayCalendarListAdapter.setSearchQuery(query)

                val values = holidayCalendarList.filter { it.type == HolidayCalendarListAdapter.HolidayItemType.Value }
                val queriedValues = values.filter { (it as HolidayCalendarListAdapter.HolidayItem.Value).holiday.country.contains(query, ignoreCase = true) }
                val sortedQueriedValues = queriedValues.sortedBy { (it as HolidayCalendarListAdapter.HolidayItem.Value).holiday.country }
                val resultList = arrayListOf<HolidayCalendarListAdapter.HolidayItem>()
                sortedQueriedValues.groupBy { (it as HolidayCalendarListAdapter.HolidayItem.Value).holiday.country[0] }.forEach {
                    resultList.add(HolidayCalendarListAdapter.HolidayItem.Header(it.key.toString(), false))
                    resultList.addAll(it.value)
                }
                binding.holidayCalendarSearchNoResult.visibleOrGone(resultList.isEmpty())
                holidayCalendarListAdapter.submitList(resultList.distinct())
            } else {
                holidayCalendarListAdapter.setSearchQuery("")
                binding.holidayCalendarSearchNoResult.visibleOrGone(false)
                holidayCalendarListAdapter.submitList(holidayCalendarList)
            }
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
