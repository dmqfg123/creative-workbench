package com.creativeworkbench.writer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.creativeworkbench.writer.ui.AppViewModel
import com.creativeworkbench.writer.ui.CreativeWorkbenchApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CreativeWorkbenchApp(viewModel)
        }
    }
}
