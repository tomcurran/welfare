package org.tomcurran.welfare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.tomcurran.welfare.ui.SettingsScreen
import org.tomcurran.welfare.ui.WeightScreen
import org.tomcurran.welfare.ui.WeightViewModel
import org.tomcurran.welfare.ui.theme.WelfareTheme

@Serializable object WeightRoute
@Serializable object SettingsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WelfareTheme {
                val navController = rememberNavController()
                val viewModel: WeightViewModel = viewModel()

                NavHost(navController = navController, startDestination = WeightRoute) {
                    composable<WeightRoute> {
                        WeightScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate(SettingsRoute) },
                        )
                    }
                    composable<SettingsRoute> {
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
