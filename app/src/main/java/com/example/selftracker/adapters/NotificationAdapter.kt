package com.example.selftracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.selftracker.R
import com.example.selftracker.models.Notification
import java.util.Date
import android.text.format.DateUtils

class NotificationAdapter : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = getItem(position)
        holder.bind(notification)
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.notification_title)
        private val message: TextView = itemView.findViewById(R.id.notification_message)
        private val time: TextView = itemView.findViewById(R.id.notification_time)
        private val icon: ImageView = itemView.findViewById(R.id.icon_notification_type)
        private val statusDot: ImageView = itemView.findViewById(R.id.status_dot)

        fun bind(notification: Notification) {
            title.text = notification.title
            message.text = notification.message
            
            // Format time relative (e.g. "2 mins ago")
            val now = System.currentTimeMillis()
            time.text = DateUtils.getRelativeTimeSpanString(notification.timestamp, now, DateUtils.MINUTE_IN_MILLIS)

            // Icon logic based on type
            val iconRes = when(notification.type) {
                 "HABIT" -> R.drawable.ic_habits_nav
                 "GOAL", "STEP", "SUBSTEP" -> R.drawable.ic_rocket_nav
                 "STREAK_FREEZE" -> R.drawable.ic_check // Or snowflake if available
                 "INACTIVITY" -> R.drawable.ic_progress_nav
                 else -> R.drawable.ic_notifications
            }
            icon.setImageResource(iconRes)
            
            // New indicator
            statusDot.visibility = if (!notification.isRead) View.VISIBLE else View.GONE
            
            // Dim read notifications slightly
            itemView.alpha = if (notification.isRead) 0.7f else 1.0f
        }
    }
}

class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
    override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
        return oldItem == newItem
    }
}
