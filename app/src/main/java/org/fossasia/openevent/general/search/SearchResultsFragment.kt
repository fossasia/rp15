package org.fossasia.openevent.general.search

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.content_no_internet.view.noInternetCard
import kotlinx.android.synthetic.main.content_no_internet.view.retry
import kotlinx.android.synthetic.main.fragment_search_results.view.chipGroup
import kotlinx.android.synthetic.main.fragment_search_results.view.chipGroupLayout
import kotlinx.android.synthetic.main.fragment_search_results.view.eventsRecycler
import kotlinx.android.synthetic.main.fragment_search_results.view.noSearchResults
import kotlinx.android.synthetic.main.fragment_search_results.view.shimmerSearch
import kotlinx.android.synthetic.main.fragment_search_results.view.toolbar
import kotlinx.android.synthetic.main.fragment_search_results.view.searchText
import kotlinx.android.synthetic.main.fragment_search_results.view.filter
import kotlinx.android.synthetic.main.fragment_search_results.view.clearSearchText
import kotlinx.android.synthetic.main.fragment_search_results.view.scrollView
import kotlinx.android.synthetic.main.fragment_search_results.view.toolbarTitle
import kotlinx.android.synthetic.main.fragment_search_results.view.appBar
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.common.EventClickListener
import org.fossasia.openevent.general.common.FavoriteFabClickListener
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.types.EventType
import org.fossasia.openevent.general.favorite.FavoriteEventsRecyclerAdapter
import org.fossasia.openevent.general.utils.Utils.setToolbar
import org.fossasia.openevent.general.utils.extensions.nonNull
import org.jetbrains.anko.design.longSnackbar
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import androidx.appcompat.view.ContextThemeWrapper
import androidx.navigation.fragment.FragmentNavigatorExtras
import com.google.android.material.appbar.AppBarLayout
import org.fossasia.openevent.general.utils.Utils.hideSoftKeyboard
import org.fossasia.openevent.general.utils.Utils.showSoftKeyboard
import org.fossasia.openevent.general.utils.extensions.setPostponeSharedElementTransition
import org.fossasia.openevent.general.utils.extensions.setStartPostponedEnterTransition

class SearchResultsFragment : Fragment(), CompoundButton.OnCheckedChangeListener {

    private lateinit var rootView: View
    private val searchViewModel by viewModel<SearchViewModel>()
    private val safeArgs: SearchResultsFragmentArgs by navArgs()
    private val favoriteEventsRecyclerAdapter = FavoriteEventsRecyclerAdapter()

    private lateinit var days: Array<String>
    private lateinit var eventDate: String
    private lateinit var eventType: String
    private var eventTypesList: List<EventType>? = arrayListOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        days = resources.getStringArray(R.array.days)
        eventDate = searchViewModel.savedTime ?: safeArgs.date
        eventType = searchViewModel.savedType ?: safeArgs.type

        searchViewModel.loadEventTypes()
        searchViewModel.eventTypes
            .nonNull()
            .observe(this, Observer { list ->
                eventTypesList = list
            })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_search_results, container, false)
        setPostponeSharedElementTransition()
        setupToolbar()
        setChips()

        rootView.eventsRecycler.layoutManager = LinearLayoutManager(context)

        rootView.eventsRecycler.adapter = favoriteEventsRecyclerAdapter
        rootView.eventsRecycler.isNestedScrollingEnabled = false
        rootView.viewTreeObserver.addOnDrawListener {
            setStartPostponedEnterTransition()
        }

        rootView.searchText.setText(safeArgs.query)
        rootView.clearSearchText.isVisible = !rootView.searchText.text.isNullOrBlank()

        searchViewModel.events
            .nonNull()
            .observe(this, Observer { list ->
                favoriteEventsRecyclerAdapter.submitList(list)
                showNoSearchResults(list)
                Timber.d("Fetched events of size %s", favoriteEventsRecyclerAdapter.itemCount)
            })

        searchViewModel.showShimmerResults
            .nonNull()
            .observe(this, Observer {
                if (it) {
                    rootView.shimmerSearch.startShimmer()
                } else {
                    rootView.shimmerSearch.stopShimmer()
                }
                rootView.shimmerSearch.isVisible = it
            })

        searchViewModel.connection
            .nonNull()
            .observe(this, Observer { isConnected ->
                if (isConnected) {
                    showNoInternetError(false)
                    val currentEvents = searchViewModel.events.value
                    if (currentEvents == null) performSearch()
                } else {
                    showNoInternetError(searchViewModel.events.value == null)
                }
            })

        searchViewModel.error
            .nonNull()
            .observe(this, Observer {
                rootView.longSnackbar(it)
            })

        rootView.retry.setOnClickListener {
            performSearch()
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideSoftKeyboard(context, rootView)
        favoriteEventsRecyclerAdapter.apply {
            onEventClick = null
            onFavFabClick = null
        }
    }

    private fun setChips(date: String = eventDate, type: String = eventType) {
        if (rootView.chipGroup.childCount> 0) {
            rootView.chipGroup.removeAllViews()
        }
            when {
            date != getString(R.string.anytime) && type != getString(R.string.anything) -> {
                addChips(date, true)
                addChips(type, true)
            }
            date != getString(R.string.anytime) && type == getString(R.string.anything) -> {
                addChips(date, true)
                searchViewModel.eventTypes
                    .nonNull()
                    .observe(this, Observer { list ->
                        list.forEach {
                            addChips(it.name, false)
                        }
                    })
            }
            date == getString(R.string.anytime) && type != getString(R.string.anything) -> {
                addChips(type, true)
                days.forEach {
                    addChips(it, false)
                }
            }
            else -> {
                days.forEach {
                    addChips(it, false)
                }
            }
        }
    }

    private fun setupToolbar() {
        setToolbar(activity, show = false)
        rootView.toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
        rootView.filter.setOnClickListener {
            findNavController(rootView)
                .navigate(SearchResultsFragmentDirections.actionSearchResultsToSearchFilter(
                    date = safeArgs.date,
                    freeEvents = safeArgs.freeEvents,
                    location = safeArgs.location,
                    type = safeArgs.type,
                    query = safeArgs.query,
                    sort = safeArgs.sort,
                    sessionsAndSpeakers = safeArgs.sessionsAndSpeakers,
                    callForSpeakers = safeArgs.callForSpeakers))
        }

        rootView.clearSearchText.setOnClickListener {
            rootView.searchText.setText("")
            performSearch("")
        }
    }

    private fun addChips(name: String, checked: Boolean) {
        val newContext = ContextThemeWrapper(context, R.style.CustomChipChoice)
        val chip = Chip(newContext)
        chip.text = name
        chip.isCheckable = true
        chip.isChecked = checked
        chip.isClickable = true
        if (checked) {
            chip.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        }
        chip.setOnCheckedChangeListener(this)
        rootView.chipGroup.addView(chip)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val eventClickListener: EventClickListener = object : EventClickListener {
            override fun onClick(eventID: Long, imageView: ImageView) {
                findNavController(rootView)
                    .navigate(SearchResultsFragmentDirections.actionSearchResultsToEventDetail(eventID),
                        FragmentNavigatorExtras(imageView to "eventDetailImage"))
            }
        }
        val favFabClickListener: FavoriteFabClickListener = object : FavoriteFabClickListener {
            override fun onClick(event: Event, itemPosition: Int) {
                searchViewModel.setFavorite(event.id, !event.favorite)
                event.favorite = !event.favorite
                favoriteEventsRecyclerAdapter.notifyItemChanged(itemPosition)
            }
        }

        favoriteEventsRecyclerAdapter.apply {
            onEventClick = eventClickListener
            onFavFabClick = favFabClickListener
        }

        rootView.searchText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER) {
                performSearch(rootView.searchText.text.toString())
                true
            } else {
                false
            }
        }

        rootView.searchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /*Do Nothing*/ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /*Do Nothing*/ }
            override fun afterTextChanged(s: Editable?) {
                rootView.clearSearchText.visibility = if (s.toString().isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })

        rootView.appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, offset ->
            if (Math.abs(offset) == appBarLayout.totalScrollRange) {
                rootView.toolbarTitle.text = if (rootView.searchText.text.isNullOrBlank())
                    getString(R.string.search_hint) else rootView.searchText.text.toString()
            } else {
                rootView.toolbarTitle.text = ""
            }
        })

        rootView.toolbar.toolbarTitle.setOnClickListener {
            rootView.scrollView.scrollTo(0, 0)
            rootView.appBar.setExpanded(true, true)
            showSoftKeyboard(context, rootView)
            rootView.searchText.isFocusable = true
        }
    }

    private fun performSearch(query: String = safeArgs.query) {
        searchViewModel.clearEvents()
        val location = safeArgs.location
        val type = eventType
        val date = eventDate
        val freeEvents = safeArgs.freeEvents
        val sortBy = safeArgs.sort
        val callForSpeakers = safeArgs.callForSpeakers

        val sessionsAndSpeakers = safeArgs.sessionsAndSpeakers
        searchViewModel.searchEvent = query
        searchViewModel.loadEvents(location, date, type, freeEvents, sortBy, sessionsAndSpeakers, callForSpeakers)
        hideSoftKeyboard(requireContext(), rootView)
    }

    private fun showNoSearchResults(events: List<Event>) {
        rootView.noSearchResults.isVisible = events.isEmpty()
    }

    private fun showNoInternetError(show: Boolean) {
        rootView.noInternetCard.isVisible = show
        rootView.chipGroupLayout.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        days.forEach {
            if (it == buttonView?.text) {
                searchViewModel.savedTime = if (isChecked) it else null
                eventDate = if (isChecked) it else getString(R.string.anytime)
                setChips(date = it)
                refreshEvents()
                return@forEach
            }
        }
        eventTypesList?.forEach {
            if (it.name == buttonView?.text) {
                searchViewModel.savedType = if (isChecked) it.name else null
                eventType = if (isChecked) it.name else getString(R.string.anything)
                refreshEvents()
                return@forEach
            }
        }
    }

    private fun refreshEvents() {
        setChips()
        rootView.noSearchResults.isVisible = false
        favoriteEventsRecyclerAdapter.submitList(null)
        searchViewModel.clearEvents()
        if (searchViewModel.isConnected()) performSearch()
        else showNoInternetError(true)
    }
}
