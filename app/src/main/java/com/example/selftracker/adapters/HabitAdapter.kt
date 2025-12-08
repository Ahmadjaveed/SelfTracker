package com.example.selftracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.selftracker.R
import com.example.selftracker.models.Habit
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class HabitAdapter(
    private val onCompleteHabit: (Habit) -> Unit,
    private val onEditHabit: (Habit) -> Unit,
    private val onDeleteHabit: (Habit) -> Unit,
    private val onShowDetails: (Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.HabitViewHolder>(HabitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_habit_card, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = getItem(position)
        holder.bind(habit)
    }

    inner class HabitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val habitName: TextView = itemView.findViewById(R.id.habit_name)
        private val habitTarget: TextView = itemView.findViewById(R.id.habit_target)
        private val habitStreak: TextView = itemView.findViewById(R.id.habit_streak)
        private val habitBestStreak: TextView = itemView.findViewById(R.id.habit_best_streak)

        private val progressBar: LinearProgressIndicator = itemView.findViewById(R.id.progress_bar)
        private val btnCompleteHabit: MaterialButton = itemView.findViewById(R.id.btn_complete_habit)


        init {
            btnCompleteHabit.setOnClickListener {
                val habit = getItem(adapterPosition)
                onCompleteHabit(habit)
            }



            itemView.setOnClickListener {
                val habit = getItem(adapterPosition)
                onShowDetails(habit)
            }
        }

        fun bind(habit: Habit) {
            habitName.text = habit.name
            habitTarget.text = "Target: ${habit.targetValue} ${habit.unit}"
            habitStreak.text = "${habit.currentStreak}" // Just the number
            habitBestStreak.text = "${habit.bestStreak}"
            
            // Dynamic Icon
            val iconRes = if (habit.unit.lowercase() in listOf("min", "mins", "minute", "minutes", "hour", "hours", "hr", "hrs", "time")) {
                R.drawable.ic_habit_time
            } else {
                R.drawable.ic_habit_count
            }
            itemView.findViewById<ImageView>(R.id.icon_habit).setImageResource(iconRes)

            // Determine if completed today
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val isCompletedToday = habit.lastCompletedDate == today

            if (isCompletedToday) {
                // Completed State
                btnCompleteHabit.setIconResource(R.drawable.ic_check_circle_filled)
                btnCompleteHabit.setBackgroundColor(itemView.context.getColor(R.color.success_container))
                btnCompleteHabit.iconTint = android.content.res.ColorStateList.valueOf(itemView.context.getColor(R.color.success_dark))
                btnCompleteHabit.alpha = 1.0f
                progressBar.progress = 100
            } else {
                // Pending State
                btnCompleteHabit.setIconResource(R.drawable.ic_check)
                btnCompleteHabit.setBackgroundColor(itemView.context.getColor(R.color.primary_container))
                btnCompleteHabit.iconTint = android.content.res.ColorStateList.valueOf(itemView.context.getColor(R.color.primary))
                btnCompleteHabit.alpha = 1.0f
                progressBar.progress = 0 // Or partial if you tracked partials, currently binary
            }

            // Click listener with animation
            btnCompleteHabit.setOnClickListener {
                // Bounce animation
                btnCompleteHabit.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(100)
                    .withEndAction {
                        btnCompleteHabit.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                        
                        // Action
                        onCompleteHabit(habit)
                    }
                    .start()
            }
        }




    }
}

class HabitDiffCallback : DiffUtil.ItemCallback<Habit>() {
    override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean {
        return oldItem.habitId == newItem.habitId
    }

    override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean {
        return oldItem == newItem
    }
}