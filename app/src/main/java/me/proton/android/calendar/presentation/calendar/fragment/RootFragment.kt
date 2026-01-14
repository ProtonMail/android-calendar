package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.FragmentRootBinding
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import org.koin.core.KoinComponent

class RootFragment : Fragment(), KoinComponent {

    private var _binding: FragmentRootBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val calendarViewModel: CalendarViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRootBinding.inflate(inflater, container, false)

        calendarViewModel.fetchingEvents.observe(viewLifecycleOwner, Observer {
            if (it == null) {
                with(binding) {
                    rootProgressBar.visibleOrGone(false)
                    rootProgressText.visibleOrGone(false)
                    rootProgressText.text = ""
                }
            } else {
                with(binding) {
                    rootProgressBar.visibleOrGone(true)
                    rootProgressText.visibleOrGone(true)
                    rootProgressText.text = it
                }
            }

        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
