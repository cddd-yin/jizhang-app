package com.expenso.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable

object ExpensoAnimations {
    fun <T> defaultTween(): TweenSpec<T> = tween(
        durationMillis = 500,
        easing = FastOutSlowInEasing
    )

    fun <T> defaultSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Wraps list or list item content. Formerly used for entrance animation, now renders content immediately.
 */
@Composable
fun AnimatedEntranceItem(
    index: Int,
    content: @Composable () -> Unit
) {
    content()
}
