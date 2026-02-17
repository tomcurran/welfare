package org.tomcurran.welfare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.tomcurran.welfare.ui.SettingsScreen
import org.tomcurran.welfare.ui.WeightScreen
import org.tomcurran.welfare.ui.WeightViewModel
import org.tomcurran.welfare.ui.theme.WelfareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WelfareTheme {
                val navController = rememberNavController()
                val viewModel: WeightViewModel = viewModel()

                NavHost(navController = navController, startDestination = "weight") {
                    composable("weight") {
                        WeightScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
