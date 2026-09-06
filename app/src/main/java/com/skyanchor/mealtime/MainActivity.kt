package com.skyanchor.mealtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skyanchor.mealtime.app.navigation.FoodApp
import com.skyanchor.mealtime.core.ui.FoodTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTheme {
                FoodApp()
            }
        }
    }
}
