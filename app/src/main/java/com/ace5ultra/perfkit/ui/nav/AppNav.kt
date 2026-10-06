package com.ace5ultra.perfkit.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Top-level destinations. A tiny manual back stack powers predictive back:
 * PredictiveBackHandler tracks the gesture progress (back-preview) and pops on
 * commit. Tab taps replace the current destination.
 */
enum class Route(val label: String) {
    Dashboard("Dashboard"),
    Profile("Profile"),
    Tuning("Tuning"),
    Adaptive("Adaptive"),
    Updater("Updater");

    companion object {
        val bottomItems = entries
    }
}

/** Hoisted navigation controller. */
class NavController(start: Route) {
    val backStack = mutableStateListOf(start)
    val current: Route get() = backStack.last()

    fun select(route: Route) {
        if (backStack.last() == route) return
        // Tapping a bottom tab replaces the top of stack, keeping one entry per tab.
        backStack.clear()
        backStack.add(route)
    }

    /** System back (non-predictive fallback): pop one, else let the system handle it. */
    fun pop(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }
}

@Composable
fun rememberNavController(start: Route = Route.Dashboard): NavController =
    remember { NavController(start) }

@Composable
fun AppNavHost(
    nav: NavController,
    content: @Composable (Route) -> Unit,
) {
    var backProgress by remember { mutableFloatStateOf(0f) }

    // Predictive back gesture with live back-preview, following the official
    // Android predictive-back guidance (enableOnBackInvokedCallback in manifest).
    PredictiveBackHandler(enabled = nav.backStack.size > 1) { progressFlow ->
        progressFlow.collect { event ->
            backProgress = event.progress
        }
        if (nav.backStack.size > 1) nav.backStack.removeAt(nav.backStack.size - 1)
        backProgress = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Back-preview transform: content slides + shrinks under the gesture.
            .graphicsLayer {
                val p = backProgress
                translationX = p * 90.dp.toPx()
                val s = 1f - p * 0.06f
                scaleX = s
                scaleY = s
            },
    ) {
        AnimatedContent(
            targetState = nav.current,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 6 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 8 })
            },
            label = "nav",
        ) { route ->
            content(route)
        }
    }
}
