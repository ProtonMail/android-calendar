package me.proton.android.calendar.presentation.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import me.proton.android.calendar.R
import me.proton.android.calendar.databinding.FragmentBaseBinding
import me.proton.android.calendar.databinding.FragmentRootBinding
import me.proton.android.calendar.presentation.main.MainActivity

abstract class BaseFragment<VB: ViewBinding> : Fragment() {

    abstract val TAG: String
    abstract val layoutResourceId: Int

    private var _binding: VB? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    protected val binding get() = _binding!!

    private var _fragmentBaseBinding: FragmentBaseBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val fragmentBaseBinding get() = _fragmentBaseBinding!!

    protected open fun onToolbarCreated(toolbar: Toolbar) {}

    val fragmentToolbarContent get() = fragmentBaseBinding.fragmentToolbarContent
    val fragmentToolbarTitleLayout get() = fragmentBaseBinding.fragmentToolbarTitleLayout
    val fragmentProgressBar get() = fragmentBaseBinding.fragmentProgressBar
    val miniCalendarChevron get() = fragmentBaseBinding.miniCalendarChevron

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _fragmentBaseBinding = FragmentBaseBinding.inflate(inflater, container, false)
        _binding = getViewBinding(inflater, container)
        fragmentBaseBinding.fragmentContainer.addView(binding.root)

        // Hide splash screen
        (requireActivity() as? MainActivity)?.displaySplashScreen(false)

        val toolbar = fragmentBaseBinding.fragmentToolbar
        toolbar.apply {
            setNavigationIcon(R.drawable.ic_proton_hamburger)
            setNavigationContentDescription(R.string.hamburger_button)

            setNavigationOnClickListener { // TODO make sure we shouldn't clear this embedded dialog-stack
                ((requireActivity().findViewById(R.id.drawer_layout) as DrawerLayout).openDrawer(
                    GravityCompat.START)) // TODO maybe move to activity
            }
            onToolbarCreated(this)
        }

        return fragmentBaseBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _fragmentBaseBinding = null
    }
}
