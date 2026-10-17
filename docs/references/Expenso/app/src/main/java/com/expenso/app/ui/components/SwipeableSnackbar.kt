package com.expenso.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = 16.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier.pointerInput(data) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            if (dragAmount.x.absoluteValue > 10f || dragAmount.y.absoluteValue > 10f) {
                                data.dismiss()
                            }
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
        }
    )
}
