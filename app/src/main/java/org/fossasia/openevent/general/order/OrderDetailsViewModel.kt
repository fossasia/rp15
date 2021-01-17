package org.fossasia.openevent.general.order

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.attendees.Attendee
import org.fossasia.openevent.general.auth.AuthHolder
import org.fossasia.openevent.general.common.SingleLiveEvent
import org.fossasia.openevent.general.data.Resource
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.EventService
import org.fossasia.openevent.general.utils.extensions.withDefaultSchedulers
import org.fossasia.openevent.general.utils.nullToEmpty
import timber.log.Timber

class OrderDetailsViewModel(
    private val eventService: EventService,
    private val orderService: OrderService,
    private val authHolder: AuthHolder,
    private val resource: Resource
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()
    val message = SingleLiveEvent<String>()
    private val mutableEvent = MutableLiveData<Event>()
    val event: LiveData<Event> = mutableEvent
    private val mutableAttendees = MutableLiveData<List<Attendee>>()
    val attendees: LiveData<List<Attendee>> = mutableAttendees
    private val mutableProgress = MutableLiveData<Boolean>()
    val progress: LiveData<Boolean> = mutableProgress
    var currentTicketPosition: Int = 0
    var brightness: Float? = 0f

    fun loadEvent(id: Long) {
        if (id == -1L) {
            throw IllegalStateException("ID should never be -1")
        }

        compositeDisposable += eventService.getEventById(id)
            .withDefaultSchedulers()
            .subscribe({
                mutableEvent.value = it
            }, {
                Timber.e(it, "Error fetching event %d", id)
                message.value = resource.getString(R.string.error_fetching_event_message)
            })
    }

    fun getToken() = authHolder.getAccessAuthorization().nullToEmpty()

    fun loadAttendeeDetails(orderId: Long) {
        if (orderId == -1L) return

        compositeDisposable += orderService
            .getOrderById(orderId)
            .flatMap { order ->
                orderService.getAttendeesUnderOrder(order.attendees.map { it.id })
            }
            .withDefaultSchedulers()
            .doOnSubscribe {
                mutableProgress.value = true
            }.doFinally {
                mutableProgress.value = false
            }.subscribe({
                mutableAttendees.value = it
                Timber.d("Fetched attendees of size %s", it)
            }, {
                Timber.e(it, "Error fetching attendee details")
                message.value = resource.getString(R.string.error_fetching_attendee_details_message)
            })
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
