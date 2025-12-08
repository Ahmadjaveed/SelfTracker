package com.example.selftracker.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.selftracker.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val splashImage = findViewById<View>(R.id.splash_image)
        val appLogo = findViewById<View>(R.id.app_logo)
        val appTitle = findViewById<View>(R.id.app_title)
        val appTagline = findViewById<View>(R.id.app_tagline)

        // Animation Configuration
        val duration = 1000L
        val interpolator = DecelerateInterpolator()

        // 1. Fade in background illustration
        splashImage.animate()
            .alpha(0.8f) // Keep it slightly dimmed for text contrast
            .setDuration(1200)
            .setInterpolator(interpolator)
            .start()

        // 2. Animate Logo (Fade in + Scale Up)
        appLogo.translationY = 50f
        appLogo.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(duration)
            .setStartDelay(200)
            .setInterpolator(interpolator)
            .withEndAction {
                // Pulse effect after entry
                appLogo.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(500)
                    .start()
            }
            .start()

        // 3. Animate Text (Fade in + Slide Up)
        appTitle.translationY = 50f
        appTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(400)
            .setInterpolator(interpolator)
            .start()

        appTagline.translationY = 50f
        appTagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(600)
            .setInterpolator(interpolator)
            .start()

        // Navigate to MainActivity after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}
