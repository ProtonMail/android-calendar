package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SEARCH_MIN_QUERY_LENGTH
import me.proton.android.calendar.common.SEARCH_QUERY_DEBOUNCE_MS
import me.proton.android.calendar.common.SEARCH_RESULTS_RANGE
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.onTextChange
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.databinding.FragmentEventFormRecurrenceBinding
import me.proton.android.calendar.databinding.FragmentSearchBinding
import me.proton.android.calendar.presentation.calendar.adapter.TimelineEventAdapter
import me.proton.android.calendar.presentation.calendar.adapter.findIndexToScrollTo
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.SearchViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent
import java.time.ZoneId

class SearchFragment() : BaseDialogFragment<FragmentSearchBinding>(), KoinComponent {

    override val TAG = "SearchFragment"
    override val layoutResourceId = R.layout.fragment_search
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()

    private lateinit var timelineEventAdapter: TimelineEventAdapter

    private var searchJob: Job? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentSearchBinding.inflate(inflater, container, false)

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialogAppbar.visibleOrGone(false)

        timelineEventAdapter = TimelineEventAdapter {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigate(
                Navigation.Deeplink.toEventDetails(it.id, it.occurrenceNumber)
            )
        }

        with(binding) {

            with(searchIconBack.imageButton) {
                setImageResource(R.drawable.ic_proton_arrow_left)
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    findNavController().navigateUp()
                }
            }

            searchResultList.addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0) {
                        // Hide keyboard on scroll down
                        requireActivity().clearFocusAndHideKeyboard(view)
                    }
                }
            })

            searchInput.onTextChange().debounce(SEARCH_QUERY_DEBOUNCE_MS).onEach { rawQuery ->

                searchClear.root.visibleOrGone(rawQuery.isNotBlank())

                val query = rawQuery.trim().toString()

                val (userEmails, userId) = withContext(Dispatchers.Default) {
                    (calendarViewModel.getUserEmails() ?: emptyList()) to (calendarViewModel.userId.value)
                }

                if (query.isNotBlank() && query.length >= SEARCH_MIN_QUERY_LENGTH && userEmails.isNotEmpty() && userId != null) {
                    fragmentProgressBar.visibleOrInvisible(true)

                    searchJob?.cancel()
                    searchJob = calendarViewModel.getTimelineEvents(
                        userId.id,
                        query,
                        userEmails,
                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                        calendarViewModel.getCalendarUserSettingsPrimaryTimezone() ?: ZoneId.systemDefault().id).onEach {

                        if (it?.isNotEmpty() == true) {
                            llSearchNoResults.visibleOrGone(false)
                            fragmentProgressBar.visibleOrInvisible(false)

                            // truncate the list from both ends and center on "today"

                            val range = SEARCH_RESULTS_RANGE // how many events to show before and after "today"
                            val indexToScrollTo = it.findIndexToScrollTo(calendarViewModel.getTimeZoneId() ?: ZoneId.systemDefault())

                            val leftIndex = maxOf(0, indexToScrollTo - range)
                            val rightIndex = minOf(it.size, indexToScrollTo + range)

                            val truncatedList = if (it[leftIndex] !is TimelineEventAdapter.TimelineItem.Header) {
                                // we truncated the header from beginning of the list, we need to put it back
                                val headerIndex = it.subList(0, leftIndex).indexOfLast { it is TimelineEventAdapter.TimelineItem.Header }

                                listOf(it[headerIndex]) + it.subList(leftIndex + 1, rightIndex)
                            } else it.subList(leftIndex, rightIndex)

                            timelineEventAdapter.submitList(truncatedList) {
                                val adjustedIndexToScrollTo = if (indexToScrollTo < range) indexToScrollTo else range
                                searchResultList.scrollToPosition(adjustedIndexToScrollTo)
                            }
                        } else {
                            timelineEventAdapter.submitList(emptyList())
                            fragmentProgressBar.visibleOrInvisible(false)
                            Handler(Looper.getMainLooper()).postDelayed({
                                llSearchNoResults.visibleOrGone(true)
                            }, 500)
                        }
                    }.launchIn(lifecycleScope)

                } else {
                    searchJob?.cancel()
                    fragmentProgressBar.visibleOrInvisible(false)
                    llSearchNoResults.visibleOrGone(false)
                    timelineEventAdapter.submitList(emptyList())
                }

            }.launchIn(lifecycleScope)

            with(searchClear.imageButton) {
                setImageResource(R.drawable.ic_proton_cross)
                setOnSingleClickListener {
                    searchInput.text.clear()
                    timelineEventAdapter.submitList(emptyList())
                }
            }

            searchResultList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)

            searchResultList.adapter = timelineEventAdapter

            pbSearchOnboardingAction.setOnSingleClickListener {
                searchViewModel.actionButtonClicked()
            }
        }

        searchViewModel.startObservingWorkerState(viewLifecycleOwner)

        searchViewModel.downloadingState.asLiveData().observe(viewLifecycleOwner) {

            when (it) {
                SearchViewModel.DownloadingState.INIT -> {
                    // don't do anything, other state should come next
                }
                SearchViewModel.DownloadingState.NONE -> {
                    lifecycleScope.launchWhenResumed {
                        if (searchViewModel.isCalendarDownloadEnabled()) {
                            showSearchInterface()
                        } else {
                            showSearchOnboarding()
                        }
                    }
                }
                is SearchViewModel.DownloadingState.ONGOING -> { // pause downloading
                    showSearchOnboarding()

                    showProgressOngoing(it.progressPercentage, it.progressText)
                }
                SearchViewModel.DownloadingState.PAUSED -> { // resume downloading
                    showSearchOnboarding()

                    showProgressPaused()
                }
                SearchViewModel.DownloadingState.FINISHED -> { // shouldn't really happen, button should be invisible

                    showProgressFinished()

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isResumed) showSearchInterface()
                        // reset the state so we don't get into FINISHED each time we get back to search
                        //  after we just finished downloading
                        searchViewModel.clearDownloadingState()
                    }, 2000)
                }
                SearchViewModel.DownloadingState.ERROR -> {
                    showSearchOnboarding()

                    showProgressError()
                }
            }

        }

    }

    override fun onBackPressedCustom() {
        activity?.clearFocusAndHideKeyboard(view)
        findNavController().navigateUp()
    }

    private fun showSearchInterface() {
        with(binding) {
            pbSearchOnboardingAction.visibleOrGone(false)

            includeSearchOnboarding.root.visibleOrGone(false)

            searchInput.visibleOrInvisible(true)
            searchSeparator.visibleOrInvisible(true)

            searchInput.requestFocus()
        }
        requireContext().showKeyboard()
    }

    private fun showSearchOnboarding() {
        with(binding) {
            pbSearchOnboardingAction.visibleOrGone(true)

            searchInput.visibleOrInvisible(false)
            searchSeparator.visibleOrInvisible(false)

            includeSearchOnboarding.root.visibleOrGone(true)

            includeSearchOnboarding.tvSearchOnboardingText.movementMethod = LinkMovementMethod.getInstance()
        }
        requireActivity().clearFocusAndHideKeyboard(view)
    }

    private fun showProgressOngoing(progressPercentage: Int, progressText: String) {
        with(binding.includeSearchOnboarding) {
            ivSearchOnboardingIllustration.visibleOrGone(false)

            pbSearchOnboarding.visibleOrGone(true)
            tvSearchOnboardingProgressHeader.visibleOrGone(true)
            tvSearchOnboardingProgressText.visibleOrGone(true)
            tvSearchOnboardingProgressPercentage.visibleOrGone(true)

            tvSearchOnboardingProgressHeader.text =
                resources.getString(R.string.search_onboarding_progress_header_ongoing)
            tvSearchOnboardingProgressHeader.setTextColor(
                requireContext().getColorFromAttr(
                    R.attr.proton_text_norm
                )
            )
            pbSearchOnboarding.progressDrawable =
                AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background)
            tvSearchOnboardingProgressText.visibleOrInvisible(true)
            tvSearchOnboardingProgressText.text = progressText
            pbSearchOnboarding.progress = progressPercentage
            tvSearchOnboardingProgressPercentage.text = "${progressPercentage}%"
        }
        binding.pbSearchOnboardingAction.text = resources.getString(R.string.search_onboarding_action_pause)
    }

    private fun showProgressPaused() {
        with(binding.includeSearchOnboarding) {
            tvSearchOnboardingProgressHeader.text =
                resources.getString(R.string.search_onboarding_progress_header_paused)
            tvSearchOnboardingProgressHeader.setTextColor(
                requireContext().getColorFromAttr(
                    R.attr.proton_notification_error
                )
            )
            tvSearchOnboardingProgressText.visibleOrInvisible(false)
            pbSearchOnboarding.progressDrawable =
                AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_paused)
        }
        binding.pbSearchOnboardingAction.text = resources.getString(R.string.search_onboarding_action_resume)
    }

    private fun showProgressError() {
        with(binding.includeSearchOnboarding) {
            tvSearchOnboardingProgressHeader.text =
                resources.getString(R.string.search_onboarding_progress_header_error)
            tvSearchOnboardingProgressHeader.setTextColor(
                requireContext().getColorFromAttr(
                    R.attr.proton_notification_error
                )
            )
            tvSearchOnboardingProgressText.visibleOrInvisible(false)
            pbSearchOnboarding.progressDrawable =
                AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_paused)
        }
        binding.pbSearchOnboardingAction.text = resources.getString(R.string.search_onboarding_action_retry)
    }

    private fun showProgressFinished() {
        with(binding.includeSearchOnboarding) {
            tvSearchOnboardingProgressHeader.text =
                resources.getString(R.string.search_onboarding_progress_header_completed)
            tvSearchOnboardingProgressHeader.setTextColor(
                requireContext().getColorFromAttr(
                    R.attr.proton_text_norm
                )
            )
            tvSearchOnboardingProgressText.visibleOrInvisible(false)
            pbSearchOnboarding.progressDrawable =
                AppCompatResources.getDrawable(requireContext(), R.drawable.custom_progress_background_finished)
            pbSearchOnboarding.progress = 100
            tvSearchOnboardingProgressPercentage.text = "${100}%"
        }
        binding.pbSearchOnboardingAction.visibleOrGone(false)
    }
}
