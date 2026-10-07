package com.example.bili2media

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        fitContentBelowSystemBars(findViewById(R.id.main), findViewById(R.id.statusBarBackground))
    }

    private fun fitContentBelowSystemBars(rootView: View, statusBarBackground: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val statusBarColor = ContextCompat.getColor(this, R.color.bili2media_background)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, rootView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val initialLeft = rootView.paddingLeft
        val initialTop = rootView.paddingTop
        val initialRight = rootView.paddingRight
        val initialBottom = rootView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarBackground.setBackgroundColor(statusBarColor)
            val layoutParams = statusBarBackground.layoutParams
            if (layoutParams.height != systemBars.top) {
                layoutParams.height = systemBars.top
                statusBarBackground.layoutParams = layoutParams
            }
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }
}
