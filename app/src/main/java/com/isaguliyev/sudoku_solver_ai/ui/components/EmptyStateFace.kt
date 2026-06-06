package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyStateFace(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    bottomHint: @Composable () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val circleBrush = if (isActive) {
        Brush.radialGradient(
            listOf(
                colorScheme.primary.copy(alpha = 0.25f),
                colorScheme.primary.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.radialGradient(
            listOf(
                colorScheme.primary.copy(alpha = 0.15f),
                colorScheme.primary.copy(alpha = 0.05f)
            )
        )
    }

    Box(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onClick
                )
            } else {
                Modifier
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.padding(24.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(64.dp)
                    .background(brush = circleBrush, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(32.dp),
                    tint               = colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text      = label,
                style     = MaterialTheme.typography.titleSmall,
                color     = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            bottomHint()
        }
    }
}
