package com.example.selftracker.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.selftracker.R
import com.example.selftracker.adapters.HabitAdapter
import com.example.selftracker.database.SelfTrackerDatabase
import com.example.selftracker.models.Habit
import com.example.selftracker.models.HabitLog
import com.example.selftracker.utils.DateUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.selftracker.utils.ReminderScheduler
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.withContext

class HabitsFragment : Fragment() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
             showSnackbar("Notifications won't be sent without permission")
        }
    }

    private lateinit var habitsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var database: SelfTrackerDatabase
    private lateinit var habitAdapter: HabitAdapter
    private var allHabits: List<Habit> = emptyList()
    private var currentSortMode = 0 // 0: Name, 1: Streak, 2: Recent

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_habits, container, false)

        // Initialize views using findViewById
        habitsRecyclerView = view.findViewById(R.id.habits_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        emptyStateText = view.findViewById(R.id.empty_state_text)
        toolbar = view.findViewById(R.id.toolbar)
        
        // Fix Toolbar color programmatically if XML isn't enough (or rely on XML)
        // toolbar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = SelfTrackerDatabase.getDatabase(requireContext())
        setupRecyclerView()
        setupToolbar()
        loadHabits()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var searchQuery: String = ""

    private fun setupToolbar() {
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.habits_menu)

        val searchItem = toolbar.menu.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        searchView?.apply {
            queryHint = "Search habits..."
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchQuery = query ?: ""
                    updateDisplayedHabits()
                    clearFocus() // Hide keyboard
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    updateDisplayedHabits()
                    return true
                }
            })
        }

        // Menu is inflated via XML (app:menu), so we just set the listener
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (handleSelectionMenuItemClick(menuItem)) {
                return@setOnMenuItemClickListener true
            }
            
            when (menuItem.itemId) {
                R.id.menu_sort -> {
                    showSortDialog()
                    true
                }
                R.id.menu_other -> {
                    showSnackbar("Other options coming soon!")
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeleteSelectedConfirmation() {
        val count = habitAdapter.selectedHabits.size
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_SelfTracker_AlertDialog)
            .setTitle("Delete $count Habits?")
            .setMessage("Are you sure you want to delete these $count habits? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val selectedIds = habitAdapter.deleteSelectedItems()
                deleteHabits(selectedIds)
                habitAdapter.clearSelection()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteHabits(habitIds: List<Int>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            habitIds.forEach { id ->
                database.habitLogDao().deleteLogsByHabit(id)
                val habit = database.habitDao().getHabitByIdSync(id) // Ideally batch delete
                if (habit != null) {
                    database.habitDao().deleteHabit(habit)
                }
            }
            withContext(Dispatchers.Main) {
                showSnackbar("$habitIds.size habits deleted")
            }
        }
    }

    private fun setupRecyclerView() {
        habitAdapter = HabitAdapter(
            onCompleteHabit = { habit -> markHabitComplete(habit) },
            onEditHabit = { habit -> showEditHabitDialog(habit) },
            onDeleteHabit = { habit -> showDeleteConfirmation(habit) },
            onShowDetails = { habit -> showHabitDetails(habit) },
            onSelectionChanged = { isSelectionMode, count -> 
                updateToolbarForSelection(isSelectionMode, count)
            }
        )
        
        habitsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = habitAdapter
            setHasFixedSize(true)
        }
    }

    private var selectionMenuItem: android.view.MenuItem? = null

    private fun updateToolbarForSelection(isSelectionMode: Boolean, count: Int) {
        if (isSelectionMode) {
            toolbar.title = "$count Selected"
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.menu_selection) // Assuming a generic selection menu or we create it dynamically
            
            // If menu_selection doesn't exist yet, we can add delete item dynamically
            if (toolbar.menu.findItem(R.id.action_delete_selected) == null) {
                 toolbar.menu.add(0, R.id.action_delete_selected, 0, "Delete").setIcon(R.drawable.ic_delete).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            
            // Tint Delete Icon White
            val deleteItem = toolbar.menu.findItem(R.id.action_delete_selected)
            deleteItem?.icon?.setTint(android.graphics.Color.WHITE)
            
            toolbar.setNavigationIcon(R.drawable.ic_close)
            toolbar.setNavigationOnClickListener {
                habitAdapter.clearSelection()
            }
        } else {
            toolbar.title = "Habits" // Or original title
            setupToolbar() // Restore original menu
            toolbar.navigationIcon = null
        }
    }
    
    // Add inside setupToolbar listener or a new listener in updateToolbarForSelection
    private fun handleSelectionMenuItemClick(item: android.view.MenuItem): Boolean {
         return when (item.itemId) {
             R.id.action_delete_selected -> {
                 showDeleteSelectedConfirmation()
                 true
             }
             else -> false
         }
    }

    private fun loadHabits() {
        viewLifecycleOwner.lifecycleScope.launch {
            database.habitDao().getAllHabits().collect { habits ->
                allHabits = habits
                updateDisplayedHabits()
            }
        }
    }
    
    private fun updateDisplayedHabits() {
        // Filter by user query first
        val filteredList = if (searchQuery.isBlank()) {
            allHabits
        } else {
            allHabits.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        val sortedList = when (currentSortMode) {
            0 -> filteredList.sortedBy { it.name.lowercase() }
            1 -> filteredList.sortedByDescending { it.currentStreak }
            2 -> filteredList.sortedByDescending { it.lastCompletedDate ?: "" }
            else -> filteredList
        }

        if (sortedList.isEmpty()) {
            if (searchQuery.isNotEmpty()) {
                // If filtering caused empty state
                habitsRecyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                emptyStateText.text = "No habits found for \"$searchQuery\""
            } else {
                showEmptyState()
            }
        } else {
            hideEmptyState()
            habitAdapter.submitList(sortedList)
        }
    }

    private fun showEmptyState() {
        habitsRecyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyStateText.text = "No habits yet. Tap the + button to create one!"
    }

    private fun hideEmptyState() {
        habitsRecyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun markHabitComplete(habit: Habit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val today = DateUtils.getCurrentDate()
            val existingLog = database.habitLogDao().getLogByHabitAndDate(habit.habitId, today)

            if (existingLog == null) {
                val log = HabitLog(
                    habitId = habit.habitId,
                    date = today,
                    isCompleted = true,
                    actualValue = habit.targetValue
                )
                database.habitLogDao().insertHabitLog(log)

                // Update streak information
                val currentHabit = database.habitDao().getHabitByIdSync(habit.habitId)
                currentHabit?.let {
                    val newStreak = calculateNewStreak(it, today)
                    database.habitDao().updateCurrentStreak(it.habitId, newStreak)

                    if (newStreak > it.bestStreak) {
                        database.habitDao().updateBestStreak(it.habitId, newStreak)
                    }
                    database.habitDao().updateLastCompletedDate(it.habitId, today)
                }

                withContext(Dispatchers.Main) {
                    showSnackbar("${habit.name} completed! 🔥", "Undo") {
                        undoHabitCompletion(habit, today)
                    }
                    // Refresh list to update UI (sorting/filtering might change)
                    loadHabits()
                }
            } else {
                withContext(Dispatchers.Main) {
                    showSnackbar("${habit.name} already completed today! ✅")
                }
            }
        }
    }

    private fun calculateNewStreak(habit: Habit, today: String): Int {
        val yesterday = DateUtils.getPreviousDate(today)
        return if (habit.lastCompletedDate == yesterday) {
            habit.currentStreak + 1
        } else {
            1
        }
    }

    private fun undoHabitCompletion(habit: Habit, date: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.habitLogDao().deleteLogByHabitAndDate(habit.habitId, date)

            val currentHabit = database.habitDao().getHabitByIdSync(habit.habitId)
            currentHabit?.let {
                val newStreak = maxOf(0, it.currentStreak - 1)
                database.habitDao().updateCurrentStreak(it.habitId, newStreak)
            }
            withContext(Dispatchers.Main) {
                 loadHabits()
            }
        }
    }

    private fun showHabitDetails(habit: Habit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_habit_details, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .show()

        // Bind data
        dialogView.findViewById<TextView>(R.id.dialog_habit_name).text = habit.name
        dialogView.findViewById<TextView>(R.id.dialog_habit_schedule).text = "Schedule: ${habit.scheduleType}"
        dialogView.findViewById<TextView>(R.id.dialog_current_streak).text = habit.currentStreak.toString()
        dialogView.findViewById<TextView>(R.id.dialog_best_streak).text = habit.bestStreak.toString()
        dialogView.findViewById<TextView>(R.id.dialog_target).text = habit.targetValue.toString()
        dialogView.findViewById<TextView>(R.id.dialog_unit).text = habit.unit
        
        // Dynamic icon for time-based habits
        val targetIconInfo = if (habit.unit.lowercase() in listOf("min", "mins", "minute", "minutes", "hour", "hours", "hr", "hrs", "time")) {
             Pair(R.drawable.ic_habit_time, R.color.accent)
        } else {
             Pair(R.drawable.ic_habit_count, R.color.primary)
        }
        val targetIconView = dialogView.findViewById<ImageView>(R.id.dialog_target_icon)
        targetIconView.setImageResource(targetIconInfo.first)
        // targetIconView.setColorFilter(ContextCompat.getColor(requireContext(), targetIconInfo.second)) // already tinted in XML or use this if tint varies
        
        val lastCompletedText = habit.lastCompletedDate?.let { 
            DateUtils.formatDateForDisplay(it) 
        } ?: "Not yet"
        dialogView.findViewById<TextView>(R.id.dialog_last_completed).text = lastCompletedText

        // Actions
        dialogView.findViewById<View>(R.id.btn_close_header).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btn_edit).setOnClickListener {
            dialog.dismiss()
            showEditHabitDialog(habit)
        }

        dialogView.findViewById<Button>(R.id.btn_stats).setOnClickListener {
            dialog.dismiss()
            val bundle = Bundle().apply {
                putInt("HABIT_ID", habit.habitId)
            }
            val progressFragment = ProgressFragment().apply {
                arguments = bundle
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, progressFragment)
                .addToBackStack(null)
                .commit()
            
            // Note: Currently assumed MainActivity manages nav. If purely using replace, this works.
            // Ideally should update BottomNav selection if present, but this is a deep link.
        }

        dialogView.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(habit)
        }
    }



    private fun showSortDialog() {
        val sortOptions = arrayOf("By Name (A-Z)", "By Streak (High to Low)", "By Recent")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Habits")
            .setSingleChoiceItems(sortOptions, currentSortMode) { dialog, which ->
                currentSortMode = which
                updateDisplayedHabits()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Removed individual sort methods as they are now unified
    private fun sortHabitsByCompletion() {
        // Placeholder or integrated above
         showSnackbar("Use Filter to see completed habits")
    }

    private fun showDeleteConfirmation(habit: Habit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Habit")
            .setMessage("Are you sure you want to delete \"${habit.name}\"? This will also delete all associated logs.")
            .setPositiveButton("Delete") { dialog, which ->
                deleteHabit(habit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHabit(habit: Habit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Delete associated logs first (due to foreign key constraint)
            database.habitLogDao().deleteLogsByHabit(habit.habitId)
            database.habitDao().deleteHabit(habit)
            withContext(Dispatchers.Main) {
                showSnackbar("Habit deleted", "Undo") {
                    restoreHabit(habit)
                }
            }
        }
    }

    private fun restoreHabit(habit: Habit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            database.habitDao().insertHabit(habit)
        }
    }

    fun showAddHabitDialog() {
        showHabitDialog(null)
    }

    private fun showEditHabitDialog(habit: Habit) {
        showHabitDialog(habit)
    }

    private fun showHabitDialog(existingHabit: Habit?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_habit, null)
        val isEditMode = existingHabit != null

        val editHabitName = dialogView.findViewById<EditText>(R.id.edit_habit_name)
        val editHabitDescription = dialogView.findViewById<EditText>(R.id.edit_habit_description)
        val editTargetValue = dialogView.findViewById<EditText>(R.id.edit_target_value)
        val editUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_habit)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker)
        
        // Setup Unit Dropdown (with common units but allowing custom input)
        val units = arrayOf("minutes", "times", "hours", "glasses", "pages", "km", "miles", "cal")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_dark, units)
        editUnit.setAdapter(adapter)
        editUnit.threshold = 1
        editUnit.setOnClickListener { editUnit.showDropDown() } // Show on click

        // Use 24h view or user pref? Defaulting to 12h as requested "H:mm a" previously, but spinner is usually 24h or system default.
        timePicker.setIs24HourView(false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        dialog.setCanceledOnTouchOutside(true)

        // Pre-fill for edit mode
        existingHabit?.let { habit ->
            editHabitName.setText(habit.name)
            editHabitDescription.setText(habit.description)
            editTargetValue.setText(habit.targetValue.toString())
            editUnit.setText(habit.unit) // AutoCompleteTextView handles setText fine
            
            habit.reminderTime?.let {
                 val cal = Calendar.getInstance().apply { timeInMillis = it }
                 timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
                 timePicker.minute = cal.get(Calendar.MINUTE)
            }
        }

        // Set focus on name field for new habits
        if (!isEditMode) {
            editHabitName.requestFocus()
        }
        
        // Helper to get time from picker
        fun getReminderTimeFromPicker(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            cal.set(Calendar.MINUTE, timePicker.minute)
            cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }

        btnSave.setOnClickListener {
            val name = editHabitName.text.toString().trim()
            val description = editHabitDescription.text.toString().trim()
            val targetStr = editTargetValue.text.toString().trim()
            val unit = editUnit.text.toString().trim()
            
            // Assume user wants reminder if they touch it? Or always save it? 
            // Since it's marked "Optional", we could add a CheckBox or just assume if they modify it. 
            // For now, let's ALWAYS save it since the clock is visible. Or maybe default to null if it matches current time? 
            // User requested "Optional reminder", but providing a permanent clock implies it's always set unless disabled.
            // Let's save it.
            val reminderTime = getReminderTimeFromPicker()

            if (validateInput(name, targetStr, editHabitName, editTargetValue)) {
                val habit = if (isEditMode) {
                    existingHabit!!.copy(
                        name = name,
                        description = description,
                        targetValue = targetStr.toInt(),
                        unit = if (unit.isEmpty()) "times" else unit,
                        reminderTime = reminderTime
                    )
                } else {
                    Habit(
                        name = name,
                        description = description,
                        targetValue = targetStr.toInt(),
                        unit = if (unit.isEmpty()) "times" else unit,
                        scheduleType = "DAILY",
                        reminderTime = reminderTime
                    )
                }

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (isEditMode) {
                        database.habitDao().updateHabit(habit)
                    } else {
                        val newId = database.habitDao().insertHabit(habit)
                         val savedHabit = habit.copy(habitId = newId.toInt())
                         // Always schedule since time is picked
                         ReminderScheduler.scheduleReminder(requireContext(), savedHabit)
                    }
                    
                    if (isEditMode) {
                        ReminderScheduler.scheduleReminder(requireContext(), habit)
                    }
                    
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        showSnackbar(
                            if (isEditMode) "Habit updated successfully!" else "Habit created successfully! 🎉"
                        )
                    }
                }
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Add keyboard enter key support
        editHabitName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                editTargetValue.requestFocus()
                true
            } else {
                false
            }
        }

        editTargetValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                editUnit.requestFocus()
                true
            } else {
                false
            }
        }

        // Show dropdown on click - Removed as it is now EditText
        // editUnit.setOnClickListener ...

        dialog.show()
    }

    private fun validateInput(
        name: String,
        targetStr: String,
        nameEditText: EditText,
        targetEditText: EditText
    ): Boolean {
        var isValid = true

        if (name.isEmpty()) {
            nameEditText.error = "Habit name is required"
            isValid = false
        } else if (name.length < 2) {
            nameEditText.error = "Habit name must be at least 2 characters"
            isValid = false
        } else {
            nameEditText.error = null
        }

        if (targetStr.isEmpty()) {
            targetEditText.error = "Target value is required"
            isValid = false
        } else {
            val target = targetStr.toIntOrNull()
            if (target == null || target <= 0) {
                targetEditText.error = "Please enter a valid positive number"
                isValid = false
            } else if (target > 1000) {
                targetEditText.error = "Target value seems too high"
                isValid = false
            } else {
                targetEditText.error = null
            }
        }

        return isValid
    }

    private fun showSnackbar(message: String, action: String? = null, actionListener: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        action?.let {
            snackbar.setAction(it) { actionListener?.invoke() }
            snackbar.setActionTextColor(resources.getColor(R.color.primary, null))
        }
        snackbar.setBackgroundTint(resources.getColor(R.color.surface_variant, null))
        snackbar.setTextColor(resources.getColor(R.color.primary_text, null))
        snackbar.show()
    }
}