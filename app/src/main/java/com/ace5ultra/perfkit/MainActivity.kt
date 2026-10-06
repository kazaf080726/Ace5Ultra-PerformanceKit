package com.ace5ultra.perfkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ace5ultra.perfkit.ui.PerfViewModel
import com.ace5ultra.perfkit.ui.components.GlassBottomBar
import com.ace5ultra.perfkit.ui.glass.GlassBackdrop
import com.ace5ultra.perfkit.ui.nav.AppNavHost
import com.ace5ultra.perfkit.ui.nav.Route
import com.ace5ultra.perfkit.ui.nav.rememberNavController
import com.ace5ultra.perfkit.ui.screens.AdaptiveScreen
import com.ace5ultra.perfkit.ui.screens.DashboardScreen
import com.ace5ultra.perfkit.ui.screens.ProfileScreen
import com.ace5ultra.perfkit.ui.screens.SetupScreen
import com.ace5ultra.perfkit.ui.screens.TuningScreen
import com.ace5ultra.perfkit.ui.screens.UpdaterScreen
import com.ace5ultra.perfkit.ui.theme.PerfKitTheme

class MainActivity : ComponentActivity() {

    private val vm: PerfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerfKitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: PerfViewModel) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rootState by vm.root.collectAsState()
    val nav = rememberNavController()

    LaunchedEffect(rootState) {
        if (rootState is PerfViewModel.RootUi.Ready) vm.startPolling() else vm.stopPolling()
    }

    Box(Modifier.fillMaxSize()) {
        // Liquid-glass ambient backdrop: real GPU blur on API 31+, soft gradient fallback below.
        GlassBackdrop(dark = dark, modifier = Modifier.fillMaxSize())

        AppNavHost(nav = nav) { route ->
            when {
                rootState !is PerfViewModel.RootUi.Ready -> SetupScreen(vm, rootState)
                else -> when (route) {
                    Route.Dashboard -> DashboardScreen(vm)
                    Route.Profile -> ProfileScreen(vm)
                    Route.Tuning -> TuningScreen(vm)
                    Route.Adaptive -> AdaptiveScreen(vm)
                    Route.Updater -> UpdaterScreen(vm)
                }
            }
        }

        // Floating blurred bottom nav. Hidden while setup is required.
        if (rootState is PerfViewModel.RootUi.Ready) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            ) {
                GlassBottomBar(current = nav.current, onSelect = { nav.select(it) })
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Double =
    (0.299 * red + 0.587 * green + 0.114 * blue).toDouble()
