package com.example.rifas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.rifas.ui.theme.RifasTheme
import com.example.rifas.ui.RaffleViewModel
import com.example.rifas.ui.RaffleViewModelFactory
import com.example.rifas.ui.RifasApp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RifasTheme {
                val raffleViewModel: RaffleViewModel = viewModel(
                    factory = RaffleViewModelFactory(application)
                )
                RifasApp(viewModel = raffleViewModel)
            }
        }
    }
}
