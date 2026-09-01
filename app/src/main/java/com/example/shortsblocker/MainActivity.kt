package com.example.shortsblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.shortsblocker.ui.ShortsBlockerScreen
import com.example.shortsblocker.ui.theme.BlockerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShortsBlockerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockerTheme {
                ShortsBlockerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
