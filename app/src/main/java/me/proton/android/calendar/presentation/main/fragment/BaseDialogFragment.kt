package me.proton.android.calendar.presentation.main.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.databinding.FragmentBaseDialogBinding

// TODO maybe remove DialogFragment whatsoever
abstract class BaseDialogFragment<VB: ViewBinding> : DialogFragment() {

    // properties for subclasses to override
    abstract val TAG: String
    abstract val layoutResourceId: Int

    private var _binding: VB? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    protected val binding get() = _binding!!

    private var _fragmentBaseDialogBinding: FragmentBaseDialogBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val fragmentBaseDialogBinding get() = _fragmentBaseDialogBinding!!

    /**
     * Show navigation arrow or close button
     */
    protected open val navigateUp: Boolean = true

    /**
     * Show hamburger icon and open drawer on navigation click
     */
    protected open val isTopLevel: Boolean = false

    /**
     * Wrap this Fragment's layout in ScrollView
     */
    protected open val isScrollable: Boolean = true
    protected open val actionMenuResourceId: Int? = null
    protected open fun onMenuItemClicked(menuItem: MenuItem) {}
    protected open fun onToolbarCreated(toolbar: Toolbar) {}

    //TODO Remove once we get rid of DialogFragment
    protected open fun onBackPressedCustom() {}

    /**
     * Override this to customise action on "close/arrow back" click.
     */
    protected open fun onNavigationIconClicked(): Boolean = false
    // ^ properties for subclasses to override

    protected lateinit var toolbar: Toolbar

    val dialogAppbar get() = fragmentBaseDialogBinding.dialogAppbar

    val dialogToolbarContent get() = fragmentBaseDialogBinding.dialogToolbarContent

    override fun getTheme(): Int = R.style.AppFullscreenDialogTheme

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        // workaround for animations being ignored by <dialog> component in Navigation Graph
        dialog?.window?.attributes?.windowAnimations = theme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _fragmentBaseDialogBinding = FragmentBaseDialogBinding.inflate(inflater, container, false)
        _binding = getViewBinding(inflater, container)
        if (isScrollable) fragmentBaseDialogBinding.dialogContainerScrollable.addView(binding.root)
        else fragmentBaseDialogBinding.dialogContainer.addView(binding.root)

        toolbar = fragmentBaseDialogBinding.dialogToolbar

        toolbar.apply {
            title = ""
            // navigation
            if (isTopLevel) {
                setNavigationIcon(R.drawable.ic_proton_hamburger)
            } else {
                if (navigateUp) {
                    setNavigationIcon(R.drawable.ic_proton_arrow_left)
                } else {
                    setNavigationIcon(R.drawable.ic_proton_cross)
                }
            }

            setNavigationOnClickListener { // TODO make sure we shouldn't clear this embedded dialog-stack
                if (isTopLevel) {
                    ((requireActivity().findViewById(R.id.drawer_layout) as DrawerLayout).openDrawer(
                        GravityCompat.START)) // TODO maybe move to activity
                } else {
                    if (!onNavigationIconClicked()) {
                        dismiss()
                    }
                }
            }
            onToolbarCreated(this)
        }

        AndroidUtils.applyAndroid15EdgeToEdge(fragmentBaseDialogBinding.root)

        return fragmentBaseDialogBinding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                onBackPressedCustom()
            }
        }
    }

    interface DisplayDialog {
        fun alertDialog(
            title: String,
            message: String,
            positiveButton: String,
            negativeButton: String,
            dialogListener: DialogListener?
        )
        fun pickerDialog(
            title: String,
            items: Array<String>,
            defaultSelectedItem: Int,
            positiveButton: String,
            negativeButton: String,
            dialogListener: DialogListener?
        )
    }

    interface DialogListener {
        fun onPositive(selectedItem: Int = 0)
        fun onNegative()
        fun onCancel()
        fun onDismiss()
    }

    fun provideDisplayDialog(): DisplayDialog {
        return object: DisplayDialog {
            override fun alertDialog(
                title: String,
                message: String,
                positiveButton: String,
                negativeButton: String,
                dialogListener: DialogListener?
            ) {
                lifecycleScope.launch {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(positiveButton) { _, _ ->
                            dialogListener?.onPositive()
                        }
                        .setNegativeButton(negativeButton) { _, _ ->
                            dialogListener?.onNegative()
                        }
                        .setOnCancelListener {
                            dialogListener?.onCancel()
                        }
                        .setOnDismissListener {
                            dialogListener?.onDismiss()
                        }
                        .show()
                }
            }

            override fun pickerDialog(
                title: String,
                items: Array<String>,
                defaultSelectedItem: Int,
                positiveButton: String,
                negativeButton: String,
                dialogListener: DialogListener?
            ) {
                lifecycleScope.launch {
                    var selectedItem = defaultSelectedItem
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setSingleChoiceItems(items, defaultSelectedItem) { _, item ->
                            selectedItem = item
                        }
                        .setPositiveButton(positiveButton) { _, _ ->
                            dialogListener?.onPositive(selectedItem)
                        }
                        .setNegativeButton(negativeButton) { _, _ ->
                            dialogListener?.onNegative()
                        }
                        .setOnCancelListener { _ ->
                            dialogListener?.onCancel()
                        }
                        .setOnDismissListener {
                            dialogListener?.onDismiss()
                        }
                        .show()
                }
            }
        }
    }
}
