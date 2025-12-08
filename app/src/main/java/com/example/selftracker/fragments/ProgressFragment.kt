package com.example.selftracker.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.selftracker.R
import com.example.selftracker.database.SelfTrackerDatabase
import com.example.selftracker.models.Habit
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ProgressFragment : Fragment() {

    private lateinit var progressContainer: LinearLayout
    private lateinit var database: SelfTrackerDatabase
    private lateinit var totalHabitsCount: TextView
    private lateinit var completedTodayCount: TextView
    private lateinit var successRate: TextView
    private lateinit var currentMonthText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressContainer = view.findViewById(R.id.progress_container)
        totalHabitsCount = view.findViewById(R.id.total_habits_count)
        completedTodayCount = view.findViewById(R.id.completed_today_count)
        successRate = view.findViewById(R.id.success_rate)
        currentMonthText = view.findViewById(R.id.current_month_text)

        database = SelfTrackerDatabase.getDatabase(requireContext())

        // Set current month
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        currentMonthText.text = monthFormat.format(Date())

        loadProgress()
    }

    private fun loadProgress() {
        val habitId = arguments?.getInt("HABIT_ID", -1) ?: -1

        viewLifecycleOwner.lifecycleScope.launch {
            if (habitId != -1) {
                // Load single habit
                val habit = withContext(Dispatchers.IO) {
                    database.habitDao().getHabitByIdSync(habitId)
                }
                progressContainer.removeAllViews()
                if (habit != null) {
                    // Update stats for just this habit (make list of 1)
                    updateSummaryStats(listOf(habit))
                    addProgressCard(habit, 0)
                } else {
                    showEmptyState()
                }
            } else {
                // Load all habits
                database.habitDao().getAllHabits().collect { habits ->
                    progressContainer.removeAllViews()
                    if (habits.isEmpty()) {
                        showEmptyState()
                    } else {
                        updateSummaryStats(habits)
                        habits.forEachIndexed { index, habit ->
                            addProgressCard(habit, index)
                        }
                    }
                }
            }
        }
    }

    private fun updateSummaryStats(habits: List<Habit>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val totalHabits = habits.size

            // Calculate completed today
            val completedToday = withContext(Dispatchers.IO) {
                habits.count { habit ->
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // For demo purposes, let's assume habits with current streak > 0 are completed today
                    // In real app, you'd check your habit completion records
                    habit.currentStreak > 0
                }
            }

            // Calculate success rate (simplified - using streak as indicator)
            val totalStreak = habits.sumOf { it.currentStreak }
            val maxPossibleStreak = habits.size * 30 // Assuming 30 days max
            val averageSuccessRate = if (maxPossibleStreak > 0) {
                (totalStreak * 100 / maxPossibleStreak).coerceAtMost(100)
            } else 0

            totalHabitsCount.text = totalHabits.toString()
            completedTodayCount.text = completedToday.toString()
            successRate.text = "$averageSuccessRate%"

            // Animate number changes
            animateNumberChange(totalHabitsCount, totalHabits)
            animateNumberChange(completedTodayCount, completedToday)
            animateNumberChange(successRate, averageSuccessRate)
        }
    }

    private fun animateNumberChange(textView: TextView, targetValue: Int) {
        val currentText = textView.text.toString()
        val currentValue = if (currentText.replace("%", "").toIntOrNull() != null) {
            currentText.replace("%", "").toInt()
        } else 0

        val animator = ObjectAnimator.ofInt(currentValue, targetValue)
        animator.duration = 1000
        animator.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            textView.text = if (textView == successRate) "$value%" else value.toString()
        }
        animator.start()
    }

    private fun showEmptyState() {
        val emptyView = LayoutInflater.from(requireContext()).inflate(R.layout.item_empty_progress, progressContainer, false)
        progressContainer.addView(emptyView)
    }

    private fun addProgressCard(habit: Habit, index: Int) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_progress_card, progressContainer, false)

        val nameText = view.findViewById<TextView>(R.id.progress_habit_name)
        val currentStreak = view.findViewById<TextView>(R.id.progress_current_streak)
        val bestStreak = view.findViewById<TextView>(R.id.progress_best_streak)
        val completedText = view.findViewById<TextView>(R.id.progress_completed)
        val streakBadge = view.findViewById<TextView>(R.id.streak_badge)
        val calendarMonthText = view.findViewById<TextView>(R.id.calendar_month_text)
        val calendarGrid = view.findViewById<GridLayout>(R.id.calendar_grid)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val progressPercentage = view.findViewById<TextView>(R.id.progress_percentage)

        // Set basic habit info
        nameText.text = habit.name
        currentStreak.text = habit.currentStreak.toString()
        bestStreak.text = habit.bestStreak.toString()

        // Fetch Logs for this habit
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.habitLogDao().getLogsByHabit(habit.habitId).collect { logs ->
                withContext(Dispatchers.Main) {
                    val completedLogs = logs.filter { it.isCompleted }
                    val totalCompletions = completedLogs.size

                    completedText.text = totalCompletions.toString()

                    // Set streak badge with appropriate emoji and logic for > 1 month
                    val streakText = when {
                        habit.currentStreak >= 30 -> {
                            val months = habit.currentStreak / 30
                            val days = habit.currentStreak % 30
                            if (days > 0) "🔥 $months months $days days" else "🔥 $months months"
                        }
                        habit.currentStreak >= 7 -> "⚡ ${habit.currentStreak} days"
                        else -> "✨ ${habit.currentStreak} days"
                    }
                    streakBadge.text = streakText

                    // Calculate monthly progress
                    val calendar = Calendar.getInstance()
                    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                    
                    // Filter logs for current month to calculate progress percentage correctly
                    val currentMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val currentMonthStr = currentMonthFormat.format(calendar.time)
                    val completedThisMonth = completedLogs.count { it.date.startsWith(currentMonthStr) }

                    // Set Month Text (e.g. December 2025)
                    val monthHeaderFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    calendarMonthText.text = monthHeaderFormat.format(calendar.time)

                    // Calculate "Active Days" in this month
                    // User Request: "according to each date, out of the month"
                    // Simplified: (Completed This Month / Current Day)
                    // This creates a strict "Month-to-Date" consistency score.
                    
                    val progress = if (currentDay > 0) {
                        ((completedThisMonth.toFloat() / currentDay.toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    progressBar.progress = progress
                    progressPercentage.text = "$progress%"

                    // Setup calendar with REAL data
                    setupCalendar(calendarGrid, habit, logs)
                }
            }
        }

        // Add animation when card appears
        view.alpha = 0f
        view.translationY = 100f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(index * 150L)
            .start()

        progressContainer.addView(view)
    }

    private fun setupCalendar(grid: GridLayout, habit: Habit, logs: List<com.example.selftracker.models.HabitLog>) {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Find earliest date ever logged for this habit to infer "StartDate"
        // If logs are empty, we assume no start date yet (so no red days).
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val earliestLogDate = logs.minByOrNull { it.date }?.let { 
             try { dateFormat.parse(it.date) } catch (e: Exception) { null }
        }

        // Map of "yyyy-MM-dd" to isCompleted
        val completionMap = logs.associate { it.date to it.isCompleted }

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

        grid.removeAllViews()
        grid.columnCount = 7

        // Add empty views for days before the 1st
        for (i in 0 until startDayOfWeek) {
            addCalendarDay(grid, "", false, false, false, false)
        }

        // Add days of the month
        for (day in 1..daysInMonth) {
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = dateFormat.format(calendar.time)
            
            val isCompleted = completionMap[dateStr] == true
            val isToday = day == currentDay
            
            // Logic: Is Missed?
            // 1. Day must be in the past (day < currentDay)
            // 2. Day must be NOT completed
            // 3. Day must be AFTER or ON the earliest log date (User: "starting dates will not show any color")
            //    If logs are empty, earliestLogDate is null, so isMissed is false (safe).
            //    If earliestLogDate exists, we check if calendar.time >= earliestLogDate.
            
            val isAfterStart = earliestLogDate != null && !calendar.time.before(earliestLogDate)
            val isMissed = (day < currentDay || (day == currentDay && !isCompleted)) && !isCompleted && isAfterStart
            
            // Note: User said "if i forget on 9 dec... it should show 9th red". 
            // If today is 10th. So Day < Today rule applies.
            // But if today is 9th and I haven't done it yet? 
            // Usually current day shouldn't be red until the day is Over.
            // So strictly: day < currentDay. 
            // User example: "today is 7th... added... 8th... forget 9th... while 10th starts". 
            // This implies 9th becomes red ONLY when 10th starts.
            
            val isMissedStrict = day < currentDay && !isCompleted && isAfterStart

            addCalendarDay(grid, day.toString(), true, isCompleted, isToday, isMissedStrict)
        }
    }

    private fun addCalendarDay(grid: GridLayout, day: String, isActive: Boolean, isCompleted: Boolean, isToday: Boolean, isMissed: Boolean) {
        val dayView = TextView(requireContext()).apply {
            text = day
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 48
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }

            if (isActive) {
                when {
                    isCompleted -> {
                        // Green for completed
                        setBackgroundColor(ContextCompat.getColor(context, R.color.success))
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                    }
                    isMissed -> {
                        // Light Red for missed/past dates
                        setBackgroundColor(ContextCompat.getColor(context, R.color.error_light))
                        setTextColor(ContextCompat.getColor(context, R.color.error_dark))
                    }
                    isToday -> {
                        // Today indicator (Neutral if not completed yet)
                        setBackgroundColor(ContextCompat.getColor(context, R.color.primary_light))
                        setTextColor(ContextCompat.getColor(context, R.color.primary))
                        background = ContextCompat.getDrawable(context, R.drawable.calendar_day_today) 
                    }
                    else -> {
                        // Future or Pre-start days
                        setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray))
                        setTextColor(ContextCompat.getColor(context, R.color.gray_400))
                    }
                }
            } else {
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                setTextColor(ContextCompat.getColor(context, R.color.gray_400))
            }
        }
        grid.addView(dayView)
    }
}