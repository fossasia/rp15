package org.fossasia.openevent.general.event

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.content_no_internet.view.noInternetCard
import kotlinx.android.synthetic.main.content_no_internet.view.retry
import kotlinx.android.synthetic.main.dialog_reset_password.view.confirmNewPassword
import kotlinx.android.synthetic.main.dialog_reset_password.view.newPassword
import kotlinx.android.synthetic.main.dialog_reset_password.view.textInputLayoutConfirmNewPassword
import kotlinx.android.synthetic.main.dialog_reset_password.view.textInputLayoutNewPassword
import kotlinx.android.synthetic.main.fragment_events.view.emptyEventsText
import kotlinx.android.synthetic.main.fragment_events.view.eventsEmptyView
import kotlinx.android.synthetic.main.fragment_events.view.eventsRecycler
import kotlinx.android.synthetic.main.fragment_events.view.locationTextView
import kotlinx.android.synthetic.main.fragment_events.view.newNotificationDot
import kotlinx.android.synthetic.main.fragment_events.view.newNotificationDotToolbar
import kotlinx.android.synthetic.main.fragment_events.view.notification
import kotlinx.android.synthetic.main.fragment_events.view.notificationToolbar
import kotlinx.android.synthetic.main.fragment_events.view.scrollView
import kotlinx.android.synthetic.main.fragment_events.view.shimmerEvents
import kotlinx.android.synthetic.main.fragment_events.view.swiperefresh
import kotlinx.android.synthetic.main.fragment_events.view.toolbar
import kotlinx.android.synthetic.main.fragment_events.view.toolbarLayout
import org.fossasia.openevent.general.BottomIconDoubleClick
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.StartupViewModel
import org.fossasia.openevent.general.common.EventClickListener
import org.fossasia.openevent.general.common.FavoriteFabClickListener
import org.fossasia.openevent.general.data.Preference
import org.fossasia.openevent.general.search.location.SAVED_LOCATION
import org.fossasia.openevent.general.utils.RESET_PASSWORD_TOKEN
import org.fossasia.openevent.general.utils.Utils.progressDialog
import org.fossasia.openevent.general.utils.Utils.setToolbar
import org.fossasia.openevent.general.utils.Utils.show
import org.fossasia.openevent.general.utils.extensions.hideWithFading
import org.fossasia.openevent.general.utils.extensions.nonNull
import org.fossasia.openevent.general.utils.extensions.setPostponeSharedElementTransition
import org.fossasia.openevent.general.utils.extensions.setStartPostponedEnterTransition
import org.fossasia.openevent.general.utils.extensions.showWithFading
import org.jetbrains.anko.design.longSnackbar
import org.koin.androidx.viewmodel.ext.android.viewModel

const val BEEN_TO_WELCOME_SCREEN = "beenToWelcomeScreen"
const val EVENTS_FRAGMENT = "eventsFragment"

class EventsFragment : Fragment(), BottomIconDoubleClick {
    private val eventsViewModel by viewModel<EventsViewModel>()
    private val startupViewModel by viewModel<StartupViewModel>()
    private lateinit var rootView: View
    private val preference = Preference()
    private val eventsListAdapter = EventsListAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setPostponeSharedElementTransition()
        rootView = inflater.inflate(R.layout.fragment_events, container, false)
        if (preference.getString(SAVED_LOCATION).isNullOrEmpty() &&
            !preference.getBoolean(BEEN_TO_WELCOME_SCREEN, false)) {
            preference.putBoolean(BEEN_TO_WELCOME_SCREEN, true)
            findNavController(requireActivity(), R.id.frameContainer).navigate(R.id.welcomeFragment)
        }
        setToolbar(activity, show = false)

        val progressDialog = progressDialog(context, getString(R.string.loading_message))

        val token = arguments?.getString(RESET_PASSWORD_TOKEN)
        if (token != null)
            showResetPasswordAlertDialog(token)

        startupViewModel.resetPasswordEmail
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                findNavController(rootView).navigate(
                    EventsFragmentDirections.actionEventsToAuth(email = it, redirectedFrom = EVENTS_FRAGMENT)
                )
            })

        startupViewModel.dialogProgress
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                progressDialog.show(it)
            })

        rootView.eventsRecycler.layoutManager =
            GridLayoutManager(activity, resources.getInteger(R.integer.events_column_count))

        rootView.eventsRecycler.adapter = eventsListAdapter
        rootView.eventsRecycler.isNestedScrollingEnabled = false

        startupViewModel.checkAccessToken()
        startupViewModel.syncNotifications()
        startupViewModel.fetchSettings()
        handleNotificationDotVisibility(
            preference.getBoolean(NEW_NOTIFICATIONS, false))
        startupViewModel.newNotifications
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                handleNotificationDotVisibility(it)
            })

        eventsViewModel.pagedEvents
            .nonNull()
            .observe(viewLifecycleOwner, Observer { list ->
                eventsListAdapter.submitList(list)
                if (!rootView.shimmerEvents.isVisible)
                    showEmptyMessage(eventsListAdapter.currentList?.isEmpty() ?: true)
            })

        eventsViewModel.progress
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                if (it) {
                    rootView.shimmerEvents.startShimmer()
                    showEmptyMessage(false)
                    showNoInternetScreen(false)
                } else {
                    rootView.shimmerEvents.stopShimmer()
                    rootView.swiperefresh.isRefreshing = false
                }
                rootView.shimmerEvents.isVisible = it
            })

        eventsViewModel.message
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                rootView.longSnackbar(it)
            })

        rootView.notification.isVisible = eventsViewModel.isLoggedIn()
        rootView.notificationToolbar.isVisible = eventsViewModel.isLoggedIn()

        eventsViewModel.loadLocation()
        if (rootView.locationTextView.text == getString(R.string.enter_location)) {
            rootView.emptyEventsText.text = getString(R.string.choose_preferred_location_message)
        } else {
            rootView.emptyEventsText.text = getString(R.string.no_events_message)
        }
        rootView.locationTextView.text = eventsViewModel.savedLocation.value
        rootView.toolbar.title = rootView.locationTextView.text

        eventsViewModel.savedLocation
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                if (eventsViewModel.lastSearch != it) {
                    eventsViewModel.lastSearch = it
                    eventsViewModel.clearEvents()
                }
            })

        eventsViewModel.connection
            .nonNull()
            .observe(viewLifecycleOwner, Observer { isConnected ->
                val currentPagedEvents = eventsViewModel.pagedEvents.value
                if (currentPagedEvents != null) {
                    showNoInternetScreen(false)
                    eventsListAdapter.submitList(currentPagedEvents)
                } else {
                    if (isConnected) {
                        eventsViewModel.loadLocationEvents()
                    } else {
                        showNoInternetScreen(true)
                    }
                }
            })

        rootView.locationTextView.setOnClickListener {
            findNavController(rootView).navigate(EventsFragmentDirections.actionEventsToSearchLocation())
        }

        rootView.retry.setOnClickListener {
            if (eventsViewModel.savedLocation.value != null && eventsViewModel.isConnected()) {
                eventsViewModel.loadLocationEvents()
            }
            showNoInternetScreen(!eventsViewModel.isConnected())
        }

        rootView.swiperefresh.setColorSchemeColors(Color.BLUE)
        rootView.swiperefresh.setOnRefreshListener {
            showNoInternetScreen(!eventsViewModel.isConnected())
            eventsViewModel.clearEvents()
            eventsViewModel.clearLastSearch()
            if (!eventsViewModel.isConnected()) {
                rootView.swiperefresh.isRefreshing = false
            } else {
                eventsViewModel.loadLocationEvents()
            }
        }

        startupViewModel.isRefresh
            .nonNull()
            .observe(viewLifecycleOwner, Observer {
                if (it) refreshData()
            })

        return rootView
    }

    private fun refreshData() {
        eventsViewModel.loadLocationEvents()
        startupViewModel.fetchSettings()
        startupViewModel.syncNotifications()
    }

    private fun handleNotificationDotVisibility(isVisible: Boolean) {
        rootView.newNotificationDot.isVisible = isVisible
        rootView.newNotificationDotToolbar.isVisible = isVisible
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView.eventsRecycler.viewTreeObserver.addOnGlobalLayoutListener {
            setStartPostponedEnterTransition()
        }
        rootView.notification.setOnClickListener {
            moveToNotification()
        }
        rootView.notificationToolbar.setOnClickListener {
            moveToNotification()
        }

        val eventClickListener: EventClickListener = object : EventClickListener {
            override fun onClick(eventID: Long, imageView: ImageView) {
                findNavController(rootView).navigate(EventsFragmentDirections.actionEventsToEventsDetail(eventID),
                        FragmentNavigatorExtras(imageView to "eventDetailImage"))
            }
        }

        val redirectToLogin = object : RedirectToLogin {
            override fun goBackToLogin() {
                findNavController(rootView)
                    .navigate(EventsFragmentDirections.actionEventsToAuth(redirectedFrom = EVENTS_FRAGMENT))
            }
        }

        val favFabClickListener: FavoriteFabClickListener = object : FavoriteFabClickListener {
            override fun onClick(event: Event, itemPosition: Int) {
                if (eventsViewModel.isLoggedIn()) {
                    event.favorite = !event.favorite
                    eventsViewModel.setFavorite(event, event.favorite)
                    eventsListAdapter.notifyItemChanged(itemPosition)
                } else {
                    EventUtils.showLoginToLikeDialog(requireContext(),
                        layoutInflater, redirectToLogin, event.originalImageUrl, event.name)
                }
            }
        }

        val hashTagClickListener: EventHashTagClickListener = object : EventHashTagClickListener {
            override fun onClick(hashTagValue: String) {
                openSearch(hashTagValue)
            }
        }

        eventsListAdapter.apply {
            onEventClick = eventClickListener
            onFavFabClick = favFabClickListener
            onHashtagClick = hashTagClickListener
        }

        rootView.scrollView.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            if (scrollY > rootView.locationTextView.y + rootView.locationTextView.height &&
                !rootView.toolbarLayout.isVisible) rootView.toolbarLayout.showWithFading()
            else if (scrollY < rootView.locationTextView.y + rootView.locationTextView.height &&
                rootView.toolbarLayout.isVisible) rootView.toolbarLayout.hideWithFading()
        }
    }

    override fun onDestroyView() {
        rootView.swiperefresh.setOnRefreshListener(null)
        eventsListAdapter.apply {
            onEventClick = null
            onFavFabClick = null
            onHashtagClick = null
        }
        super.onDestroyView()
    }

    private fun moveToNotification() {
        startupViewModel.mutableNewNotifications.value = false
        findNavController(rootView).navigate(EventsFragmentDirections.actionEventsToNotification())
    }

    private fun openSearch(hashTag: String) {
            findNavController(rootView).navigate(EventsFragmentDirections.actionEventsToSearchResults(
                query = "",
                location = Preference().getString(SAVED_LOCATION).toString(),
                date = getString(R.string.anytime),
                type = hashTag))
    }

    private fun showNoInternetScreen(show: Boolean) {
        if (show) {
            rootView.shimmerEvents.isVisible = false
            rootView.eventsEmptyView.isVisible = false
            eventsListAdapter.clear()
        }
        rootView.noInternetCard.isVisible = show
    }

    private fun showEmptyMessage(show: Boolean) {
        rootView.eventsEmptyView.isVisible = show
    }

    private fun showResetPasswordAlertDialog(token: String) {
        val layout = layoutInflater.inflate(R.layout.dialog_reset_password, null)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_change_password))
            .setView(layout)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                startupViewModel.checkAndReset(token, layout.newPassword.text.toString())
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .setCancelable(false)
            .show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

        layout.newPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

                /* to make PasswordToggle visible again, if made invisible
                   after empty field error
                */
                if (!layout.textInputLayoutNewPassword.isEndIconVisible) {
                    layout.textInputLayoutNewPassword.isEndIconVisible = true
                }

                if (layout.newPassword.text.toString().length >= 8) {
                    layout.textInputLayoutNewPassword.error = null
                    layout.textInputLayoutNewPassword.isErrorEnabled = false
                } else {
                    layout.textInputLayoutNewPassword.error = getString(R.string.invalid_password_message)
                }
                if (layout.confirmNewPassword.text.toString() == layout.newPassword.text.toString()) {
                    layout.textInputLayoutConfirmNewPassword.error = null
                    layout.textInputLayoutConfirmNewPassword.isErrorEnabled = false
                } else {
                    layout.textInputLayoutConfirmNewPassword.error =
                        getString(R.string.invalid_confirm_password_message)
                }
                when (layout.textInputLayoutConfirmNewPassword.isErrorEnabled ||
                    layout.textInputLayoutNewPassword.isErrorEnabled) {
                    true -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    false -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { /*Implement here*/ }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { /*Implement here*/ }
        })

        layout.confirmNewPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

                /* to make PasswordToggle visible again, if made invisible
                   after empty field error
                 */
                if (!layout.textInputLayoutConfirmNewPassword.isEndIconVisible) {
                    layout.textInputLayoutConfirmNewPassword.isEndIconVisible = true
                }

                if (layout.confirmNewPassword.text.toString() == layout.newPassword.text.toString()) {
                    layout.textInputLayoutConfirmNewPassword.error = null
                    layout.textInputLayoutConfirmNewPassword.isErrorEnabled = false
                } else {
                    layout.textInputLayoutConfirmNewPassword.error =
                        getString(R.string.invalid_confirm_password_message)
                }
                when (layout.textInputLayoutConfirmNewPassword.isErrorEnabled ||
                    layout.textInputLayoutNewPassword.isErrorEnabled) {
                    true -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    false -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { /*Implement here*/ }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { /*Implement here*/ }
        })
    }

    override fun doubleClick() = rootView.scrollView.smoothScrollTo(0, 0)
}
