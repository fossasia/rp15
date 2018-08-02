package org.fossasia.openevent.general.order

import android.support.v7.widget.RecyclerView
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_card_order.view.*
import org.fossasia.openevent.general.R
import org.fossasia.openevent.general.event.Event
import org.fossasia.openevent.general.event.EventUtils

class OrdersViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(event: Event, clickListener: OrdersRecyclerAdapter.OrderClickListener?, orderIdentifier: String?) {
        val formattedDateTime = EventUtils.getLocalizedDateTime(event.startsAt)
        val formattedTime = EventUtils.getFormattedTime(formattedDateTime)
        val timezone = EventUtils.getFormattedTimeZone(formattedDateTime)

        itemView.eventName.text = event.name
        itemView.time.text = "Starts at $formattedTime $timezone"
        itemView.setOnClickListener {
            orderIdentifier?.let { it1 -> clickListener?.onClick(event.id, it1) }
        }

        itemView.date.text = formattedDateTime.dayOfMonth.toString()
        itemView.month.text = formattedDateTime.month.name.slice(0 until 3)

        event.originalImageUrl?.let {
            Picasso.get()
                    .load(it)
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(itemView.eventImage)
        }
    }
}