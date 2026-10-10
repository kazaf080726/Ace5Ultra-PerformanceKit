package com.ace5ultra.perfkit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.ace5ultra.perfkit.ui.nav.rememberNavController
import com.ace5ultra.perfkit.ui.screens.AdaptiveScreen
import com.ace5ultra.perfkit.ui.screens.DashboardScreen
import com.ace5ultra.perfkit.ui.screens.ProfileScreen
import com.ace5ultra.perfkit.ui.screens.SetupScreen
import com.ace5ultra.perfkit.ui.screens.TuningScreen
import com.ace5ultra.perfkit.ui.screens.UpdaterScreen
import com.ace5ultra.perfkit.ui.theme.PerfKitTheme

class MainActivity : AppCompatActivity() {

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
    val rootState by vm.root.collectAsState()
    val nav = rememberNavController()

    LaunchedEffect(rootState) {
        if (rootState is PerfViewModel.RootUi.Ready) vm.startPolling() else vm.stopPolling()
    }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(dark = false, modifier = Modifier.fillMaxSize())

        AppNavHost(nav = nav) { route ->
            when {
                rootState !is PerfViewModel.RootUi.Ready -> SetupScreen(vm, rootState)
                else -> when (route) {
                    com.ace5ultra.perfkit.ui.nav.Route.Dashboard -> DashboardScreen(vm)
                    com.ace5ultra.perfkit.ui.nav.Route.Profile -> ProfileScreen(vm)
                    com.ace5ultra.perfkit.ui.nav.Route.Tuning -> TuningScreen(vm)
                    com.ace5ultra.perfkit.ui.nav.Route.Adaptive -> AdaptiveScreen(vm)
                    com.ace5ultra.perfkit.ui.nav.Route.Updater -> UpdaterScreen(vm)
                }
            }
        }

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
