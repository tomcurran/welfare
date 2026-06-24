package org.tomcurran.welfare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import org.tomcurran.welfare.BuildConfig
import org.tomcurran.welfare.ui.LogsScreen
import org.tomcurran.welfare.ui.SettingsScreen
import org.tomcurran.welfare.ui.WeightScreen
import org.tomcurran.welfare.ui.theme.WelfareTheme

@Serializable object WeightRoute
@Serializable object SettingsRoute
@Serializable object LogsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WelfareTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = WeightRoute) {
                    composable<WeightRoute> {
                        WeightScreen(
                            viewModel = hiltViewModel(),
                            onNavigateToSettings = { navController.navigate(SettingsRoute) },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = hiltViewModel(),
                            onBack = { navController.popBackStack() },
                            onNavigateToLogs = { navController.navigate(LogsRoute) },
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        composable<LogsRoute> {
                            LogsScreen(
                                viewModel = hiltViewModel(),
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
