package com.example.selftracker.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.selftracker.R
import com.example.selftracker.database.SelfTrackerDatabase
import com.example.selftracker.models.Goal
import com.example.selftracker.models.GoalStep
import com.example.selftracker.models.GoalSubStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoalDetailFragment : Fragment() {

    private lateinit var database: SelfTrackerDatabase
    private var goalId: Long = 0
    private lateinit var goal: Goal
    private lateinit var stepsTreeContainer: LinearLayout
    private lateinit var goalTitle: TextView
    private lateinit var goalDescription: TextView
    private lateinit var progressBar: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var completedCount: TextView
    private lateinit var totalCount: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_goal_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = SelfTrackerDatabase.getDatabase(requireContext())
        goalId = arguments?.getLong("goalId") ?: 0

        initViews(view)
        loadGoalDetails()
        setupToolbarMenu()
    }

    private fun initViews(view: View) {
        stepsTreeContainer = view.findViewById(R.id.steps_tree_container)
        goalTitle = view.findViewById(R.id.goal_title)
        goalDescription = view.findViewById(R.id.goal_description)
        progressBar = view.findViewById(R.id.progress_bar)
        progressText = view.findViewById(R.id.progress_text)
        completedCount = view.findViewById(R.id.completed_count)
        totalCount = view.findViewById(R.id.total_count)

        // Setup toolbar
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Setup Add Step Button (in card)
        view.findViewById<ImageButton>(R.id.btn_add_step_detail).setOnClickListener {
            showAddStepDialog()
        }
    }

    private fun setupToolbarMenu() {
        val toolbar = requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_edit_goal -> {
                    showEditGoalDialog()
                    true
                }
                R.id.menu_delete_goal -> {
                    showDeleteGoalDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadGoalDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Load goal on background thread
            val goalResult = withContext(Dispatchers.IO) {
                database.goalDao().getGoalById(goalId)
            }

            goal = goalResult ?: return@launch

            // Update UI on main thread
            goalTitle.text = goal.name
            goalDescription.text = goal.description ?: "No description"

            // Observe steps on main thread
            database.goalStepDao().getStepsByGoal(goalId).observe(viewLifecycleOwner) { steps ->
                updateStepsUI(steps)
            }
        }
    }

    private fun updateStepsUI(steps: List<GoalStep>) {
        stepsTreeContainer.removeAllViews()

        val totalSteps = steps.size
        val completedSteps = steps.count { it.isCompleted }

        // Calculate progress
        val progress = if (totalSteps > 0) (completedSteps.toFloat() / totalSteps.toFloat() * 100).toInt() else 0

        progressBar.progress = progress
        progressText.text = "$progress%"
        completedCount.text = "$completedSteps completed"
        totalCount.text = "$totalSteps total"

        steps.sortedBy { it.orderIndex }.forEachIndexed { index, step ->
            addStepToTree(step, index)
        }

        if (steps.isEmpty()) {
            showEmptyStepsState()
        }
    }

    private fun addStepToTree(step: GoalStep, stepIndex: Int) {
        val stepView = LayoutInflater.from(requireContext()).inflate(R.layout.item_step_tree, stepsTreeContainer, false)

        val stepName = stepView.findViewById<TextView>(R.id.step_name)
        val stepDuration = stepView.findViewById<TextView>(R.id.step_duration)
        val stepProgress = stepView.findViewById<TextView>(R.id.step_progress)
        val stepProgressBar = stepView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.step_progress_bar)
        val stepCheckbox = stepView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.step_checkbox)
        val substepsContainer = stepView.findViewById<LinearLayout>(R.id.substeps_container)
        val btnAddSubstep = stepView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_substep)
        val btnExpand = stepView.findViewById<ImageButton>(R.id.btn_expand)

        stepName.text = step.name
        stepDuration.text = "${step.duration} ${step.durationUnit}"
        stepCheckbox.isChecked = step.isCompleted

        // Step completion checkbox listener
        stepCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (isChecked) {
                    // Mark step as completed and all its substeps as completed
                    database.goalStepDao().updateGoalStep(step.copy(isCompleted = true))

                    // Mark all substeps as completed
                    val substeps = database.goalSubStepDao().getSubStepsByStep(step.stepId).value ?: emptyList()
                    substeps.forEach { substep ->
                        database.goalSubStepDao().updateGoalSubStep(substep.copy(isCompleted = true))
                    }
                } else {
                    // Mark step as incomplete (but don't change substeps)
                    database.goalStepDao().updateGoalStep(step.copy(isCompleted = false))
                }
            }
        }

        // Load and display substeps
        database.goalSubStepDao().getSubStepsByStep(step.stepId).observe(viewLifecycleOwner) { substeps ->
            updateSubstepsUI(substepsContainer, substeps, stepProgress, stepProgressBar, step, stepCheckbox)
        }

        // Expand/collapse functionality
        var isExpanded = false
        // Expand/collapse logic
        val toggleExpansion = {
            isExpanded = !isExpanded
            substepsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            btnExpand.setImageResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        btnExpand.setOnClickListener { toggleExpansion() }
        
        // Step click listener - Now toggles expansion
        stepView.setOnClickListener { toggleExpansion() }

        // Step long click listener - Edit step details
        stepView.setOnLongClickListener {
            showStepDetailsDialog(step)
            true
        }

        // Add substep button
        btnAddSubstep.setOnClickListener {
            showAddSubstepDialog(step.stepId)
        }

        stepsTreeContainer.addView(stepView)
    }

    private fun updateSubstepsUI(
        container: LinearLayout,
        substeps: List<GoalSubStep>,
        stepProgress: TextView,
        stepProgressBar: com.google.android.material.progressindicator.LinearProgressIndicator,
        step: GoalStep,
        stepCheckbox: com.google.android.material.checkbox.MaterialCheckBox
    ) {
        container.removeAllViews()

        val totalSubsteps = substeps.size
        val completedSubsteps = substeps.count { it.isCompleted }

        stepProgress.text = "$completedSubsteps/$totalSubsteps done"

        // Update step progress bar
        val substepProgress = if (totalSubsteps > 0) (completedSubsteps.toFloat() / totalSubsteps.toFloat() * 100).toInt() else 0
        stepProgressBar.progress = substepProgress

        // Auto-complete step if all substeps are completed
        if (totalSubsteps > 0 && completedSubsteps == totalSubsteps && !step.isCompleted) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                database.goalStepDao().updateGoalStep(step.copy(isCompleted = true))
            }
        }

        substeps.sortedBy { it.orderIndex }.forEach { substep ->
            addSubstepToTree(container, substep, step)
        }
    }

    private fun addSubstepToTree(container: LinearLayout, substep: GoalSubStep, parentStep: GoalStep) {
        val substepView = LayoutInflater.from(requireContext()).inflate(R.layout.item_substep_tree, container, false)

        val substepName = substepView.findViewById<TextView>(R.id.substep_name)
        val substepDuration = substepView.findViewById<TextView>(R.id.substep_duration)
        val substepStatusText = substepView.findViewById<TextView>(R.id.substep_status_text)
        val substepStatus = substepView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.substep_status)
        val btnSubstepMenu = substepView.findViewById<ImageButton>(R.id.btn_substep_menu)

        substepName.text = substep.name
        substepDuration.text = "${substep.duration} ${substep.durationUnit}"

        // Update status text and color
        if (substep.isCompleted) {
            substepStatusText.text = "Completed"
            substepStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            substepStatusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_small, 0, 0, 0)
        } else {
            substepStatusText.text = "In Progress"
            substepStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            substepStatusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_clock_small, 0, 0, 0)
        }

        substepStatus.isChecked = substep.isCompleted

        // Substep status change listener
        substepStatus.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                database.goalSubStepDao().updateGoalSubStep(substep.copy(isCompleted = isChecked))
            }
        }

        // Substep menu button
        btnSubstepMenu.setOnClickListener { view ->
            showSubstepMenu(view, substep)
        }

        // Substep click listener
        substepView.setOnClickListener {
            showSubstepDetailsDialog(substep)
        }

        container.addView(substepView)
    }

    private fun showSubstepMenu(view: View, substep: GoalSubStep) {
        val popup = android.widget.PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.substep_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_substep -> {
                    showEditSubstepDialog(substep)
                    true
                }
                R.id.menu_delete_substep -> {
                    showDeleteSubstepDialog(substep)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // FIXED: Added missing dialog methods
    private fun showAddStepDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_step, null)
        val editStepName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_name)
        val editStepDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_description)
        val editStepDuration = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_step)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_step)

        // Setup duration unit dropdown
        val durationUnits = arrayOf("days", "weeks", "months")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durationUnits)
        editDurationUnit.setAdapter(adapter)
        editDurationUnit.setText(durationUnits[0], false) // Default to days

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Make background transparent to show custom rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val name = editStepName.text.toString().trim()
            val description = editStepDescription.text.toString().trim()
            val duration = editStepDuration.text.toString().toIntOrNull() ?: 0
            val durationUnit = editDurationUnit.text.toString()

            if (name.isNotEmpty() && duration > 0) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // Get the next order index
                    val steps = database.goalStepDao().getStepsByGoal(goalId).value ?: emptyList()
                    val nextOrderIndex = steps.size

                    val newStep = GoalStep(
                        stepId = 0,
                        goalId = goalId,
                        name = name,
                        description = if (description.isNotEmpty()) description else null,
                        duration = duration,
                        durationUnit = durationUnit,
                        isCompleted = false,
                        orderIndex = nextOrderIndex
                    )
                    database.goalStepDao().insertGoalStep(newStep)
                    
                    withContext(Dispatchers.Main) {
                       Toast.makeText(requireContext(), "Step added!", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showStepDetailsDialog(step: GoalStep) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_step, null)
        val editStepName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_name)
        val editStepDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_description)
        val editStepDuration = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_step_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_step)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_step)

        // Pre-fill existing data
        editStepName.setText(step.name)
        editStepDescription.setText(step.description ?: "")
        editStepDuration.setText(step.duration.toString())
        btnSave.text = "Update Step" // Update button text

        // Setup duration unit dropdown
        val durationUnits = arrayOf("days", "weeks", "months")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durationUnits)
        editDurationUnit.setAdapter(adapter)
        editDurationUnit.setText(step.durationUnit, false)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val name = editStepName.text.toString().trim()
            val description = editStepDescription.text.toString().trim()
            val duration = editStepDuration.text.toString().toIntOrNull() ?: 0
            val durationUnit = editDurationUnit.text.toString()

            if (name.isNotEmpty() && duration > 0) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updatedStep = step.copy(
                        name = name,
                        description = if (description.isNotEmpty()) description else null,
                        duration = duration,
                        durationUnit = durationUnit
                    )
                    database.goalStepDao().updateGoalStep(updatedStep)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Step updated", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddSubstepDialog(stepId: Long) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_substep, null)
        val editSubstepName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_name)
        val editSubstepDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_description)
        val editSubstepDuration = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_substep)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_substep)

        // Setup duration unit dropdown
        val durationUnits = arrayOf("days", "weeks", "months")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durationUnits)
        editDurationUnit.setAdapter(adapter)
        editDurationUnit.setText(durationUnits[0], false) // Default to days

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val name = editSubstepName.text.toString().trim()
            val description = editSubstepDescription.text.toString().trim()
            val duration = editSubstepDuration.text.toString().toIntOrNull() ?: 0
            val durationUnit = editDurationUnit.text.toString()

            if (name.isNotEmpty() && duration > 0) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // Get the next order index
                    val substeps = database.goalSubStepDao().getSubStepsByStep(stepId).value ?: emptyList()
                    val nextOrderIndex = substeps.size

                    val newSubstep = GoalSubStep(
                        subStepId = 0,
                        stepId = stepId,
                        name = name,
                        description = if (description.isNotEmpty()) description else null,
                        duration = duration,
                        durationUnit = durationUnit,
                        isCompleted = false,
                        orderIndex = nextOrderIndex
                    )
                    database.goalSubStepDao().insertGoalSubStep(newSubstep)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSubstepDetailsDialog(substep: GoalSubStep) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_substep, null)
        val editSubstepName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_name)
        val editSubstepDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_description)
        val editSubstepDuration = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_substep_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_substep)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_substep)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        
        // Update Title for Edit
        dialogTitle.text = "Edit Sub-step"
        btnSave.text = "Update"

        // Pre-fill existing data
        editSubstepName.setText(substep.name)
        editSubstepDescription.setText(substep.description ?: "")
        editSubstepDuration.setText(substep.duration.toString())

        // Setup duration unit dropdown
        val durationUnits = arrayOf("days", "weeks", "months")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, durationUnits)
        editDurationUnit.setAdapter(adapter)
        editDurationUnit.setText(substep.durationUnit, false)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val name = editSubstepName.text.toString().trim()
            val description = editSubstepDescription.text.toString().trim()
            val duration = editSubstepDuration.text.toString().toIntOrNull() ?: 0
            val durationUnit = editDurationUnit.text.toString()

            if (name.isNotEmpty() && duration > 0) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updatedSubstep = substep.copy(
                        name = name,
                        description = if (description.isNotEmpty()) description else null,
                        duration = duration,
                        durationUnit = durationUnit
                    )
                    database.goalSubStepDao().updateGoalSubStep(updatedSubstep)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditSubstepDialog(substep: GoalSubStep) {
        // Reuse the showSubstepDetailsDialog for editing
        showSubstepDetailsDialog(substep)
    }

    private fun showDeleteSubstepDialog(substep: GoalSubStep) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Sub-step")
            .setMessage("Are you sure you want to delete '${substep.name}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    database.goalSubStepDao().deleteGoalSubStep(substep)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showEditGoalDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_goal, null)
        val editGoalName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_goal_name)
        val editGoalDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_goal_description)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_goal)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_goal)

        // Pre-fill existing data
        editGoalName.setText(goal.name)
        editGoalDescription.setText(goal.description ?: "")
        
        btnSave.text = "Update Goal"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val name = editGoalName.text.toString().trim()
            val description = editGoalDescription.text.toString().trim()

            if (name.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updatedGoal = goal.copy(
                        name = name,
                        description = if (description.isNotEmpty()) description else null
                    )
                    database.goalDao().updateGoal(updatedGoal)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter a goal name", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteGoalDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Goal")
            .setMessage("Are you sure you want to delete '${goal.name}'? This will also delete all steps and substeps.")
            .setPositiveButton("Delete") { dialog, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    database.goalDao().deleteGoal(goal)
                    withContext(Dispatchers.Main) {
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmptyStepsState() {
        val emptyView = LayoutInflater.from(requireContext()).inflate(R.layout.empty_steps_state, stepsTreeContainer, false)
        stepsTreeContainer.addView(emptyView)
    }

    companion object {
        fun newInstance(goalId: Long): GoalDetailFragment {
            val fragment = GoalDetailFragment()
            val args = Bundle()
            args.putLong("goalId", goalId)
            fragment.arguments = args
            return fragment
        }
    }
}