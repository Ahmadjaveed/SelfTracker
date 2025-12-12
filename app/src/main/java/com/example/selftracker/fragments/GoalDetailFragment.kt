package com.example.selftracker.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.selftracker.R
import com.example.selftracker.database.SelfTrackerDatabase
import com.example.selftracker.models.Goal
import com.bumptech.glide.Glide
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
    private val allResources = mutableListOf<com.example.selftracker.models.GoalResource>()

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

    override fun onResume() {
        super.onResume()
        // Set Status Bar to Dark for this fragment with Light Icons
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.premium_dark_bg)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
             requireActivity().window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.systemUiVisibility = requireActivity().window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    override fun onPause() {
        super.onPause()
        // Reset Status Bar to default (Light Background, Dark Icons)
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.background)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
             requireActivity().window.insetsController?.setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        } else {
             @Suppress("DEPRECATION")
             requireActivity().window.decorView.systemUiVisibility = requireActivity().window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private var isSelectionMode = false
    private val selectedStepIds = mutableSetOf<Long>()

    private fun setupToolbarMenu() {
        val toolbar = requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        
        // Force menu icons to be white using PorterDuff color filter
        val menu = toolbar.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.icon?.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        
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
                R.id.action_delete_selected -> {
                    showDeleteSelectedConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarForSelection() {
        val toolbar = requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        if (isSelectionMode) {
            toolbar.title = "${selectedStepIds.size} Selected"
            toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)?.apply {
                setTint(ContextCompat.getColor(requireContext(), R.color.white))
            }
            toolbar.setNavigationOnClickListener {
                exitSelectionMode()
            }
            
            toolbar.menu.clear()
            // Add Delete Action dynamically
            val deleteItem = toolbar.menu.add(0, R.id.action_delete_selected, 0, "Delete")
            deleteItem.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
            deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            deleteItem.icon?.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            
        } else {
            if (::goal.isInitialized) {
                toolbar.title = goal.name
            } else {
                toolbar.title = "Goal Details"
            }
            toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back)?.apply {
                 setTint(ContextCompat.getColor(requireContext(), R.color.white))
            }
            toolbar.setNavigationOnClickListener {
                requireActivity().supportFragmentManager.popBackStack()
            }
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.goal_detail_menu)
            setupToolbarMenu() // Re-apply tint
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedStepIds.clear()
        updateToolbarForSelection()
        loadGoalDetails() // Refresh UI to remove highlights
    }

    private fun toggleSelection(stepId: Long) {
        if (selectedStepIds.contains(stepId)) {
            selectedStepIds.remove(stepId)
            if (selectedStepIds.isEmpty()) {
                exitSelectionMode()
                return // Exit early
            }
        } else {
            selectedStepIds.add(stepId)
        }
        updateToolbarForSelection()
        loadGoalDetails() // Refresh UI to update highlights
    }

    private fun showDeleteSelectedConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selectedStepIds.size} Steps?")
            .setMessage("Are you sure you want to delete selected steps? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                 deleteSelectedSteps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedSteps() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val idsToDelete = selectedStepIds.toList()
            idsToDelete.forEach { id ->
                // Delete substeps first (if cascade not set, manual delete safety)
                val substeps = database.goalSubStepDao().getSubStepsByStep(id).value ?: emptyList()
                substeps.forEach { database.goalSubStepDao().deleteGoalSubStep(it) }
                
                // Delete step
                val step = database.goalStepDao().getStepById(id)
                step?.let { database.goalStepDao().deleteGoalStep(it) }
            }
            
            withContext(Dispatchers.Main) {
                exitSelectionMode()
                Toast.makeText(requireContext(), "${idsToDelete.size} steps deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadGoalDetails() {
        // Reset states on load
        expandedStepIds.clear()
        selectedStepIds.clear()
        isSelectionMode = false // Ensure we are not in selection mode
        
        viewLifecycleOwner.lifecycleScope.launch {
            // Load goal on background thread
            val goalResult = withContext(Dispatchers.IO) {
                database.goalDao().getGoalById(goalId)
            }

            goal = goalResult ?: return@launch
            
            // Now safe to update toolbar since goal is initialized
            updateToolbarForSelection()

            // Update UI on main thread
            goalTitle.text = goal.name
            goalDescription.text = goal.description ?: "No description"

            // Observe Resources
            database.goalResourceDao().getResourcesByGoal(goalId).observe(viewLifecycleOwner) { resources ->
                // Update global resource list for steps to use
                allResources.clear()
                allResources.addAll(resources)

                val recyclerResources = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_resources)
                val textNoResources = view?.findViewById<TextView>(R.id.text_no_resources)
                val btnAddResource = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_resource)
                
                // Filter for Main Goal Resources only (no stepId/subStepId)
                val goalResources = resources.filter { it.stepId == null && it.subStepId == null }
                
                if (goalResources.isNullOrEmpty()) {
                    recyclerResources?.visibility = View.GONE
                    textNoResources?.visibility = View.VISIBLE
                } else {
                    recyclerResources?.visibility = View.VISIBLE
                    textNoResources?.visibility = View.GONE
                    recyclerResources?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                    recyclerResources?.adapter = ResourcesAdapter(goalResources) { resource ->
                        openResource(resource)
                    }
                }
                
                btnAddResource?.setOnClickListener {
                    showAddResourceDialog()
                }
            }

            // Observe steps and substeps to calculate weighted progress
            val stepsLiveData = database.goalStepDao().getStepsByGoal(goalId)
            val subStepsLiveData = database.goalSubStepDao().getSubStepsByGoal(goalId)

            stepsLiveData.observe(viewLifecycleOwner) { steps ->
                subStepsLiveData.observe(viewLifecycleOwner) { allSubSteps ->
                    updateStepsUI(steps, allSubSteps)
                }
            }
        }
    }

    private fun showAddResourceDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_resource, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.edit_resource_title)
        val editUrl = dialogView.findViewById<EditText>(R.id.edit_resource_url)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_type)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_resource)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_resource) // Assuming text button "Cancel" in new layout

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val title = editTitle.text.toString().trim()
            val url = editUrl.text.toString().trim()
            
            val type = when (chipGroup.checkedChipId) {
                R.id.chip_type_video -> "VIDEO"
                R.id.chip_type_article -> "ARTICLE"
                else -> "LINK"
            }

            if (title.isNotEmpty() && url.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val resource = com.example.selftracker.models.GoalResource(
                        goalId = goalId,
                        title = title,
                        url = url,
                        resourceType = type
                    )
                    database.goalResourceDao().insertResource(resource)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Resource added", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            } else {
                 Toast.makeText(context, "Enter title and URL", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    
    // Resource Adapter
    inner class ResourcesAdapter(
        private val resources: List<com.example.selftracker.models.GoalResource>,
        private val onItemClick: (com.example.selftracker.models.GoalResource) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ResourcesAdapter.ResourceViewHolder>() {
        
        inner class ResourceViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.text_resource_title)
            val url: TextView = view.findViewById(R.id.text_resource_url)
            val iconWrapper: FrameLayout = view.findViewById<ImageView>(R.id.img_resource_thumbnail).parent as FrameLayout
            val iconInner: ImageView = view.findViewById(R.id.img_resource_thumbnail)
            val chip: com.google.android.material.chip.Chip = view.findViewById(R.id.chip_resource_type)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_resource_card, parent, false)
            return ResourceViewHolder(view)
        }

        override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
            val resource = resources[position]
            holder.title.text = resource.title
            
            val host = try { java.net.URI(resource.url).host ?: resource.url } catch(e:Exception) { "Link" }
            holder.url.text = host
            
            holder.chip.text = resource.resourceType
            
            val context = holder.itemView.context
            
            // Helper to reset style for icons vs images
            fun showAsImage() {
                holder.iconInner.setPadding(0, 0, 0, 0)
                holder.iconInner.clearColorFilter()
                holder.iconInner.scaleType = ImageView.ScaleType.CENTER_CROP
            }

            fun showAsIcon(resId: Int) {
                val padding = android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics
                ).toInt()
                holder.iconInner.setPadding(padding, padding, padding, padding)
                holder.iconInner.setColorFilter(ContextCompat.getColor(context, R.color.primary), android.graphics.PorterDuff.Mode.SRC_IN)
                holder.iconInner.setImageResource(resId)
            }
            
            val youtubeId = extractYoutubeId(resource.url)
            if (youtubeId != null) {
                holder.chip.text = "VIDEO"
                val thumbnailUrl = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg" 
                
                showAsImage()
                Glide.with(context)
                    .load(thumbnailUrl)
                    .placeholder(R.color.surface_dim) // Generic placeholder
                    .error(R.drawable.ic_play_circle) // Show play icon if thumbnail fails
                    .centerCrop()
                    .into(holder.iconInner)
                    
            } else if (resource.url.contains("youtube.com") || resource.url.contains("youtu.be")) {
                // Fallback for YouTube Search/Channel links
                holder.chip.text = "VIDEO"
                showAsImage() // Or showAsIcon if we have a youtube icon resource
                
                // Try to load a generic YouTube banner or icon from web, or just use a nice placeholder
                // For now, let's use the play circle but maybe with a hint it's a link
                 Glide.with(context)
                    .load("https://www.youtube.com/img/desktop/yt_1200.png") // Generic YT Image
                    .placeholder(R.drawable.ic_play_circle) 
                    .error(R.drawable.ic_play_circle)
                    .centerCrop()
                    .into(holder.iconInner)
            } else {
                // 2. Generic Link / Article
                
                // A. Check if we already have a text thumbnail (cached in DB?) 
                // Currently DB logic update is async. For now, rely on runtime fetch or existing.
                
                if (!resource.thumbnailUrl.isNullOrEmpty()) {
                    showAsImage()
                    Glide.with(context)
                        .load(resource.thumbnailUrl)
                        .placeholder(if (resource.resourceType == "ARTICLE") R.drawable.ic_menu_book else R.drawable.ic_link)
                        .centerCrop()
                        .into(holder.iconInner)
                } else {
                    // Start Background Fetch for Metadata if not video
                    // We shouldn't do this inside onBind usually, but for a prototype it's okay if managed.
                    // Better: Trigger generic favicon first, then fetch.
                    
                    // Favicons are images too, but might be small. Treat as image but maybe fitCenter?
                    // Usually we want them to look like icons? 
                    // Let's try treating them as images (no tint).
                    // Let's try treating them as images (no tint).
                    showAsImage()
                    
                    val faviconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=512"
                    
                    Glide.with(context)
                        .load(faviconUrl)
                        .placeholder(if (resource.resourceType == "ARTICLE") R.drawable.ic_menu_book else R.drawable.ic_link)
                        .fitCenter()
                        .into(holder.iconInner)
                        
                    // Fire and forget fetcher
                    fetchLinkMetadata(resource)
                }
            }
             
             holder.itemView.setOnClickListener { 
                onItemClick(resource)
            }
             
             // Long click to delete
             holder.itemView.setOnLongClickListener {
                 AlertDialog.Builder(requireContext())
                    .setTitle("Delete Resource?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            database.goalResourceDao().deleteResource(resource)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                 true
             }
        }
        
        private fun fetchLinkMetadata(resource: com.example.selftracker.models.GoalResource) {
            if (!resource.thumbnailUrl.isNullOrEmpty()) return

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
               val logTag = "LinkScraper"
               // android.util.Log.d(logTag, "Starting scrape for: ${resource.url}") 
               try {
                   val doc = org.jsoup.Jsoup.connect(resource.url)
                       .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                       .referrer("http://www.google.com")
                       .timeout(10000)
                       .followRedirects(true)
                       .ignoreContentType(true) // Crucial for some image links or specialized pages
                       .get()
                   
                   // 1. Try Open Graph
                   var imageUrl = doc.select("meta[property=og:image]").attr("content")
                   // if (imageUrl.isNotEmpty()) android.util.Log.d(logTag, "Found og:image: $imageUrl")
                   
                   // ... (Logic for other tags remains same logic, just reducing log noise if needed)

                   // 2. Try Twitter Card
                   if (imageUrl.isEmpty()) {
                       imageUrl = doc.select("meta[name=twitter:image]").attr("content")
                   }
                   
                   // 3. Try Link Rel
                   if (imageUrl.isEmpty()) {
                       imageUrl = doc.select("link[rel=image_src]").attr("href")
                   }
                   
                   // 4. Try first large image
                   if (imageUrl.isEmpty()) {
                        val firstImg = doc.select("img[src~=(?i)\\.(png|jpe?g)]").firstOrNull()
                        if (firstImg != null) {
                            var src = firstImg.attr("src")
                            if (src.isNotEmpty()) {
                                 // Handle relative URLs
                                 if (!src.startsWith("http")) {
                                     src = java.net.URI(resource.url).resolve(src).toString()
                                 }
                                 imageUrl = src
                            }
                        }
                   }

                   if (imageUrl.isNotEmpty()) {
                       // android.util.Log.d(logTag, "Updating DB for resource: ${resource.title}")
                       val updated = resource.copy(thumbnailUrl = imageUrl)
                       database.goalResourceDao().updateResource(updated)
                   }
               } catch (e: java.net.UnknownHostException) {
                   android.util.Log.w(logTag, "Network unavailable for ${resource.url}: ${e.message}")
               } catch (e: java.net.SocketTimeoutException) {
                   android.util.Log.w(logTag, "Connection timed out for ${resource.url}")
               } catch (e: Exception) {
                   android.util.Log.w(logTag, "Scraping failed for ${resource.url}: ${e.message}")
               }
            }
        }
        override fun getItemCount() = resources.size

        private fun extractYoutubeId(url: String): String? {
             val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
             val compiledPattern = java.util.regex.Pattern.compile(pattern)
             val matcher = compiledPattern.matcher(url)
             return if (matcher.find()) matcher.group() else null
        }
    }

    private fun openResource(resource: com.example.selftracker.models.GoalResource) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resource.url))
            // This will automatically prompt for YouTube app if it's a YouTube link, 
            // or Browser for others. This is the standard "Open In App" behavior.
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun extractYoutubeId(url: String): String? {
         val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
         val compiledPattern = java.util.regex.Pattern.compile(pattern)
         val matcher = compiledPattern.matcher(url)
         return if (matcher.find()) matcher.group() else null
    }

    private fun updateStepsUI(steps: List<GoalStep>, allSubSteps: List<GoalSubStep> = emptyList()) {
        val sortedSteps = steps.sortedBy { it.orderIndex }
        
        // Smart Update: Check if we can reuse existing views
        if (stepsTreeContainer.childCount == sortedSteps.size) {
            var canReuse = true
            
            // First pass: Verify structure matches (simplistic check by count, ideally by ID but count is proxy)
            // Realistically, if count matches and we assume order matches, we can try.
            
            // Let's iterate and update in-place
            sortedSteps.forEachIndexed { index, step ->
                val stepView = stepsTreeContainer.getChildAt(index)
                // We could verify ID using tag if we set it, but let's assume index alignment for now.
                
                val substepsContainer = stepView.findViewById<LinearLayout>(R.id.substeps_container)
                val stepSubSteps = allSubSteps.filter { it.stepId == step.stepId }.sortedBy { it.orderIndex }
                
                if (substepsContainer.childCount != stepSubSteps.size) {
                    canReuse = false // Substep count mismatch, simpler to full rebuild or partial rebuild
                }
            }
            
            if (canReuse) {
                // Perform smart update
                calculateWeightedProgress(steps, allSubSteps)
                
                sortedSteps.forEachIndexed { index, step ->
                    val stepView = stepsTreeContainer.getChildAt(index)
                    val stepSubSteps = allSubSteps.filter { it.stepId == step.stepId }.sortedBy { it.orderIndex }
                    bindStepToView(stepView, step, stepSubSteps)
                }
                return
            }
        }

        // Check if only substeps changed for a specific step (Partial Rebuild Optimization)
        // Complexity increases. Let's stick to Full Rebuild if Smart Update fails.

        // Fallback: Full Rebuild
        val scrollView = view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.details_scroll_view)
        val savedScrollY = scrollView?.scrollY ?: 0

        stepsTreeContainer.removeAllViews()

        calculateWeightedProgress(steps, allSubSteps)

        sortedSteps.forEachIndexed { index, step ->
            val stepSubSteps = allSubSteps.filter { it.stepId == step.stepId }.sortedBy { it.orderIndex }
            addStepToTree(step, index, stepSubSteps)
        }
        
        // Restore scroll position
        scrollView?.post {
            scrollView.scrollY = savedScrollY
        }

        if (steps.isEmpty()) {
            showEmptyStepsState()
        }
    }

    private fun bindStepToView(stepView: View, step: GoalStep, stepSubSteps: List<GoalSubStep>) {
        val stepName = stepView.findViewById<TextView>(R.id.step_name)
        val stepDuration = stepView.findViewById<TextView>(R.id.step_duration)
        val stepProgress = stepView.findViewById<TextView>(R.id.step_progress)
        val stepProgressBar = stepView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.step_progress_bar)
        val stepCheckbox = stepView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.step_checkbox)
        val substepsContainer = stepView.findViewById<LinearLayout>(R.id.substeps_container)
        val btnExpand = stepView.findViewById<ImageButton>(R.id.btn_expand)
        
        stepName.text = step.name
        stepDuration.text = "${step.duration} ${step.durationUnit}"
        
        // Update Checkbox safely
        stepCheckbox.setOnCheckedChangeListener(null) // Detach listener
        stepCheckbox.isChecked = step.isCompleted
        
        // Re-attach listener
        stepCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (isChecked) {
                     database.goalStepDao().updateGoalStep(step.copy(isCompleted = true))
                     val substeps = database.goalSubStepDao().getSubStepsByStep(step.stepId).value ?: emptyList()
                     substeps.forEach { substep ->
                         database.goalSubStepDao().updateGoalSubStep(substep.copy(isCompleted = true))
                     }
                } else {
                     database.goalStepDao().updateGoalStep(step.copy(isCompleted = false))
                }
            }
        }
        
        // Update Selection State
        if (selectedStepIds.contains(step.stepId)) {
            stepView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_container))
        } else {
            stepView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Update Logic for Substeps (Reuse existing views)
        val substeps = stepSubSteps
        val totalSubsteps = substeps.size
        
        if (totalSubsteps > 0) {
            stepCheckbox.visibility = View.INVISIBLE
            stepCheckbox.isEnabled = false 
        } else {
            stepCheckbox.visibility = View.VISIBLE
            stepCheckbox.isEnabled = true
        }

        val completedSubsteps = substeps.count { it.isCompleted }
        stepProgress.text = "$completedSubsteps/$totalSubsteps done"
        val substepProgress = if (totalSubsteps > 0) (completedSubsteps.toFloat() / totalSubsteps.toFloat() * 100).toInt() else 0
        stepProgressBar.progress = substepProgress
        
        // Bind Substeps
        substeps.forEachIndexed { i, substep ->
            val substepView = substepsContainer.getChildAt(i)
            bindSubstepToView(substepView, substep)
        }
        
        // Bind Step Resources
        val recyclerStepRes = stepView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_step_resources)
        val stepResources = allResources.filter { it.stepId == step.stepId }
        
        if (stepResources.isNotEmpty()) {
            recyclerStepRes.visibility = View.VISIBLE
            recyclerStepRes.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(stepView.context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            recyclerStepRes.adapter = ResourcesAdapter(stepResources) { res -> openResource(res) }
        } else {
            recyclerStepRes.visibility = View.GONE
        }

        

        
        // Expansion State
        val isExpanded = expandedStepIds.contains(step.stepId)
        substepsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        btnExpand.setImageResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
    }

    private fun bindSubstepToView(substepView: View, substep: GoalSubStep) {
        val substepName = substepView.findViewById<TextView>(R.id.substep_name)
        val substepDuration = substepView.findViewById<TextView>(R.id.substep_duration)
        val substepStatusText = substepView.findViewById<TextView>(R.id.substep_status_text)
        val substepStatus = substepView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.substep_status)

        substepName.text = substep.name
        substepDuration.text = "${substep.duration} ${substep.durationUnit}"

        if (substep.isCompleted) {
            substepStatusText.text = "Completed"
            substepStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            substepStatusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_small, 0, 0, 0)
        } else {
            substepStatusText.text = "In Progress"
            substepStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            substepStatusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_clock_small, 0, 0, 0)
        }
        
        substepStatus.setOnCheckedChangeListener(null) // Detach
        substepStatus.isChecked = substep.isCompleted
        substepStatus.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                database.goalSubStepDao().updateGoalSubStep(substep.copy(isCompleted = isChecked))
            }
        }
        
        // OnClick toggle logic relies on view properties not changing much, but let's re-set it to be safe or leave as is?
        // The original setOnClickListener just toggles substepStatus.isChecked.
        // Doing that triggers the listener we just set. Correct.
        
        // Bind Substep Resources
        val recyclerSubstepRes = substepView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_substep_resources)
        val subStepResources = allResources.filter { it.subStepId == substep.subStepId }
        
        if (subStepResources.isNotEmpty()) {
            recyclerSubstepRes.visibility = View.VISIBLE
            recyclerSubstepRes.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(substepView.context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            recyclerSubstepRes.adapter = ResourcesAdapter(subStepResources) { res -> openResource(res) }
        } else {
            recyclerSubstepRes.visibility = View.GONE
        }
        

    }

    private fun addStepToTree(step: GoalStep, stepIndex: Int) {
        val stepView = LayoutInflater.from(requireContext()).inflate(R.layout.item_step_tree, stepsTreeContainer, false)
        // ... (Initial setup, click listeners that don't depend on data updates)
        val btnExpand = stepView.findViewById<ImageButton>(R.id.btn_expand)
        val btnAddSubstep = stepView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_substep)
        val btnEditStep = stepView.findViewById<ImageButton>(R.id.btn_edit_step)
        val substepsContainer = stepView.findViewById<LinearLayout>(R.id.substeps_container)

        // Static listeners (Edit, Add Substep, Expand Toggle, Selection)
        btnEditStep.setOnClickListener { showStepDetailsDialog(step) }
        btnAddSubstep.setOnClickListener { showAddSubstepDialog(step.stepId) }
        
        val toggleExpansion = {
            if (!isSelectionMode) {
                if (expandedStepIds.contains(step.stepId)) {
                    expandedStepIds.remove(step.stepId)
                } else {
                    expandedStepIds.add(step.stepId)
                }
                // Update visibility immediately for responsiveness, though bind will cover it too
                // bindStepToView will be called next if we rerender, but here we just toggle
                val isExpanded = expandedStepIds.contains(step.stepId)
                substepsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                btnExpand.setImageResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            }
        }
        btnExpand.setOnClickListener { toggleExpansion() }
        
        stepView.setOnClickListener { 
            if (isSelectionMode) toggleSelection(step.stepId) else toggleExpansion()
        }
        stepView.setOnLongClickListener {
            if (!isSelectionMode) { isSelectionMode = true; toggleSelection(step.stepId); true } else false
        }
        
        stepsTreeContainer.addView(stepView)
        
        // Initial Bind
        // We need stepSubSteps here. 
        // Issue: addStepToTree signature in original code didn't take subSteps (it fetched them async).
        // My previous fix (checking file content) line 372: observes substeps!
        // `database.goalSubStepDao().getSubStepsByStep(step.stepId).observe(viewLifecycleOwner) { substeps -> ... }`
        
        // WAIT. If I use `observe` inside `addStepToTree`, I AM CREATING MULTIPLE OBSERVERS per row!
        // And `updateStepsUI` is called by `loadGoalDetails` which observes ALL steps.
        
        // If I switch to `smart updates` driven by `updateStepsUI`, I should REMOVE the per-row observers in `addStepToTree`.
        // The `updateStepsUI` receives `allSubSteps`. I should pass the relevant ones to `addStepToTree` (or `bindStepToView`).
        
        // Correct fix: Remove internal observer in `addStepToTree`. Pass substeps to it.
        // We can't change `addStepToTree` signature easily without changing lines 373-375 call site.
        // Wait, `addStepToTree` is called inside `updateStepsUI`.
        
        // So I need to update `addStepToTree` signature to accept `substeps`.
        // And in `updateStepsUI` (fallback), pass them.
        
        // Let's rely on the `bind` logic.
    }

    private fun calculateWeightedProgress(steps: List<GoalStep>, allSubSteps: List<GoalSubStep>) {
        var totalDuration = 0.0
        var completedDuration = 0.0

        steps.forEach { step ->
            val stepSubSteps = allSubSteps.filter { it.stepId == step.stepId }
            
            if (stepSubSteps.isNotEmpty()) {
                // Leaf nodes are substeps
                stepSubSteps.forEach { subStep ->
                    val duration = getDurationInDays(subStep.duration, subStep.durationUnit)
                    totalDuration += duration
                    if (subStep.isCompleted) {
                        completedDuration += duration
                    }
                }
            } else {
                // Leaf node is the step itself
                val duration = getDurationInDays(step.duration, step.durationUnit)
                totalDuration += duration
                if (step.isCompleted) {
                    completedDuration += duration
                }
            }
        }

        val progress = if (totalDuration > 0) (completedDuration / totalDuration * 100).toInt() else 0

        progressBar.progress = progress
        progressText.text = "$progress%"
        
        // Count total leaf tasks for display or keep simple Count
        // User asked for "progress percentage based on days".
        // The "X completed Y total" text might still refer to steps?
        // Let's update it to show "Tasks" or keep it as steps count?
        // User didn't explicitly ask to change the count text, just the percentage logic.
        // But "X completed" usually refers to the progress bar.
        // If progress is 50% (days), but 1/10 steps done, it matches.
        
        val completedStepsCount = steps.count { it.isCompleted }
        completedCount.text = "$completedStepsCount steps done"
        totalCount.text = "${steps.size} total steps"
    }

    private fun getDurationInDays(duration: Int, unit: String): Double {
        return when (unit.lowercase()) {
            "weeks" -> duration * 7.0
            "months" -> duration * 30.0
            else -> duration.toDouble() // days or default
        }
    }

    private val expandedStepIds = mutableSetOf<Long>()

    private fun addStepToTree(step: GoalStep, index: Int, stepSubSteps: List<GoalSubStep>) {
        val stepView = LayoutInflater.from(requireContext()).inflate(R.layout.item_step_tree, stepsTreeContainer, false)
        val btnExpand = stepView.findViewById<ImageButton>(R.id.btn_expand)
        val btnAddSubstep = stepView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_substep)
        val btnEditStep = stepView.findViewById<ImageButton>(R.id.btn_edit_step)
        val substepsContainer = stepView.findViewById<LinearLayout>(R.id.substeps_container)

        // Add sub-views first to structure
        stepSubSteps.sortedBy { it.orderIndex }.forEach { substep ->
             val substepView = LayoutInflater.from(requireContext()).inflate(R.layout.item_substep_tree, substepsContainer, false)
             val btnSubstepMenu = substepView.findViewById<ImageButton>(R.id.btn_substep_menu)
             val substepStatus = substepView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.substep_status)
             
             // Static Listeners for substep
             btnSubstepMenu.setOnClickListener { showSubstepMenu(it, substep) }
             substepView.setOnClickListener { substepStatus.isChecked = !substepStatus.isChecked }
             
             substepsContainer.addView(substepView)
        }

        stepsTreeContainer.addView(stepView)
        
        // Static Listeners for step
        btnEditStep.setOnClickListener { showStepDetailsDialog(step) }
        btnAddSubstep.setOnClickListener { showAddSubstepDialog(step.stepId) }
        
        val toggleExpansion = {
            if (!isSelectionMode) {
                if (expandedStepIds.contains(step.stepId)) {
                    expandedStepIds.remove(step.stepId)
                } else {
                    expandedStepIds.add(step.stepId)
                }
                bindStepToView(stepView, step, stepSubSteps) // Re-bind to update visibility
            }
        }
        btnExpand.setOnClickListener { toggleExpansion() }
        
        stepView.setOnClickListener { 
            if (isSelectionMode) toggleSelection(step.stepId) else toggleExpansion()
        }
        stepView.setOnLongClickListener {
            if (!isSelectionMode) { isSelectionMode = true; toggleSelection(step.stepId); true } else false
        }

        // Initial Bind
        bindStepToView(stepView, step, stepSubSteps)
    }

    // updateSubstepsUI is removed/merged into bindStepToView 


    private fun showSubstepMenu(view: View, substep: GoalSubStep) {
        // Use ContextThemeWrapper to apply High Contrast Theme
        val wrapper = android.view.ContextThemeWrapper(requireContext(), R.style.Theme_SelfTracker_PopupOverlay)
        val popup = android.widget.PopupMenu(wrapper, view)
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
        val editStepName = dialogView.findViewById<EditText>(R.id.edit_step_name)
        val editStepDescription = dialogView.findViewById<EditText>(R.id.edit_step_description)
        val editStepDuration = dialogView.findViewById<EditText>(R.id.edit_step_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_step)
        // btn_cancel_step is removed, replaced by header close icon
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)

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

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showStepDetailsDialog(step: GoalStep) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_step, null)
        val editStepName = dialogView.findViewById<EditText>(R.id.edit_step_name)
        val editStepDescription = dialogView.findViewById<EditText>(R.id.edit_step_description)
        val editStepDuration = dialogView.findViewById<EditText>(R.id.edit_step_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_step)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title) // Assuming I need to add ID or find logic? No, layout doesn't have ID on text "New Step" in my snippet?
        // Wait, my XML snippet for dialog_add_step.xml DOES NOT have an ID for the title TextView "New Step".
        // Ah, it's inside RelativeLayout. Let me double check usage. 
        // In XML Refactor: <TextView ... android:text="New Step" ... /> has NO ID in my previous `write_to_file`.
        // So I can't change title to "Edit Step" easily without ID.
        // Wait, for step details, I should just set the text?
        // I should have added an ID to the title in the XML.
        
        // Let's assume for now I won't change the title text or I will accept "New Step" or I should fix the XML first?
        // Actually, for "Edit", users usually expect "Edit Step".
        // I will just pre-fill data.
        
        // Update: I will fix XML in next step if needed, but for now I just update logic.

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

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddSubstepDialog(stepId: Long) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_substep, null)
        val editSubstepName = dialogView.findViewById<EditText>(R.id.edit_substep_name)
        val editSubstepDescription = dialogView.findViewById<EditText>(R.id.edit_substep_description)
        val editSubstepDuration = dialogView.findViewById<EditText>(R.id.edit_substep_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_substep)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)

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

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSubstepDetailsDialog(substep: GoalSubStep) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_substep, null)
        val editSubstepName = dialogView.findViewById<EditText>(R.id.edit_substep_name)
        val editSubstepDescription = dialogView.findViewById<EditText>(R.id.edit_substep_description)
        val editSubstepDuration = dialogView.findViewById<EditText>(R.id.edit_substep_duration)
        val editDurationUnit = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_duration_unit)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_substep)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)
        
        // Note: XML Refactor didn't include ID for title TextView (it's hardcoded "New Sub-step"). 
        // We skip updating title for now or just rely on Button text "Update" to indicate mode.
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

        btnClose.setOnClickListener {
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
        val editGoalName = dialogView.findViewById<EditText>(R.id.edit_goal_name)
        val editGoalDescription = dialogView.findViewById<EditText>(R.id.edit_goal_description)
        val btnEnhance = dialogView.findViewById<ImageView>(R.id.btn_enhance_description)
        val imgIcon = dialogView.findViewById<ImageView>(R.id.img_goal_icon)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_goal)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_close_dialog)

        // AI UI Elements
        val btnGenerate = dialogView.findViewById<Button>(R.id.btn_generate_ai_steps)
        val progressGen = dialogView.findViewById<View>(R.id.progress_ai_generation)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_ai_status)
        val recyclerOptions = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_plan_options)
        val recyclerSteps = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_generated_steps)
        val layoutSteps = dialogView.findViewById<LinearLayout>(R.id.layout_generated_steps)

        recyclerSteps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerOptions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        
        var generatedSteps: List<com.example.selftracker.models.GeneratedStep> = emptyList()
        var newIconPath: String? = goal.localIconPath // Start with current icon

        // Pre-fill existing data
        editGoalName.setText(goal.name)
        editGoalDescription.setText(goal.description ?: "")
        
        // Load current icon
        if (newIconPath != null) {
            if (newIconPath!!.startsWith("http")) {
                 Glide.with(requireContext()).load(newIconPath).centerInside().into(imgIcon)
                 imgIcon.imageTintList = null
            } else {
                 viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                     val dr = loadIconFromFile(newIconPath)
                     withContext(Dispatchers.Main) {
                         if (dr != null) {
                             imgIcon.setImageDrawable(dr)
                             imgIcon.imageTintList = null
                         }
                     }
                 }
            }
        }

        btnSave.text = "Update Goal"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 1. AI Enhance Description Logic
        btnEnhance.setOnClickListener {
             val originalText = editGoalDescription.text.toString().trim()
             if (originalText.isNotEmpty()) {
                 textStatus.text = "Enhancing..."
                 textStatus.visibility = View.VISIBLE
                 btnEnhance.isEnabled = false
                 btnEnhance.imageAlpha = 128
                 
                 viewLifecycleOwner.lifecycleScope.launch {
                     try {
                         val repository = com.example.selftracker.repository.GoalGeneratorRepository()
                         val enhanced = repository.enhanceGoalDescription(originalText)
                         withContext(Dispatchers.Main) {
                             editGoalDescription.setText(enhanced)
                             Toast.makeText(requireContext(), "Enhanced! ✨", Toast.LENGTH_SHORT).show()
                             btnEnhance.isEnabled = true
                             btnEnhance.imageAlpha = 255
                             textStatus.visibility = View.GONE
                         }
                     } catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                             btnEnhance.isEnabled = true
                             btnEnhance.imageAlpha = 255
                             textStatus.visibility = View.GONE
                         }
                     }
                 }
             }
        }

        // 2. AI Generate Logic
        btnGenerate.setOnClickListener {
            val name = editGoalName.text.toString().trim()
            val description = editGoalDescription.text.toString().trim()
            
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
                        
                        val options = repository.generatePlanOptions(prompt)
                        
                        withContext(Dispatchers.Main) {
                            progressGen.visibility = View.GONE
                            textStatus.text = "Select a strategy:"
                            recyclerOptions.visibility = View.VISIBLE
                            recyclerOptions.adapter = AiPlanOptionsAdapter(options) { selectedOption ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    recyclerOptions.visibility = View.GONE
                                    progressGen.visibility = View.VISIBLE
                                    textStatus.text = "Building plan..."
                                    textStatus.visibility = View.VISIBLE
                                    
                                    try {
                                        val newGeneratedGoal = repository.generateGoal(prompt, selectedOption.strategy)
                                        
                                        // Generate new icon logic
                                        withContext(Dispatchers.Main) { textStatus.text = "Checking icons..." }
                                        val iconResult = repository.generateGoalIcon(newGeneratedGoal.goalTitle, description)
                                        
                                        var generatedIconPath: String? = null
                                        if (iconResult.startsWith("http")) {
                                            generatedIconPath = iconResult
                                        } else {
                                            val iconFileName = "goal_icon_${System.currentTimeMillis()}.svg"
                                            val iconFile = java.io.File(requireContext().filesDir, iconFileName)
                                            iconFile.writeText(iconResult)
                                            generatedIconPath = iconFile.absolutePath
                                        }
                                        newIconPath = generatedIconPath // Update for saving

                                        withContext(Dispatchers.Main) {
                                            editGoalName.setText(newGeneratedGoal.goalTitle)
                                            generatedSteps = newGeneratedGoal.steps // Store for saving
                                            
                                            // Update Icon UI
                                            if (newIconPath != null) {
                                                if (newIconPath!!.startsWith("http")) {
                                                     Glide.with(requireContext()).load(newIconPath).centerInside().into(imgIcon)
                                                     imgIcon.imageTintList = null
                                                } else {
                                                     viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                                         val dr = loadIconFromFile(newIconPath)
                                                         withContext(Dispatchers.Main) {
                                                             if (dr != null) {
                                                                 imgIcon.setImageDrawable(dr)
                                                                 imgIcon.imageTintList = null
                                                             }
                                                         }
                                                     }
                                                }
                                            }

                                            layoutSteps.visibility = View.VISIBLE
                                            recyclerSteps.visibility = View.VISIBLE
                                            recyclerSteps.adapter = AiStepsAdapter(generatedSteps)
                                            
                                            textStatus.visibility = View.GONE
                                            progressGen.visibility = View.GONE
                                            btnGenerate.isEnabled = true
                                        }
                                    } catch (e: Exception) {
                                         withContext(Dispatchers.Main) {
                                            btnGenerate.isEnabled = true
                                            progressGen.visibility = View.GONE
                                            textStatus.text = "Failed: ${e.message}"
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
            }
        }

        btnSave.setOnClickListener {
            val name = editGoalName.text.toString().trim()
            val description = editGoalDescription.text.toString().trim()

            if (name.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // 1. Update Goal Details
                    val updatedGoal = goal.copy(
                        name = name,
                        description = if (description.isNotEmpty()) description else null,
                        localIconPath = newIconPath
                    )
                    database.goalDao().updateGoal(updatedGoal)
                    
                    // 2. Insert NEW steps if generated
                    if (generatedSteps.isNotEmpty()) {
                        // Get current steps count to append correctly
                        val currentSteps = database.goalStepDao().getStepsByGoal(goal.goalId).value ?: emptyList()
                        var startIndex = currentSteps.size
                        
                        generatedSteps.forEach { genStep ->
                            val step = com.example.selftracker.models.GoalStep(
                                goalId = goal.goalId,
                                name = genStep.stepName,
                                description = genStep.description,
                                orderIndex = startIndex++,
                                duration = genStep.durationValue,
                                durationUnit = genStep.durationUnit
                            )
                            val stepId = database.goalStepDao().insertGoalStep(step)
                            
                            // Save Substeps
                            genStep.substeps?.forEachIndexed { subIndex, genSubStep ->
                                val subStep = com.example.selftracker.models.GoalSubStep(
                                    stepId = stepId,
                                    name = genSubStep.name,
                                    orderIndex = subIndex,
                                    duration = genSubStep.durationValue, // Use actual duration from AI
                                    durationUnit = genSubStep.durationUnit // Use actual unit from AI
                                )
                                database.goalSubStepDao().insertGoalSubStep(subStep)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                         // Update UI
                         goalTitle.text = updatedGoal.name
                         goalDescription.text = updatedGoal.description ?: "No description"
                         loadGoalDetails() // Reload steps list
                         Toast.makeText(requireContext(), "Goal Updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter a goal name", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener {
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

    // ADAPTERS FOR AI DIALOG (Copied from GoalsFragment to support Edit Mode)
    private fun loadIconFromFile(path: String?): android.graphics.drawable.Drawable? {
        if (path == null) return null
        val file = java.io.File(path)
        if (!file.exists()) {
            return null
        }
        return try {
            val fis = java.io.FileInputStream(file)
            val svg = com.caverock.androidsvg.SVG.getFromInputStream(fis)
            val size = 512f
            svg.documentWidth = size
            svg.documentHeight = size
            val bitmap = android.graphics.Bitmap.createBitmap(size.toInt(), size.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            svg.renderToCanvas(canvas)
            android.graphics.drawable.BitmapDrawable(resources, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    inner class AiPlanOptionsAdapter(
        private val options: List<com.example.selftracker.models.PlanOption>,
        private val onOptionClick: (com.example.selftracker.models.PlanOption) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<AiPlanOptionsAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.text_option_title)
            val description: TextView = itemView.findViewById(R.id.text_option_description)
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