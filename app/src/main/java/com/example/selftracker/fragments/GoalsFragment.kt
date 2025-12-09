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
        val stepsContainer = view.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.steps_container)
        val iconView = view.findViewById<ImageView>(R.id.icon_target)
        
        nameText.text = goal.name
        
        // Load AI Icon if exists
        android.util.Log.d("GoalsFragment", "Goal: ${goal.name}, IconPath: ${goal.localIconPath}")
        if (goal.localIconPath != null) {
            val drawable = loadIconFromFile(goal.localIconPath)
            if (drawable != null) {
                iconView.setImageDrawable(drawable)
                iconView.imageTintList = null // Remove tint to show colors
            }
        }

        val progress = if (totalSteps > 0) (completedSteps.toFloat() / totalSteps.toFloat() * 100).toInt() else 0
        progressBar.progress = progress
        
        statusText.text = if (totalSteps == 0) "No steps added" else "$completedSteps/$totalSteps Steps Completed"

        // Load steps and render as dots
        database.goalStepDao().getStepsByGoal(goal.goalId).observe(viewLifecycleOwner) { steps ->
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

    private fun addStepDot(container: com.google.android.flexbox.FlexboxLayout, step: com.example.selftracker.models.GoalStep, stepNumber: Int) {
        val dotView = LayoutInflater.from(requireContext()).inflate(R.layout.item_step_dot, container, false)

        val dot = dotView.findViewById<View>(R.id.step_dot)
        val stepNumberText = dotView.findViewById<TextView>(R.id.step_number)

        // Enhance Logic: Tick/Outline logic
        if (step.isCompleted) {
             dot.setBackgroundResource(R.drawable.ic_check_circle_filled) 
             dot.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.success))
             stepNumberText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
        } else {
             dot.setBackgroundResource(R.drawable.ic_circle_outline)
             dot.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray_400))
             stepNumberText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_600))
        }

        stepNumberText.text = stepNumber.toString()
        
        // Interactive Tooltip
        dotView.setOnClickListener {
            Toast.makeText(requireContext(), "${stepNumber}. ${step.name}", Toast.LENGTH_SHORT).show()
        }

        container.addView(dotView)
    }

    fun showAddGoalDialog() {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_goal, null)

        val editName = dialogView.findViewById<EditText>(R.id.edit_goal_name)
        val editDescription = dialogView.findViewById<EditText>(R.id.edit_goal_description)
        // No more TextInputLayout for description interactions
        val btnEnhance = dialogView.findViewById<ImageView>(R.id.btn_enhance_description)

        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_goal)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog) // Changed from btn_cancel_goal button
        
        // AI UI Elements
        val btnGenerate = dialogView.findViewById<Button>(R.id.btn_generate_ai_steps)
        val progressGen = dialogView.findViewById<View>(R.id.progress_ai_generation)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_ai_status)
        val recyclerOptions = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_plan_options)
        val recyclerSteps = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_generated_steps)
        
        recyclerSteps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerOptions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        
        var generatedSteps: List<com.example.selftracker.models.GeneratedStep> = emptyList()

        builder.setView(dialogView)
        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Handle Magic Wand Click (Enhance Description)
        btnEnhance.setOnClickListener {
            val originalText = editDescription.text.toString().trim()
            if (originalText.isNotEmpty()) {
                textStatus.text = "Enhancing description..."
                textStatus.visibility = View.VISIBLE
                btnEnhance.isEnabled = false
                btnEnhance.imageAlpha = 128

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val repository = com.example.selftracker.repository.GoalGeneratorRepository()
                        val enhanced = repository.enhanceGoalDescription(originalText)
                        
                        withContext(Dispatchers.Main) {
                            btnEnhance.isEnabled = true
                            btnEnhance.imageAlpha = 255
                            textStatus.visibility = View.GONE
                            editDescription.setText(enhanced)
                            Toast.makeText(context, "Description enhanced! ✨", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            btnEnhance.isEnabled = true
                            btnEnhance.imageAlpha = 255
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
                // Hide keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(dialogView.windowToken, 0)
                
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
                                        
                                        // Generate Icon
                                        withContext(Dispatchers.Main) { textStatus.text = "Designing icon..." }
                                        val iconXml = repository.generateGoalIcon(generatedGoal.goalTitle)
                                        val iconFileName = "goal_icon_${System.currentTimeMillis()}.xml"
                                        val iconFile = java.io.File(requireContext().filesDir, iconFileName)
                                        iconFile.writeText(iconXml)
                                        
                                        withContext(Dispatchers.Main) {
                                            btnGenerate.isEnabled = true
                                            progressGen.visibility = View.GONE
                                            textStatus.visibility = View.GONE
                                            
                                            val layoutSteps = dialogView.findViewById<LinearLayout>(R.id.layout_generated_steps) // Dynamically find view
                                            
                                            // Auto-fill the optimized title
                                            editName.setText(generatedGoal.goalTitle)
                                            editName.tag = iconFile.absolutePath // Stash path
                                            
                                            // FIX: Assign to outer variable so it can be saved
                                            generatedSteps = generatedGoal.steps
                                            
                                            layoutSteps.visibility = View.VISIBLE
                                            recyclerSteps.visibility = View.VISIBLE
                                            recyclerSteps.adapter = AiStepsAdapter(generatedSteps)
                                            Toast.makeText(context, "Plan ready! Title updated.", Toast.LENGTH_SHORT).show()
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
                val iconPath = editName.tag as? String
                val goal = Goal(
                    name = name,
                    description = editDescription.text.toString().trim().takeIf { it.isNotEmpty() },
                    localIconPath = iconPath
                )
                
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // 1. Save Goal
                        val goalId = database.goalDao().insertGoal(goal)
                        android.util.Log.d("GoalsFragment", "Saved Goal ID: $goalId")
                        
                        // 2. Save Generated Steps (if any)
                        android.util.Log.d("GoalsFragment", "Generated Steps Count: ${generatedSteps.size}")
                        
                        if (generatedSteps.isNotEmpty()) {
                            generatedSteps.forEachIndexed { index, genStep ->
                                    val step = com.example.selftracker.models.GoalStep(
                                        goalId = goalId,
                                        name = genStep.stepName,
                                        description = genStep.description,
                                        orderIndex = index,
                                        duration = genStep.durationValue,
                                        durationUnit = genStep.durationUnit
                                    )
                                val stepId = database.goalStepDao().insertGoalStep(step)
                                android.util.Log.d("GoalsFragment", "Saved Step ID: $stepId")
                                
                        // 3. Save Substeps
                        val subStepsList = genStep.substeps ?: emptyList() // Safety check
                        
                        subStepsList.forEachIndexed { subIndex, subStepData ->
                             val subStep = com.example.selftracker.models.GoalSubStep(
                                 stepId = stepId,
                                 name = subStepData.name,
                                 orderIndex = subIndex,
                                 duration = subStepData.durationValue,
                                 durationUnit = subStepData.durationUnit
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
                    } catch (e: Exception) {
                        android.util.Log.e("GoalsFragment", "Error saving goal", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a goal name", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
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
            val stepDescription: TextView = view.findViewById(R.id.text_step_description)
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
            holder.stepDescription.text = step.description
            holder.duration.text = "${step.durationValue} ${step.durationUnit}"
            
            val subSteps = step.substeps ?: emptyList()
            if (subSteps.isNotEmpty()) {
                holder.substeps.visibility = View.VISIBLE
                holder.substeps.text = subSteps.joinToString("\n• ", prefix = "• ")
            } else {
                holder.substeps.visibility = View.GONE
            }
        }

        override fun getItemCount() = steps.size
    }

    private fun loadIconFromFile(path: String?): android.graphics.drawable.Drawable? {
        if (path == null) return null
        val file = java.io.File(path)
        if (!file.exists()) {
            android.util.Log.e("GoalsFragment", "Icon file not found: $path")
            return null
        }
        
        return try {
            val fis = java.io.FileInputStream(file)
            val parser = android.util.Xml.newPullParser()
            parser.setInput(fis, "UTF-8")
            
            // Skip to first tag
            var type = parser.eventType
            while (type != org.xmlpull.v1.XmlPullParser.START_TAG && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                type = parser.next()
            }
            
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) {
                android.util.Log.e("GoalsFragment", "No start tag found")
                return null
            }

            android.util.Log.d("GoalsFragment", "Parsing icon from $path")
            
            // Use VectorDrawableCompat manually
            androidx.vectordrawable.graphics.drawable.VectorDrawableCompat.createFromXml(resources, parser, null)
            
        } catch (e: Exception) {
            android.util.Log.e("GoalsFragment", "Error loading icon", e)
            null
        }
    }

}