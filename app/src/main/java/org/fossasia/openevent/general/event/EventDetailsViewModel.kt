package org.fossasia.openevent.general.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.fossasia.openevent.general.BuildConfig.MAPBOX_KEY
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.auth.AuthHolder
import org.fossasia.openevent.general.auth.User
import org.fossasia.openevent.general.auth.UserId
import org.fossasia.openevent.general.common.SingleLiveEvent
import org.fossasia.openevent.general.connectivity.MutableConnectionLiveData
import org.fossasia.openevent.general.data.Resource
import org.fossasia.openevent.general.favorite.FavoriteEvent
import org.fossasia.openevent.general.feedback.Feedback
import org.fossasia.openevent.general.feedback.FeedbackService
import org.fossasia.openevent.general.order.Order
import org.fossasia.openevent.general.order.OrderService
import org.fossasia.openevent.general.sessions.Session
import org.fossasia.openevent.general.sessions.SessionService
import org.fossasia.openevent.general.social.SocialLinksService
import org.fossasia.openevent.general.social.SocialLink
import org.fossasia.openevent.general.speakers.Speaker
import org.fossasia.openevent.general.speakers.SpeakerService
import org.fossasia.openevent.general.sponsor.Sponsor
import org.fossasia.openevent.general.sponsor.SponsorService
import org.fossasia.openevent.general.utils.extensions.withDefaultSchedulers
import timber.log.Timber

class EventDetailsViewModel(
    private val eventService: EventService,
    private val authHolder: AuthHolder,
    private val speakerService: SpeakerService,
    private val sponsorService: SponsorService,
    private val sessionService: SessionService,
    private val socialLinksService: SocialLinksService,
    private val feedbackService: FeedbackService,
    private val resource: Resource,
    private val orderService: OrderService,
    private val mutableConnectionLiveData: MutableConnectionLiveData
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()

    val connection: LiveData<Boolean> = mutableConnectionLiveData
    private val mutableProgress = MutableLiveData<Boolean>()
    val progress: LiveData<Boolean> = mutableProgress
    private val mutableUser = MutableLiveData<User>()
    val user: LiveData<User> = mutableUser
    private val mutablePopMessage = SingleLiveEvent<String>()
    val popMessage: LiveData<String> = mutablePopMessage
    private val mutableEvent = MutableLiveData<Event>()
    val event: LiveData<Event> = mutableEvent
    private val mutableEventFeedback = MutableLiveData<List<Feedback>>()
    val eventFeedback: LiveData<List<Feedback>> = mutableEventFeedback
    private val mutableSubmittedFeedback = MutableLiveData<Feedback>()
    val submittedFeedback: LiveData<Feedback> = mutableSubmittedFeedback
    private val mutableEventSessions = MutableLiveData<List<Session>>()
    val eventSessions: LiveData<List<Session>> = mutableEventSessions
    private val mutableEventSpeakers = MutableLiveData<List<Speaker>>()
    val eventSpeakers: LiveData<List<Speaker>> = mutableEventSpeakers
    private val mutableEventSponsors = MutableLiveData<List<Sponsor>>()
    val eventSponsors: LiveData<List<Sponsor>> = mutableEventSponsors
    private val mutableSocialLinks = MutableLiveData<List<SocialLink>>()
    val socialLinks: LiveData<List<SocialLink>> = mutableSocialLinks
    private val mutableSimilarEvents = MutableLiveData<Set<Event>>()
    val similarEvents: LiveData<Set<Event>> = mutableSimilarEvents
    private val mutableOrders = MutableLiveData<List<Order>>()
    val orders: LiveData<List<Order>> = mutableOrders

    fun isLoggedIn() = authHolder.isLoggedIn()

    fun getId() = authHolder.getId()

    fun fetchEventFeedback(id: Long) {
        if (id == -1L) return

        compositeDisposable += feedbackService.getEventFeedback(id)
            .withDefaultSchedulers()
            .subscribe({
                mutableEventFeedback.value = it
            }, {
                Timber.e(it, "Error fetching events feedback")
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.feedback))
            })
    }

    fun submitFeedback(comment: String, rating: Float?, eventId: Long) {
        val feedback = Feedback(rating = rating.toString(), comment = comment,
            event = EventId(eventId), user = UserId(getId()))
        compositeDisposable += feedbackService.submitFeedback(feedback)
            .withDefaultSchedulers()
            .subscribe({
                mutablePopMessage.value = resource.getString(R.string.feedback_submitted)
                mutableSubmittedFeedback.value = it
            }, {
                mutablePopMessage.value = resource.getString(R.string.error_submitting_feedback)
            })
    }
    fun fetchEventSpeakers(id: Long) {
        if (id == -1L) return

        val query = """[{
                |   'and':[{
                |       'name':'is-featured',
                |       'op':'eq',
                |       'val':'true'
                |    }]
                |}]""".trimMargin().replace("'", "\"")

        compositeDisposable += speakerService.fetchSpeakersForEvent(id, query)
            .withDefaultSchedulers()
            .subscribe({
                mutableEventSpeakers.value = it
            }, {
                Timber.e(it, "Error fetching speaker for event id %d", id)
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.speakers))
            })
    }

    fun fetchSocialLink(id: Long) {
        if (id == -1L) return

        compositeDisposable += socialLinksService.getSocialLinks(id)
            .withDefaultSchedulers()
            .subscribe({
                mutableSocialLinks.value = it
            }, {
                Timber.e(it, "Error fetching social link for event id $id")
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.social_links))
            })
    }

    fun fetchSimilarEvents(eventId: Long, topicId: Long, location: String?) {
        if (eventId == -1L) return

        if (topicId != -1L) {
            compositeDisposable += eventService.getSimilarEvents(topicId)
                .withDefaultSchedulers()
                .distinctUntilChanged()
                .subscribe({ events ->
                    val list = events.filter { it.id != eventId }
                    val oldList = mutableSimilarEvents.value

                    val similarEventList = mutableSetOf<Event>()
                    similarEventList.addAll(list)
                    oldList?.let {
                        similarEventList.addAll(it)
                    }
                    mutableSimilarEvents.value = similarEventList
                }, {
                    Timber.e(it, "Error fetching similar events")
                    mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                        resource.getString(R.string.similar_events))
                })
        }

        compositeDisposable += eventService.getEventsByLocation(location)
            .withDefaultSchedulers()
            .distinctUntilChanged()
            .subscribe({ events ->
                val list = events.filter { it.id != eventId }
                val oldList = mutableSimilarEvents.value
                val similarEventList = mutableSetOf<Event>()
                similarEventList.addAll(list)
                oldList?.let {
                    similarEventList.addAll(it)
                }
                mutableSimilarEvents.value = similarEventList
            }, {
                Timber.e(it, "Error fetching similar events")
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.similar_events))
            })
    }

    fun fetchEventSponsors(id: Long) {
        if (id == -1L) return

        compositeDisposable += sponsorService.fetchSponsorsWithEvent(id)
            .withDefaultSchedulers()
            .subscribe({
                mutableEventSponsors.value = it
            }, {
                Timber.e(it, "Error fetching sponsor for event id %d", id)
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.sponsors))
            })
    }

    fun loadEvent(id: Long) {
        if (id == -1L) {
            mutablePopMessage.value = resource.getString(R.string.error_fetching_event_message)
            return
        }
        compositeDisposable += eventService.getEvent(id)
            .withDefaultSchedulers()
            .distinctUntilChanged()
            .doOnSubscribe {
                mutableProgress.value = true
            }.subscribe({
                mutableProgress.value = false
                mutableEvent.value = it
            }, {
                mutableProgress.value = false
                Timber.e(it, "Error fetching event %d", id)
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_message)
            })
    }

    fun loadEventByIdentifier(identifier: String) {
        if (identifier.isEmpty()) {
            mutablePopMessage.value = resource.getString(R.string.error_fetching_event_message)
            return
        }
        compositeDisposable += eventService.getEventByIdentifier(identifier)
            .withDefaultSchedulers()
            .doOnSubscribe {
                mutableProgress.value = true
            }.doFinally {
                mutableProgress.value = false
            }.subscribe({
                mutableEvent.value = it
            }, {
                Timber.e(it, "Error fetching event")
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_message)
            })
    }

    fun fetchEventSessions(id: Long) {
        if (id == -1L) return

        compositeDisposable += sessionService.fetchSessionForEvent(id)
            .withDefaultSchedulers()
            .subscribe({
                mutableEventSessions.value = it
            }, {
                mutablePopMessage.value = resource.getString(R.string.error_fetching_event_section_message,
                    resource.getString(R.string.sessions))
                Timber.e(it, "Error fetching events sessions")
            })
    }

    fun loadOrders() {
        if (!isLoggedIn())
            return
        compositeDisposable += orderService.getOrdersOfUser(getId())
            .withDefaultSchedulers()
            .subscribe({
                mutableOrders.value = it
            }, {
                Timber.e(it, "Error fetching orders")
            })
    }

    fun loadMap(event: Event): String {
        // location handling
        val BASE_URL = "https://api.mapbox.com/v4/mapbox.emerald/pin-l-marker+673ab7"
        val LOCATION = "(${event.longitude},${event.latitude})/${event.longitude},${event.latitude}"
        return "$BASE_URL$LOCATION,15/900x500.png?access_token=$MAPBOX_KEY"
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
                mutablePopMessage.value = resource.getString(R.string.add_event_to_shortlist_message)
            }, {
                mutablePopMessage.value = resource.getString(R.string.out_bad_try_again)
                Timber.d(it, "Fail on adding like for event ID ${event.id}")
            })
    }

    private fun removeFavorite(event: Event) {
        val favoriteEventId = event.favoriteEventId ?: return

        val favoriteEvent = FavoriteEvent(favoriteEventId, EventId(event.id))
        compositeDisposable += eventService.removeFavorite(favoriteEvent, event)
            .withDefaultSchedulers()
            .subscribe({
                mutablePopMessage.value = resource.getString(R.string.remove_event_from_shortlist_message)
            }, {
                mutablePopMessage.value = resource.getString(R.string.out_bad_try_again)
                Timber.d(it, "Fail on removing like for event ID ${event.id}")
            })
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
