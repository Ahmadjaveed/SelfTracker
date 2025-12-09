package com.example.selftracker.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.selftracker.R
import com.example.selftracker.fragments.GoalsFragment
import com.example.selftracker.fragments.HabitsFragment
import com.example.selftracker.fragments.ProgressFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.qamar.curvedbottomnaviagtion.CurvedBottomNavigation

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: CurvedBottomNavigation
    private lateinit var fabAdd: FloatingActionButton
    private var currentFragment: Fragment? = null
    private var isFabVisible = true
    private var fabAnimator: ValueAnimator? = null
    
    // IDs for Navigation
    private val ID_HABITS = 1
    private val ID_GOALS = 2
    private val ID_PROGRESS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        initializeViews()
        setupSystemUi()
        setupFragments()
        setupBottomNavigation()
        setupFab()

        // Start FAB animation immediately
        startFabIdleAnimation()
    }

    private fun initializeViews() {
        bottomNavigation = findViewById(R.id.meow_bottom_navigation)
        fabAdd = findViewById(R.id.fab_add)

        // Make sure FAB is visible
        fabAdd.visibility = View.VISIBLE
        
        // Setup Bottom Navigation Items
        bottomNavigation.add(CurvedBottomNavigation.Model(ID_HABITS, "Habits", R.drawable.ic_habits_nav))
        bottomNavigation.add(CurvedBottomNavigation.Model(ID_GOALS, "Goals", R.drawable.ic_rocket_nav))
        bottomNavigation.add(CurvedBottomNavigation.Model(ID_PROGRESS, "Progress", R.drawable.ic_progress_nav))
        
        // Default selected
        bottomNavigation.show(ID_HABITS, true)
    }

    private fun setupSystemUi() {
        // Handle system window insets for proper layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
    }

    private fun setupFragments() {
        // Check if fragment container is empty
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            loadFragment(HabitsFragment())
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is com.example.selftracker.fragments.GoalDetailFragment) {
                fabAdd.hide()
            } else {
                 // Restore visibility if needed
                 fabAdd.show()
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnClickMenuListener {
            when (it.id) {
                ID_HABITS -> {
                    loadFragmentWithAnimation(HabitsFragment())
                    fabAdd.show()
                }
                ID_GOALS -> {
                    loadFragmentWithAnimation(GoalsFragment())
                    fabAdd.show()
                }
                ID_PROGRESS -> {
                    loadFragmentWithAnimation(ProgressFragment())
                    fabAdd.hide() // Optional: Hide add button on progress
                }
            }
        }
        
        // Not all forks support Reselect listener the same way, check docs. 
        // Qamar's fork usually supports setOnReselectListener
        bottomNavigation.setOnReselectListener {
            // Handle reselect (bounce or refresh)
             if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            } else {
                 showTemporaryMessage("Refreshed")
            }
        }
    }

    private fun setupFab() {
        fabAdd.setOnClickListener {
            animateFabClick()

            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (currentFragment) {
                is HabitsFragment -> {
                    currentFragment.showAddHabitDialog()
                }
                is GoalsFragment -> {
                    currentFragment.showAddGoalDialog()
                }
                else -> {
                    showTemporaryMessage("Select Habits or Goals tab to add items")
                }
            }
        }
    }

    private fun startFabIdleAnimation() {
        fabAnimator?.cancel()

        fabAnimator = ValueAnimator.ofFloat(-10f, 10f)
        fabAnimator?.duration = 2000
        fabAnimator?.repeatCount = ValueAnimator.INFINITE
        fabAnimator?.repeatMode = ValueAnimator.REVERSE
        fabAnimator?.interpolator = AccelerateDecelerateInterpolator()
        fabAnimator?.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            fabAdd.translationY = value
        }
        fabAnimator?.start()
    }

    private fun loadFragmentWithAnimation(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun animateFabClick() {
        fabAnimator?.cancel()
        fabAdd.animate().cancel()

        val scaleDownX = ObjectAnimator.ofFloat(fabAdd, View.SCALE_X, 1f, 0.7f)
        val scaleDownY = ObjectAnimator.ofFloat(fabAdd, View.SCALE_Y, 1f, 0.7f)
        val scaleUpX = ObjectAnimator.ofFloat(fabAdd, View.SCALE_X, 0.7f, 1.2f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(fabAdd, View.SCALE_Y, 0.7f, 1.2f, 1f)
        val rotation = ObjectAnimator.ofFloat(fabAdd, View.ROTATION, 0f, 360f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 100

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, rotation)
        scaleUp.duration = 400
        scaleUp.interpolator = OvershootInterpolator()

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(scaleDown, scaleUp)
        animatorSet.doOnEnd {
            startFabIdleAnimation()
        }
        animatorSet.start()
    }

    private fun showTemporaryMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fabAnimator?.cancel()
    }
}