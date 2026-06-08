package com.isaguliyev.sudoku_solver_ai.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.isaguliyev.sudoku_solver_ai.viewmodel.ImageSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val FLIP_DRAG_THRESHOLD = 0.25f
private const val FLIP_SETTLE_MAX_MS = 400
private const val FLIP_SETTLE_MIN_MS = 220

private fun settleDurationMs(current: Float, target: Float): Int {
    val fraction = (abs(target - current) / 180f).coerceIn(0f, 1f)
    return (FLIP_SETTLE_MAX_MS * fraction).toInt().coerceIn(FLIP_SETTLE_MIN_MS, FLIP_SETTLE_MAX_MS)
}

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
    imageBitmap: Bitmap?,
    imageSource: ImageSource?,
    showClear: Boolean,
    onImageClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manualBitmap = if (imageSource == ImageSource.Manual) imageBitmap else null
    val scannedBitmap = if (imageSource == ImageSource.Scan) imageBitmap else null
    var settledRotation by remember { mutableFloatStateOf(0f) }
    var dragStartRotation by remember { mutableFloatStateOf(0f) }
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val peekRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val hintVisible = !isDragging && !rotation.isRunning

    LaunchedEffect(imageSource, imageBitmap) {
        if (imageSource == ImageSource.Scan && imageBitmap != null) {
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(
                    durationMillis = settleDurationMs(rotation.value, 180f),
                    easing = FastOutSlowInEasing
                )
            )
            rotation.snapTo(180f)
            settledRotation = 180f
        }
    }

    LaunchedEffect(isDragging, rotation.isRunning) {
        if (isDragging || rotation.isRunning) return@LaunchedEffect
        while (true) {
            delay(1500)
            peekRotation.animateTo(5f, tween(700, easing = FastOutSlowInEasing))
            peekRotation.animateTo(-5f, tween(900, easing = FastOutSlowInEasing))
            peekRotation.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            delay(2500)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .pointerInput(cardWidthPx, settledRotation) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        scope.launch {
                            rotation.stop()
                            peekRotation.stop()
                            peekRotation.snapTo(0f)
                        }
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
                            rotation.stop()
                            val current = rotation.value
                            rotation.animateTo(
                                targetValue = target,
                                animationSpec = tween(
                                    durationMillis = settleDurationMs(current, target),
                                    easing = FastOutSlowInEasing
                                )
                            )
                            val normalized = normalizeSettledRotation(target)
                            rotation.snapTo(normalized)
                            settledRotation = normalized
                            isDragging = false
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
                rotationY = rotation.value + peekRotation.value
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
                    bitmap       = manualBitmap,
                    showClear    = showClear,
                    onClear      = onClear,
                    onImageClick = onImageClick,
                    hintVisible  = hintVisible
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = backFaceCounterRotation(rotation.value) }
                ) {
                    BubbleControlContent(
                        modifier       = Modifier.fillMaxSize(),
                        hintVisible    = hintVisible,
                        isBackFace     = true,
                        showClear      = showClear,
                        onClear        = onClear,
                        scannedBitmap  = scannedBitmap
                    )
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
    onImageClick: () -> Unit,
    hintVisible: Boolean
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            CardImagePreview(
                bitmap             = bitmap,
                contentDescription = "Selected Sudoku image",
                modifier           = Modifier.fillMaxSize(),
                onClick            = onImageClick
            )
        } else {
            EmptyStateFace(
                icon       = Icons.Default.Add,
                label      = "Tap to select a Sudoku image",
                modifier   = Modifier.fillMaxSize(),
                onClick    = onImageClick,
                bottomHint = {
                    FlipGestureHint(
                        isBackFace = false,
                        visible    = hintVisible
                    )
                }
            )
        }

        if (showClear) {
            CardClearButton(
                onClick = onClear,
                contentDescription = "Clear sudoku",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }

        if (bitmap != null) {
            FlipGestureHint(
                isBackFace = false,
                visible    = hintVisible,
                modifier   = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun CardImagePreview(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    contentDescription: String = "Sudoku image",
    onClick: (() -> Unit)? = null
) {
    val imageModifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(20.dp))
        .then(
            if (onClick != null) {
                Modifier.pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                }
            } else {
                Modifier
            }
        )

    Image(
        bitmap             = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier           = modifier.then(imageModifier),
        contentScale       = ContentScale.Fit
    )
}

@Composable
fun CardClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Clear"
) {
    val colorScheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colorScheme.surface.copy(alpha = 0.85f),
            contentColor = colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

