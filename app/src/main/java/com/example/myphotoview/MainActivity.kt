package com.example.myphotoview

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        val photoView = findViewById<CustomZoomImageView>(R.id.iv_photo)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Feature: Zoom out and start activity on outside tap
        photoView.onOutsidePhotoTapListener = {
            android.widget.Toast.makeText(this, "Outside Tap! Resetting Scale & Starting Activity...", android.widget.Toast.LENGTH_SHORT).show()
        }
        val density = resources.displayMetrics.density

        // --- Demo for Requirement 1: Fixed Height (200dp) ---
        // val density = resources.displayMetrics.density
        // photoView.setFixedHeight((200 * density).toInt())

        // --- Demo Case: Fixed Width (200dp), Auto aspect height ---
        // photoView.setFixedWidth((200 * density).toInt())

        // --- Demo Case: Fixed Size (200x200) ---
        // photoView.setFixedSize((200 * density).toInt(), (200 * density).toInt())

        // --- Demo for Requirement 2a: Width 70% of screen, Height 100dp ---
        // photoView.setWidthPercentageAndHeight(0.7f, (100 * density).toInt())

        // --- Demo Case: Height 50% screen, Width Fixed 100dp ---
        // photoView.setHeightPercentageAndWidth(0.5f, (100 * density).toInt())

        // --- Demo Case: Width 80% screen, Auto Height (by image) ---
        // photoView.setWidthPercent(0.8f)

        // --- Demo Case: Height 50% screen, Auto Width (by image) ---
        // photoView.setHeightPercent(0.5f)

        // --- Demo Case: Width 50% screen, Height 50% screen ---
        // photoView.setPercentDimensions(0.5f, 0.5f)

        // --- Demo for Requirement 2b: Width 80% screen, Height 16:9 Aspect Ratio ---
        // Uncomment below to test:
        // photoView.setWidthPercentageAndAspectRatio(0.8f, 9f / 16f)
        
        // Default: just let xml handle it (match_parent)
        // --- Demo: Setting Image Programmatically ---
        // Verify that setting image from code works and correctly resets/fits the image
         photoView.setImageResource(R.drawable.image_prev)
    }
}