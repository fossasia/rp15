package org.fossasia.openevent.general.ticket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.fossasia.openevent.general.auth.AuthHolder
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.EventService
import timber.log.Timber

class TicketsViewModel(
    private val ticketService: TicketService,
    private val eventService: EventService,
    private val authHolder: AuthHolder
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()

    private val mutableProgressTickets = MutableLiveData<Boolean>()
    val progressTickets: LiveData<Boolean> = mutableProgressTickets
    val tickets = MutableLiveData<List<Ticket>>()
    private val mutableError = MutableLiveData<String>()
    val error: LiveData<String> = mutableError
    private val mutableEvent = MutableLiveData<Event>()
    val event: LiveData<Event> = mutableEvent
    private val mutableTicketTableVisibility = MutableLiveData<Boolean>()
    val ticketTableVisibility: LiveData<Boolean> = mutableTicketTableVisibility
    private val mutableTicketIdAndQty = MutableLiveData<ArrayList<Pair<Int, Int>>>()
    val ticketIdAndQty = mutableTicketIdAndQty

    fun isLoggedIn() = authHolder.isLoggedIn()

    fun loadTickets(id: Long) {
        if (id == -1L) {
            mutableError.value = "Error fetching tickets"
            return
        }
        compositeDisposable.add(ticketService.getTickets(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                mutableProgressTickets.value = true
            }.subscribe({ ticketList ->
                mutableProgressTickets.value = false
                mutableTicketTableVisibility.value = ticketList.isNotEmpty()
                tickets.value = ticketList
            }, {
                mutableError.value = "Error fetching tickets"
                Timber.e(it, "Error fetching tickets %d", id)
            })
        )
    }

    fun loadEvent(id: Long) {
        if (id == -1L) {
            throw IllegalStateException("ID should never be -1")
        }
        compositeDisposable.add(eventService.getEvent(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                mutableEvent.value = it
            }, {
                Timber.e(it, "Error fetching event %d", id)
                mutableError.value = "Error fetching event"
            })
        )
    }

    fun totalTicketsEmpty(ticketIdAndQty: List<Pair<Int, Int>>): Boolean {
        return ticketIdAndQty.sumBy { it.second } == 0
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
    fun handleTicketSelect(id: Int, quantity: Int) {
        val pos = mutableTicketIdAndQty.value?.map { it.first }?.indexOf(id) ?: -1
        if (pos == -1) {
            mutableTicketIdAndQty.value?.add(Pair(id, quantity))
        } else {
            mutableTicketIdAndQty.value?.set(pos, Pair(id, quantity))
        }
    }
}
