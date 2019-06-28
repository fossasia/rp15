package org.fossasia.openevent.general.search

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.auth.AuthHolder
import org.fossasia.openevent.general.utils.extensions.withDefaultSchedulers
import org.fossasia.openevent.general.common.SingleLiveEvent
import org.fossasia.openevent.general.connectivity.MutableConnectionLiveData
import org.fossasia.openevent.general.data.Preference
import org.fossasia.openevent.general.data.Resource
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.EventId
import org.fossasia.openevent.general.event.EventService
import org.fossasia.openevent.general.event.EventUtils
import org.fossasia.openevent.general.event.types.EventType
import org.fossasia.openevent.general.search.location.SAVED_LOCATION
import org.fossasia.openevent.general.search.time.SAVED_TIME
import org.fossasia.openevent.general.search.type.SAVED_TYPE
import org.fossasia.openevent.general.favorite.FavoriteEvent
import org.fossasia.openevent.general.utils.DateTimeUtils.getNextDate
import org.fossasia.openevent.general.utils.DateTimeUtils.getNextMonth
import org.fossasia.openevent.general.utils.DateTimeUtils.getNextToNextDate
import org.fossasia.openevent.general.utils.DateTimeUtils.getNextToNextMonth
import org.fossasia.openevent.general.utils.DateTimeUtils.getNextToWeekendDate
import org.fossasia.openevent.general.utils.DateTimeUtils.getWeekendDate
import timber.log.Timber
import java.lang.StringBuilder
import java.util.Date

class SearchViewModel(
    private val eventService: EventService,
    private val preference: Preference,
    private val mutableConnectionLiveData: MutableConnectionLiveData,
    private val resource: Resource,
    private val authHolder: AuthHolder
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()

    private val mutableShowShimmerResults = MutableLiveData<Boolean>()
    val showShimmerResults: LiveData<Boolean> = mutableShowShimmerResults
    private val mutableEvents = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = mutableEvents
    private val mutableMessage = SingleLiveEvent<String>()
    val message: LiveData<String> = mutableMessage
    val connection: LiveData<Boolean> = mutableConnectionLiveData
    var searchEvent: String? = null
    var savedLocation: String? = null
    var savedType: String? = null
    var savedTime: String? = null
    private val savedNextDate = getNextDate()
    private val savedNextToNextDate = getNextToNextDate()
    private val savedWeekendDate = getWeekendDate()
    private val savedWeekendNextDate = getNextToWeekendDate()
    private val savedNextMonth = getNextMonth()
    private val savedNextToNextMonth = getNextToNextMonth()
    private val mutableEventTypes = MutableLiveData<List<EventType>>()
    val eventTypes: LiveData<List<EventType>> = mutableEventTypes
    var isQuerying = false
    private val recentSearches = mutableListOf<Pair<String, String>>()

    fun getRecentSearches(): List<Pair<String, String>> {
        val searchesStrings = preference.getString(RECENT_SEARCHES, "")
        if (recentSearches.isNotEmpty()) recentSearches.clear()
        if (!searchesStrings.isNullOrEmpty()) {
            val searches = searchesStrings.split(",")
            searches.forEach {
                val searchAndLocation = it.split("/")
                recentSearches.add(Pair(searchAndLocation[0], searchAndLocation[1]))
            }
        }
        return recentSearches
    }

    fun saveRecentSearch(query: String, location: String, position: Int = 0) {
        if (query.isEmpty() || location.isEmpty() || location == resource.getString(R.string.enter_location)) return
        recentSearches.add(position, Pair(query, location))
        saveRecentSearchToPreference()
    }

    fun removeRecentSearch(position: Int) {
        recentSearches.removeAt(position)
        saveRecentSearchToPreference()
    }

    private fun saveRecentSearchToPreference() {
        val builder = StringBuilder()
        for ((index, pair) in recentSearches.withIndex()) {
            builder.append(pair.first).append("/").append(pair.second)
            if (index != recentSearches.size - 1) builder.append(",")
        }
        Timber.d("DEBUGGING SAVED: $builder")
        preference.putString(RECENT_SEARCHES, builder.toString())
    }

    fun isLoggedIn() = authHolder.isLoggedIn()

    fun loadEventTypes() {
        compositeDisposable += eventService.getEventTypes()
            .withDefaultSchedulers()
            .subscribe({
                mutableEventTypes.value = it
            }, {
                Timber.e(it, "Error fetching events types")
            })
    }

    fun loadSavedLocation() {
        savedLocation = preference.getString(SAVED_LOCATION) ?: resource.getString(R.string.enter_location)
    }
    fun loadSavedType() {
        savedType = preference.getString(SAVED_TYPE)
    }
    fun loadSavedTime() {
        savedTime = preference.getString(SAVED_TIME)
    }

    fun loadEvents(
        location: String,
        time: String,
        type: String,
        freeEvents: Boolean,
        sortBy: String,
        sessionsAndSpeakers: Boolean,
        callForSpeakers: Boolean
    ) {
        if (mutableEvents.value != null) {
            return
        }
        if (!isConnected()) return
        preference.putString(SAVED_LOCATION, location)

        val freeStuffFilter = if (freeEvents)
            """, {
               |    'name':'tickets',
               |    'op':'any',
               |    'val':{
               |        'name':'price',
               |        'op':'eq',
               |        'val':'0'
               |    }
               |}, {
               |       'name':'ends-at',
               |       'op':'ge',
               |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
               |    }
            """.trimIndent()
            else ""
        val sessionsAndSpeakersFilter = if (sessionsAndSpeakers)
            """, {
               |       'name':'is-sessions-speakers-enabled',
               |       'op':'eq',
               |       'val':'true'
               |    }
            """.trimIndent()
            else ""
        val query: String = when {
            TextUtils.isEmpty(location) -> """[{
                |   'name':'name',
                |   'op':'ilike',
                |   'val':'%$searchEvent%'
                |}, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }]""".trimMargin().replace("'", "'")
            time == "Anytime" && type == "Anything" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |    }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |    }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
            time == "Anytime" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |    }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |    }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |    }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
            time == "Today" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |   }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'ge',
                |       'val':'$time%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'lt',
                |       'val':'$savedNextDate%'
                |   }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |   }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
            time == "Tomorrow" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |   }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'ge',
                |       'val':'$savedNextDate%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'lt',
                |       'val':'$savedNextToNextDate%'
                |   }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |   }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
            time == "This weekend" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |   }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'ge',
                |       'val':'$savedWeekendDate%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'lt',
                |       'val':'$savedWeekendNextDate%'
                |   }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |   }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
            time == "In the next month" -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |   }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'ge',
                |       'val':'$savedNextMonth%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'lt',
                |       'val':'$savedNextToNextMonth%'
                |   }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |   }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")

            else -> """[{
                |   'and':[{
                |       'name':'location-name',
                |       'op':'ilike',
                |       'val':'%$location%'
                |   }, {
                |       'name':'name',
                |       'op':'ilike',
                |       'val':'%$searchEvent%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'ge',
                |       'val':'$time%'
                |   }, {
                |       'name':'starts-at',
                |       'op':'lt',
                |       'val':'$savedNextDate%'
                |   }, {
                |       'name':'event-type',
                |       'op':'has',
                |       'val': {
                |       'name':'name',
                |       'op':'eq',
                |       'val':'$type'
                |       }
                |   }, {
                |       'name':'ends-at',
                |       'op':'ge',
                |       'val':'%${EventUtils.getTimeInISO8601(Date())}%'
                |    }$freeStuffFilter
                |    $sessionsAndSpeakersFilter]
                |}]""".trimMargin().replace("'", "\"")
        }
        Timber.e(query)
        compositeDisposable += eventService.getSearchEvents(query, sortBy)
            .withDefaultSchedulers()
            .distinctUntilChanged()
            .doOnSubscribe {
                mutableShowShimmerResults.value = true
            }.doFinally {
                stopLoaders()
            }.subscribe({
                stopLoaders()
                mutableEvents.value = if (callForSpeakers) it.filter { it.speakersCall != null } else it
            }, {
                stopLoaders()
                Timber.e(it, "Error fetching events")
                mutableMessage.value = resource.getString(R.string.error_fetching_events_message)
            })
    }

    private fun stopLoaders() {
        mutableShowShimmerResults.value = false
    }

    fun setFavorite(event: Event, favorite: Boolean) {
        if (favorite) {
            addFavorite(event)
        } else {
            removeFavorite(event)
        }
    }

    private fun addFavorite(event: Event) {
        val favoriteEvent = FavoriteEvent(authHolder.getId(), EventId(event.id))
        compositeDisposable += eventService.addFavorite(favoriteEvent, event)
            .withDefaultSchedulers()
            .subscribe({
                mutableMessage.value = resource.getString(R.string.add_event_to_shortlist_message)
            }, {
                mutableMessage.value = resource.getString(R.string.out_bad_try_again)
                Timber.d(it, "Fail on adding like for event ID ${event.id}")
            })
    }

    private fun removeFavorite(event: Event) {
        val favoriteEventId = event.favoriteEventId ?: return

        val favoriteEvent = FavoriteEvent(favoriteEventId, EventId(event.id))
        compositeDisposable += eventService.removeFavorite(favoriteEvent, event)
            .withDefaultSchedulers()
            .subscribe({
                mutableMessage.value = resource.getString(R.string.remove_event_from_shortlist_message)
            }, {
                mutableMessage.value = resource.getString(R.string.out_bad_try_again)
                Timber.d(it, "Fail on removing like for event ID ${event.id}")
            })
    }

    fun isConnected(): Boolean = mutableConnectionLiveData.value ?: false

    fun clearEvents() {
        mutableEvents.value = null
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
