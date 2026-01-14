package me.proton.android.calendar.presentation.calendar.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.property.Attendee
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CONTACTS_SEARCH_QUERY
import me.proton.android.calendar.common.FormValidation.ATTENDEE_MAX_ALLOWED
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.onTextChange
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.validateEmail
import me.proton.android.calendar.databinding.FragmentEventFormAttendeesBinding
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.adapter.AddAttendeeListAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.contact.domain.entity.ContactEmail
import org.koin.core.KoinComponent

class EventFormAttendeesFragment() : BaseDialogFragment<FragmentEventFormAttendeesBinding>(), KoinComponent, LoaderManager.LoaderCallbacks<Cursor> {

    override val TAG = "EventFormAttendeesFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_attendees
    override val isScrollable = false

    private val navigationArguments: EventFormAttendeesFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val eventViewModel: EventViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private val _searchAttendeeList: MutableLiveData<List<Attendee>> = MutableLiveData()
    private val searchAttendeeList: LiveData<List<Attendee>> = _searchAttendeeList

    private lateinit var attendeeListAdapter: AddAttendeeListAdapter
    private lateinit var searchAttendeeListAdapter: AddAttendeeListAdapter
    private lateinit var toolbarTitle: TextView

    private val protonContacts: ArrayList<ContactEmail> = arrayListOf()
    private val cachedProtonContacts: ArrayList<Attendee> = arrayListOf()
    private val cachedDeviceContacts: ArrayList<Attendee> = arrayListOf()

    private var contactsAccessGranted = false

    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsAccessGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (contactsAccessGranted) {
            LoaderManager.getInstance(this).initLoader(0, null, this)
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentEventFormAttendeesBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            accountViewModel.getPrimaryUserId()?.let {
                val contacts = mainViewModel.getProtonContacts(it) ?: emptyList()
                protonContacts.addAll(contacts)
                cachedProtonContacts.addAll(
                    contacts.map { Attendee(it.name, it.email) }
                )

                if (this@EventFormAttendeesFragment::attendeeListAdapter.isInitialized
                    && attendeeListAdapter.currentList.isNotEmpty()) {
                    withStarted {
                        attendeeListAdapter.submitList(
                            ProtonUtilsImpl.matchAttendeesWithContacts(
                                attendeeListAdapter.currentList,
                                cachedDeviceContacts,
                                protonContacts
                            )
                        ) {
                            binding.eventFormAttendeesList.smoothScrollToPosition(0)
                        }
                    }
                }
            }
        }

        readOnly = navigationArguments.readOnly
        if (readOnly) {
            // Hide search
            binding.eventFormAttendeesSearchIcon.visibleOrGone(false)
            binding.eventFormAttendeesSearchInput.visibleOrGone(false)
            // Hide done button
            binding.eventFormAttendeesDone.root.visibleOrGone(false)
            // Display disclaimer
            binding.eventFormAttendeesReadOnlyDisclaimer.visibleOrGone(true)
            // Display toolbar and set title
            dialogAppbar.visibleOrGone(true)
            toolbarTitle = toolbar.findViewById(R.id.dialog_toolbar_title)
            toolbarTitle.text = getString(R.string.event_text_attendees)
        } else {
            binding.eventFormAttendeesSearchIcon.visibleOrGone(true)
            binding.eventFormAttendeesSearchInput.visibleOrGone(true)
            binding.eventFormAttendeesDone.root.visibleOrGone(true)
            binding.eventFormAttendeesReadOnlyDisclaimer.visibleOrGone(false)
            dialogAppbar.visibleOrGone(false)
        }

        binding.eventFormAttendeesDone.toolbarActionText.text = getString(R.string.action_done)
        binding.eventFormAttendeesListHeader.text = getString(R.string.event_text_participants, 0, ATTENDEE_MAX_ALLOWED)

        binding.eventFormAttendeesDone.toolbarActionText.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }

        binding.eventFormAttendeesList.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        binding.eventFormAttendeesSearchList.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    // Hide keyboard on scroll down
                    requireActivity().clearFocusAndHideKeyboard(view)
                }
            }
        })

        binding.eventFormAttendeesSearchInput.onTextChange { query ->
            binding.eventFormAttendeesDone.root.visibleOrInvisible(query.isEmpty())
            binding.eventFormAttendeesSearchClear.visibleOrGone(query.isNotEmpty())

            val attendeeList = eventViewModel.eventLiveData.value?.iCalEvent?.attendees
            binding.eventFormAttendeesListLayout.visibleOrGone(query.isEmpty() && !attendeeList.isNullOrEmpty())
            if (query.isEmpty()) binding.eventFormAttendeesSearchList.visibleOrGone(false)

            if (query.isNotEmpty()) {
                binding.eventFormAttendeesSearchList.visibleOrGone(true)
                searchAttendeeListAdapter.setQuery(query.toString())

                if (contactsAccessGranted) {
                    val args = Bundle().apply {
                        putString(CONTACTS_SEARCH_QUERY, query.toString())
                    }
                    LoaderManager.getInstance(this).restartLoader(0, args, this)
                } else {
                    val currentlyTypedAttendeeList = if (validateEmail(query)) {
                        listOf(Attendee("", query.toString()))
                    } else emptyList()

                    val protonAttendees = cachedProtonContacts.asSequence().filter {
                        it.commonName.contains(query.toString(), ignoreCase = true)
                                || it.extractEmail()?.contains(query.toString(), ignoreCase = true) == true
                    }

                    val protonAttendeesContainTypedAttendee = protonAttendees.any { it.commonName.equals(query.toString(), ignoreCase = true)
                            || it.extractEmail()?.equals(query.toString(), ignoreCase = true) == true }

                    // if typed Attendee is in Proton Contacts, don't add it to the result list
                    val protonAttendeesAndTypedAttendee = if (protonAttendeesContainTypedAttendee) {
                        protonAttendees
                    } else {
                        protonAttendees + currentlyTypedAttendeeList
                    }

                    val searchResult = protonAttendeesAndTypedAttendee.groupBy { it.extractEmail() }.flatMap { it.value.sortedBy { it.commonName } }
                    
                    binding.eventFormAttendeesSearchList.visibleOrGone(searchResult.isNotEmpty())
                    _searchAttendeeList.postValue(searchResult)
                }
            }
        }

        binding.eventFormAttendeesSearchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE && validateEmail(binding.eventFormAttendeesSearchInput.text)) {
                val query = binding.eventFormAttendeesSearchInput.text.toString()
                val index = searchAttendeeListAdapter.currentList.indexOfFirst {
                    it.extractEmail().equals(query, true)
                }
                if (index != -1) {
                    binding.eventFormAttendeesSearchList.getChildAt(index)?.findViewById<View>(
                        R.id.item_add_attendee_press
                    )?.let { itemPress ->
                        if (itemPress.isVisible) itemPress.performClick()
                    }
                }
            }
            false
        }

        binding.eventFormAttendeesSearchClear.setOnSingleClickListener {
            binding.eventFormAttendeesSearchInput.text.clear()
        }

        val attendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        binding.eventFormAttendeesList.layoutManager = attendeesLayoutManager
        attendeeListAdapter = AddAttendeeListAdapter(searchList = false, readOnly = readOnly) {
            eventViewModel.handleAttendee(it, addAttendee = false)
        }
        (binding.eventFormAttendeesList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.eventFormAttendeesList.adapter = attendeeListAdapter

        val searchAttendeesLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        binding.eventFormAttendeesSearchList.layoutManager = searchAttendeesLayoutManager
        searchAttendeeListAdapter = AddAttendeeListAdapter(searchList = true, readOnly = readOnly) {
            addAttendee(it)
        }
        (binding.eventFormAttendeesSearchList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.eventFormAttendeesSearchList.adapter = searchAttendeeListAdapter

        searchAttendeeList.observe(viewLifecycleOwner) { searchAttendeeList ->
            searchAttendeeListAdapter.submitList(searchAttendeeList)
            // TODO Try to find a way to refresh the highlighted text and icons visibility without calling notifyDataSetChanged
            searchAttendeeListAdapter.notifyDataSetChanged()
        }

        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { nullableEvent ->
            val event = nullableEvent ?: return@Observer
            lifecycleScope.launch {
                // Get non canonical email
                val organizerEmail = event.calendar.email
                val userAddresses = calendarViewModel.getUserAddresses()
                val organizerAddress = userAddresses?.firstOrNull {
                    canonicalizeProtonEmail(it.email, forceCanonicalization = true).equals(
                        canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true),
                        true
                    )
                }
                val organizerName = organizerAddress?.displayName
                val organizer = Attendee(
                    if (organizerName.isNullOrEmpty()) organizerEmail else organizerName,
                    organizerEmail
                )

                organizer.extractEmail()?.let {
                    attendeeListAdapter.setOrganizerEmail(it)
                    searchAttendeeListAdapter.setOrganizerEmail(it)
                }
                val attendeeList = ArrayList(event.iCalEvent.attendees.reversed()) // Last added at the top, first at the bottom
                if (!attendeeList.isNullOrEmpty() && organizer.extractEmail() != null && !attendeeList.contains(organizer)) {
                    attendeeList.add(organizer)
                }

                searchAttendeeListAdapter.setAttendeeList(attendeeList)

                val scrollUp = attendeeList.size > attendeeListAdapter.currentList.size

                attendeeListAdapter.submitList(
                    ProtonUtilsImpl.matchAttendeesWithContacts(
                        attendeeList,
                        cachedDeviceContacts,
                        protonContacts
                    )
                ) {
                    if (scrollUp) binding.eventFormAttendeesList.smoothScrollToPosition(0) // Scroll up top to new attendee
                }

                binding.eventFormAttendeesListHeader.visibleOrGone(attendeeList.isNotEmpty())
                val attendeeCount = if (attendeeList.size > 0) attendeeList.size - 1 else 0 // Subtract organizer that was added to bottom of list
                binding.eventFormAttendeesListHeader.text = getString(R.string.event_text_participants, attendeeCount, ATTENDEE_MAX_ALLOWED)
                binding.eventFormAttendeesListLayout.visibleOrGone(attendeeList.isNotEmpty())
            }
        })

        if (!readOnly) {
            binding.eventFormAttendeesSearchInput.requestFocus()
            requireContext().showKeyboard()
        }
    }

    private fun addAttendee(attendee: Attendee) {
        lifecycleScope.launch {
            val tmpAttendeeList = ArrayList(eventViewModel.eventLiveData.value?.iCalEvent?.attendees ?: listOf<Attendee>())

            if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) {
                view?.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                return@launch
            }

            val email = attendee.extractEmail()
            email?.let {
                // TODO Use get canonical route for second validation ? What are the actual error cases ?
                val canonicalEmail = calendarViewModel.getCanonicalEmails(listOf(email))?.get(email)
                if (canonicalEmail == null) {
                    view?.displaySnackBar(getString(R.string.snack_add_participant_invalid_email))
                    searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                    return@launch
                }

                val userEmails = calendarViewModel.getUserAddresses()?.map { it.email } ?: listOf()
                if (userEmails.firstOrNull { canonicalizeProtonEmail(it, forceCanonicalization = true).equals(
                        canonicalizeProtonEmail(canonicalEmail, forceCanonicalization = true), true
                    ) } != null) {
                    view?.displaySnackBar(getString(R.string.snack_add_self_as_participant))
                    searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
                    return@launch
                }

                tmpAttendeeList.add(attendee)

                binding.eventFormAttendeesSearchInput.text.clear()

                eventViewModel.handleAttendee(attendee, canonicalEmail)

                if (tmpAttendeeList.size >= ATTENDEE_MAX_ALLOWED) { // Warn the user once max is reached
                    view?.displaySnackBar(getString(R.string.snack_maximum_participants_reached))
                }

                searchAttendeeListAdapter.notifyDataSetChanged() // Clear loading icon visibility
            }
        }
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    // Loader and callbacks for contacts search

    companion object {
        private const val ANDROID_ORDER_BY = ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " ASC"
        private const val ANDROID_SELECTION = (
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " LIKE ?" + " OR " + ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?" + " OR "
                        + ContactsContract.CommonDataKinds.Email.DATA + " LIKE ?")
        private val ANDROID_PROJECTION = arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.DATA)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val searchString = args?.getString(CONTACTS_SEARCH_QUERY) ?: ""
        val selectionArgs = arrayOf("%$searchString%", "%$searchString%", "%$searchString%")
        return CursorLoader(
            requireContext(),
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            ANDROID_PROJECTION,
            ANDROID_SELECTION,
            selectionArgs,
            ANDROID_ORDER_BY
        )
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        if (data.isBeforeFirst) {
            val attendees = data.getAttendeeList()
            val query = binding.eventFormAttendeesSearchInput.text
            val deviceList =
                attendees.ifEmpty {
                    // If no results in contacts, suggest email
                    val searchResult =
                        when {
                            validateEmail(query) -> {
                                val participant = Attendee("", query.toString())
                                listOf(participant)
                            }

                            else -> listOf()
                        }
                    searchResult
                }
            val protonList = cachedProtonContacts.filter {
                it.commonName.contains(query.toString(), ignoreCase = true)
                        || it.extractEmail()?.contains(query.toString(), ignoreCase = true) == true
            }
            cachedDeviceContacts.addAll(deviceList)
            _searchAttendeeList.postValue(
                deviceList.plus(protonList).groupBy { it.extractEmail() }.flatMap { it.value.sortedBy { it.commonName } }
            )
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        _searchAttendeeList.postValue(emptyList())
    }

    private fun Cursor.extractAttendee(): Attendee {
        val displayNamePrimary = getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY)
        val emailAddress = getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
        val name = getString(if (displayNamePrimary >= 0) displayNamePrimary else 0)
        val email = getString(if (emailAddress >= 0) emailAddress else 0)
        return Attendee(
            name,
            email
        )
    }

    private fun Cursor.getAttendeeList(): List<Attendee> {
        val contactsList = mutableListOf<Attendee>()
        this.apply {
            while(moveToNext()) {
                val contactItem = extractAttendee()
                contactsList.add(contactItem)
            }
        }
        return contactsList
    }
}
