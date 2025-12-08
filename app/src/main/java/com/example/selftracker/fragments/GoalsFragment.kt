package com.example.selftracker.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.selftracker.R
import com.example.selftracker.database.SelfTrackerDatabase
import com.example.selftracker.models.Goal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoalsFragment : Fragment() {

    private lateinit var goalsContainer: LinearLayout
    private lateinit var database: SelfTrackerDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_goals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        goalsContainer = view.findViewById(R.id.goals_container)
        database = SelfTrackerDatabase.getDatabase(requireContext())
        setupToolbar(view)
        loadGoals()
    }

    private fun setupToolbar(view: View) {
        val btnNotifications = view.findViewById<ImageButton>(R.id.btn_notifications)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        btnNotifications.setOnClickListener {
            Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show()
        }
        
        // Handle generic menu items (Settings)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadGoals() {
        database.goalDao().getAllGoalsWithProgress().observe(viewLifecycleOwner) { goalsWithProgress ->
            goalsContainer.removeAllViews()
            if (goalsWithProgress.isEmpty()) {
                showEmptyState()
            } else {
                goalsWithProgress.forEach { goalWithProgress ->
                    addGoalCard(goalWithProgress.goal, goalWithProgress.totalSteps, goalWithProgress.completedSteps)
                }
            }
        }
    }

    private fun showEmptyState() {
        val emptyText = TextView(requireContext()).apply {
            text = "No goals yet. Tap + to create one!"
            textSize = 16f
            setPadding(32, 32, 32, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
        }
        goalsContainer.addView(emptyText)
    }

    private fun addGoalCard(goal: Goal, totalSteps: Int, completedSteps: Int) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.item_goal_card, goalsContainer, false)

        val nameText = view.findViewById<TextView>(R.id.goal_name)
        val statusText = view.findViewById<TextView>(R.id.goal_status_text)
        val progressBar = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.goal_progress_bar)
        val stepsContainer = view.findViewById<LinearLayout>(R.id.steps_container)
        
        nameText.text = goal.name

        val progress = if (totalSteps > 0) (completedSteps.toFloat() / totalSteps.toFloat() * 100).toInt() else 0
        progressBar.progress = progress
        
        statusText.text = if (totalSteps == 0) "No steps added" else "$completedSteps/$totalSteps Steps Completed"

        // Load steps and render as dots
        database.goalStepDao().getStepsByGoal(goal.goalId).observe(viewLifecycleOwner) { steps ->
            stepsContainer.removeAllViews()
            steps.sortedBy { it.orderIndex }.forEachIndexed { index, step ->
                addStepDot(stepsContainer, step, index + 1)
            }
        }


        if (completedSteps >= totalSteps && totalSteps > 0) {
            statusText.text = "Goal Completed! 🎉"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        }

        // Click on entire card to view details
        view.setOnClickListener {
            val goalDetailFragment = GoalDetailFragment.newInstance(goal.goalId)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, goalDetailFragment)
                .addToBackStack("goal_detail")
                .commit()
        }

        goalsContainer.addView(view)
    }

    private fun addStepDot(container: LinearLayout, step: com.example.selftracker.models.GoalStep, stepNumber: Int) {
        val dotView = LayoutInflater.from(requireContext()).inflate(R.layout.item_step_dot, container, false)

        val dot = dotView.findViewById<View>(R.id.step_dot)
        val stepNumberText = dotView.findViewById<TextView>(R.id.step_number)

        if (step.isCompleted) {
            // Completed: Dark green node with checkmark, NO text inside (text below acts as label if needed, but usually hidden or generic)
            // User request: "tick inside the circle... darker green tick circle"
            // And presumably hide the number from the view below it if it's redundant / or keep it?
            // "lighter green for incomplete steps"
            
            dot.setBackgroundResource(R.drawable.ic_step_completed)
            stepNumberText.visibility = View.GONE // Hide the number "1", "2" below the dot as the dot itself is the main indicator? 
            // Wait, the xml has `step_number` BELOW the dot. Usually steps are numbered. 
            // If I look at the XML, `step_number` is below. 
            // User didn't explicitly say "hide the number". 
            // "instead of the simple circle i want you to use a tick insdie the circle" implies the CONTENT of the circle changes.
            // The current `item_step_dot` has a View (circle) and a TextView (number) BELOW it. 
            // Wait, looking at current XML:
            /*
            <View android:id="@+id/step_dot" ... />
            <TextView android:id="@+id/step_number" ... below ... />
            */
            // Ideally, the number should be INSIDE the circle for pending, and TICK inside for completed?
            // User: "lighter green for incomplete steps (and darker green tick circle for the completed tasks)"
            // Existing XML has number BELOW. 
            // If I want number INSIDE for incomplete, I need to use a TextView for the dot or a FrameLayout.
            // Let's look at `item_step_dot.xml` again.
            // It has `orientation="vertical"`. So number IS below.
            // If user wants to "enhance the goal card", maybe the number logic is weird.
            // Most step trackers have Number INSIDE the circle.
            // I will Assume: Keep number below for now to minimize risk, unless I change XML to FrameLayout.
            // BUT, if I just change the background, the number below is fine.
            // Wait, for "tick inside the circle", `ic_step_completed` has a tick.
            // For incomplete, it's just a light green circle.
            // Step Number is separate.
            // I'll keep the step number visible below for context, or hide it if it looks cluttered. 
            // User request: "tick inside the circle".
            // I'll stick to: Completed -> Tick icon. Pending -> Light circle.
            // I will keep the number text below visible as it helps identify step order.
            
            stepNumberText.visibility = View.VISIBLE
            stepNumberText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary)) 
        } else {
            // Incomplete: Light green circle
            dot.setBackgroundResource(R.drawable.ic_step_pending)
            stepNumberText.visibility = View.VISIBLE
            stepNumberText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_600))
        }

        stepNumberText.text = stepNumber.toString()

        container.addView(dotView)
    }

    fun showAddGoalDialog() {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_goal, null)

        val editName = view.findViewById<EditText>(R.id.edit_goal_name)
        val editDescription = view.findViewById<EditText>(R.id.edit_goal_description)
        val inputDescription = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_layout_description)

        val btnSave = view.findViewById<Button>(R.id.btn_save_goal)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_goal)
        
        // AI UI Elements
        val btnGenerate = view.findViewById<Button>(R.id.btn_generate_ai_steps)
        val progressGen = view.findViewById<View>(R.id.progress_ai_generation)
        val textStatus = view.findViewById<TextView>(R.id.text_ai_status)
        val recyclerOptions = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_plan_options)
        val recyclerSteps = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_generated_steps)
        
        recyclerSteps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerOptions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        
        var generatedSteps: List<com.example.selftracker.models.GeneratedStep> = emptyList()

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Handle Magic Wand Click (Enhance Description)
        inputDescription.setEndIconOnClickListener {
            val originalText = editDescription.text.toString().trim()
            if (originalText.isNotEmpty()) {
                textStatus.text = "Enhancing description..."
                textStatus.visibility = View.VISIBLE
                inputDescription.isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val repository = com.example.selftracker.repository.GoalGeneratorRepository()
                        val enhanced = repository.enhanceGoalDescription(originalText)
                        
                        withContext(Dispatchers.Main) {
                            inputDescription.isEnabled = true
                            textStatus.visibility = View.GONE
                            editDescription.setText(enhanced)
                            Toast.makeText(context, "Description enhanced! ✨", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            inputDescription.isEnabled = true
                            textStatus.visibility = View.GONE
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                 Toast.makeText(context, "Type a description first to enhance it", Toast.LENGTH_SHORT).show()
            }
        }

        btnGenerate.setOnClickListener {
            val name = editName.text.toString().trim()
            val description = editDescription.text.toString().trim()
            
            if (name.isNotEmpty()) {
                btnGenerate.isEnabled = false
                progressGen.visibility = View.VISIBLE
                textStatus.text = "Drafting strategies..."
                textStatus.visibility = View.VISIBLE
                recyclerSteps.visibility = View.GONE
                recyclerOptions.visibility = View.GONE
                
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val prompt = if (description.isNotEmpty()) "$name. Context: $description" else name
                        val repository = com.example.selftracker.repository.GoalGeneratorRepository()
                        
                        // Phase 1: Generate Options
                        val options = repository.generatePlanOptions(prompt)
                        
                        withContext(Dispatchers.Main) {
                            progressGen.visibility = View.GONE
                            textStatus.text = "Select a strategy:"
                            
                            recyclerOptions.visibility = View.VISIBLE
                            recyclerOptions.adapter = AiPlanOptionsAdapter(options) { selectedOption ->
                                // Phase 2: Generate Steps based on Selection
                                viewLifecycleOwner.lifecycleScope.launch {
                                    recyclerOptions.visibility = View.GONE
                                    progressGen.visibility = View.VISIBLE
                                    textStatus.text = "Building '${selectedOption.title}' plan..."
                                    textStatus.visibility = View.VISIBLE
                                    
                                    try {
                                        val generatedGoal = repository.generateGoal(prompt, selectedOption.strategy)
                                        withContext(Dispatchers.Main) {
                                            btnGenerate.isEnabled = true
                                            progressGen.visibility = View.GONE
                                            textStatus.visibility = View.GONE
                                            
                                            generatedSteps = generatedGoal.steps
                                            recyclerSteps.visibility = View.VISIBLE
                                            recyclerSteps.adapter = AiStepsAdapter(generatedSteps)
                                            Toast.makeText(context, "Plan ready! Review steps below.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                         withContext(Dispatchers.Main) {
                                            btnGenerate.isEnabled = true
                                            progressGen.visibility = View.GONE
                                            textStatus.text = "Failed to generate steps."
                                            recyclerOptions.visibility = View.VISIBLE // Show options again
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            btnGenerate.isEnabled = true
                            progressGen.visibility = View.GONE
                            textStatus.visibility = View.GONE
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a goal name first", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isNotEmpty()) {
                val goal = Goal(
                    name = name,
                    description = editDescription.text.toString().trim().takeIf { it.isNotEmpty() }
                )
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // 1. Save Goal
                    val goalId = database.goalDao().insertGoal(goal)
                    
                    // 2. Save Generated Steps (if any)
                    if (generatedSteps.isNotEmpty()) {
                        generatedSteps.forEachIndexed { index, genStep ->
                            val step = com.example.selftracker.models.GoalStep(
                                goalId = goalId,
                                name = genStep.stepName,
                                orderIndex = index,
                                duration = genStep.durationValue,
                                durationUnit = genStep.durationUnit
                            )
                            val stepId = database.goalStepDao().insertGoalStep(step)
                            
                            // 3. Save Substeps
                            genStep.substeps.forEachIndexed { subIndex, subName ->
                                 val subStep = com.example.selftracker.models.GoalSubStep(
                                     stepId = stepId,
                                     name = subName,
                                     orderIndex = subIndex
                                 )
                                 database.goalSubStepDao().insertGoalSubStep(subStep)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        val message = if (generatedSteps.isNotEmpty()) "Goal & Roadmap Created!" else "Goal Added!"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a goal name", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    inner class AiPlanOptionsAdapter(
        private val options: List<com.example.selftracker.models.PlanOption>,
        private val onOptionClick: (com.example.selftracker.models.PlanOption) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<AiPlanOptionsAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.text_option_title)
            val description: TextView = itemView.findViewById(R.id.text_option_description)
            val card: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_plan_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = options[position]
            holder.title.text = option.title
            holder.description.text = option.description
            holder.itemView.setOnClickListener { onOptionClick(option) }
        }

        override fun getItemCount() = options.size
    }

    inner class AiStepsAdapter(private val steps: List<com.example.selftracker.models.GeneratedStep>) : 
        androidx.recyclerview.widget.RecyclerView.Adapter<AiStepsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val stepNumber: TextView = view.findViewById(R.id.text_step_number)
            val stepName: TextView = view.findViewById(R.id.text_step_name)
            val duration: TextView = view.findViewById(R.id.text_step_duration)
            val substeps: TextView = view.findViewById(R.id.text_substeps)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_step_preview, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val step = steps[position]
            holder.stepNumber.text = (position + 1).toString()
            holder.stepName.text = step.stepName
            holder.duration.text = "${step.durationValue} ${step.durationUnit}"
            
            if (step.substeps.isNotEmpty()) {
                holder.substeps.visibility = View.VISIBLE
                holder.substeps.text = step.substeps.joinToString("\n• ", prefix = "• ")
            } else {
                holder.substeps.visibility = View.GONE
            }
        }

        override fun getItemCount() = steps.size
    }

}