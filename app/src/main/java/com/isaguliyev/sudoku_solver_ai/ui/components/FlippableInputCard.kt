package com.isaguliyev.sudoku_solver_ai.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.isaguliyev.sudoku_solver_ai.ui.bubble.BubbleControlContent
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val FLIP_DRAG_THRESHOLD = 0.25f

private fun clampDragRotation(raw: Float, dragStartRotation: Float): Float =
    when {
        dragStartRotation == 0f  -> raw.coerceIn(-180f, 180f)
        dragStartRotation > 0f   -> raw.coerceIn(0f, 360f)
        else                     -> raw.coerceIn(-360f, 0f)
    }

private fun flipTargetRotation(dragStartRotation: Float, cumulativeDrag: Float): Float =
    when {
        dragStartRotation == 0f  -> if (cumulativeDrag > 0f) 180f else -180f
        dragStartRotation > 0f   -> if (cumulativeDrag > 0f) 360f else 0f
        else                     -> if (cumulativeDrag < 0f) -360f else 0f
    }

private fun normalizeSettledRotation(angle: Float): Float =
    when (angle) {
        360f, -360f -> 0f
        else        -> angle
    }

private fun isFrontFace(rotationY: Float): Boolean {
    val r = ((rotationY % 360f) + 360f) % 360f
    return r <= 90f || r >= 270f
}

private fun backFaceCounterRotation(rotationY: Float): Float =
    if (rotationY < 0f || rotationY > 180f) -180f else 180f

@Composable
fun FlippableInputCard(
    bitmap: Bitmap?,
    showClear: Boolean,
    onImageClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var settledRotation by remember { mutableFloatStateOf(0f) }
    var dragStartRotation by remember { mutableFloatStateOf(0f) }
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .pointerInput(cardWidthPx, settledRotation) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartRotation = settledRotation
                        cumulativeDrag = 0f
                    },
                    onDragEnd = {
                        val progress = abs(cumulativeDrag) / cardWidthPx
                        val target = if (progress > FLIP_DRAG_THRESHOLD && cumulativeDrag != 0f) {
                            flipTargetRotation(dragStartRotation, cumulativeDrag)
                        } else {
                            dragStartRotation
                        }
                        scope.launch {
                            rotation.animateTo(target, springSpec)
                            val normalized = normalizeSettledRotation(target)
                            rotation.snapTo(normalized)
                            settledRotation = normalized
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        cumulativeDrag += dragAmount
                        val signedProgress = (cumulativeDrag / cardWidthPx).coerceIn(-1f, 1f)
                        val rawRotation = dragStartRotation + signedProgress * 180f
                        scope.launch {
                            rotation.snapTo(clampDragRotation(rawRotation, dragStartRotation))
                        }
                    }
                )
            }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12f * density.density
            },
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isFrontFace(rotation.value)) {
                ImagePickerFace(
                    bitmap    = bitmap,
                    showClear = showClear,
                    onClear   = onClear,
                    onImageClick = onImageClick
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = backFaceCounterRotation(rotation.value) }
                ) {
                    BubbleControlContent(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ImagePickerFace(
    bitmap: Bitmap?,
    showClear: Boolean,
    onClear: () -> Unit,
    onImageClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = "Selected Sudoku image",
                modifier           = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(onImageClick) {
                        detectTapGestures(onTap = { onImageClick() })
                    },
                contentScale       = ContentScale.Fit
            )
        } else {
            EmptyStateFace(
                icon       = Icons.Default.Add,
                label      = "Tap to select a Sudoku image",
                modifier   = Modifier.fillMaxSize(),
                onClick    = onImageClick,
                bottomHint = { FrontFlipHint() }
            )
        }

        if (showClear) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                FilledIconButton(
                    onClick  = onClear,
                    modifier = Modifier.size(36.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colorScheme.surface.copy(alpha = 0.85f),
                        contentColor   = colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Clear sudoku",
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (bitmap != null) {
            FrontFlipHint(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun FrontFlipHint(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier              = modifier.padding(bottom = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text  = "Swipe for bubble",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint               = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier           = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
        )
    }
}
