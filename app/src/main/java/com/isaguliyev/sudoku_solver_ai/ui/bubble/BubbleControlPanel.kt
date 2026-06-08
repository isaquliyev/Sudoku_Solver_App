package com.isaguliyev.sudoku_solver_ai.ui.bubble

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.FloatingBubbleService
import com.isaguliyev.sudoku_solver_ai.ui.components.CardClearButton
import com.isaguliyev.sudoku_solver_ai.ui.components.CardImagePreview
import com.isaguliyev.sudoku_solver_ai.ui.components.EmptyStateFace
import com.isaguliyev.sudoku_solver_ai.ui.components.FlipGestureHint

@Composable
fun BubbleControlPanel() {
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        BubbleControlContent(
            modifier      = Modifier.fillMaxSize(),
            showFlipHint  = false
        )
    }
}

@Composable
fun BubbleControlContent(
    modifier: Modifier = Modifier,
    showFlipHint: Boolean = true,
    hintVisible: Boolean = true,
    showPageIndicator: Boolean = true,
    isBackFace: Boolean = true,
    showClear: Boolean = false,
    onClear: () -> Unit = {},
    scannedBitmap: Bitmap? = null
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val isBubbleRunning by FloatingBubbleService.isRunningFlow.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val overlayTheme = remember(colorScheme) {
        BubbleOverlayTheme(
            primary = colorScheme.primary.toArgb(),
            onPrimary = colorScheme.onPrimary.toArgb(),
            surface = colorScheme.surface.toArgb(),
            onSurface = colorScheme.onSurface.toArgb(),
            outline = colorScheme.outline.toArgb(),
            error = colorScheme.error.toArgb()
        )
    }

    fun refreshPermissions() {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun startBubbleServiceWithProjection(resultCode: Int, resultData: Intent) {
        FloatingBubbleService.markRunning()
        val intent = Intent(context, FloatingBubbleService::class.java).apply {
            putExtra(FloatingBubbleService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingBubbleService.EXTRA_RESULT_DATA, resultData)
            overlayTheme.putExtras(this)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopBubbleService() {
        context.startService(
            Intent(context, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_STOP
            }
        )
    }

    val allGranted = hasOverlayPermission && hasNotifPermission

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultData = result.data
        if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            startBubbleServiceWithProjection(result.resultCode, resultData)
        } else {
            Toast.makeText(
                context,
                "Screen capture is required for scanning",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestProjectionAndStartBubble() {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    DisposableEffect(lifecycleOwner, showPermissionDialog) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                if (showPermissionDialog) {
                    val granted = Settings.canDrawOverlays(context) &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED)
                    if (granted && !FloatingBubbleService.isRunningFlow.value) {
                        showPermissionDialog = false
                        requestProjectionAndStartBubble()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    fun onBubbleTap() {
        if (isBubbleRunning) {
            stopBubbleService()
        } else if (allGranted) {
            requestProjectionAndStartBubble()
        } else {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        BubblePermissionDialog(
            hasOverlayPermission = hasOverlayPermission,
            hasNotifPermission   = hasNotifPermission,
            onDismiss            = { showPermissionDialog = false },
            onGrantOverlay       = {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onGrantNotifications = {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }

    Box(modifier = modifier) {
        if (scannedBitmap != null) {
            CardImagePreview(
                bitmap             = scannedBitmap,
                contentDescription = "Scanned Sudoku image",
                modifier           = Modifier.fillMaxSize()
            )
            if (showFlipHint) {
                FlipGestureHint(
                    isBackFace        = isBackFace,
                    visible           = hintVisible,
                    showPageIndicator = showPageIndicator,
                    modifier          = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            EmptyStateFace(
                icon     = Icons.Default.PlayArrow,
                label    = if (isBubbleRunning) {
                    "Tap to stop floating bubble"
                } else {
                    "Tap to start floating bubble"
                },
                modifier = Modifier.fillMaxSize(),
                isActive = isBubbleRunning,
                onClick  = { onBubbleTap() },
                bottomHint = {
                    if (showFlipHint) {
                        FlipGestureHint(
                            isBackFace        = isBackFace,
                            visible           = hintVisible,
                            showPageIndicator = showPageIndicator
                        )
                    }
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
    }
}

private const val PERMISSION_DIALOG_FADE_MS = 200

@Composable
private fun BubblePermissionDialog(
    hasOverlayPermission: Boolean,
    hasNotifPermission: Boolean,
    onDismiss: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit
) {
    val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission
    val message = buildString {
        append("Allow access to float the scan bubble over other apps")
        if (needsNotif) append(" and send notifications")
        append(". Screen capture will be requested when you start the bubble.")
    }

    var animatedVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animatedVisible = true
    }

    fun requestDismiss() {
        if (isClosing) return
        isClosing = true
        animatedVisible = false
    }

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        delay(PERMISSION_DIALOG_FADE_MS.toLong())
        onDismiss()
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestDismiss() }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = animatedVisible,
                enter = fadeIn(tween(PERMISSION_DIALOG_FADE_MS)),
                exit = fadeOut(tween(PERMISSION_DIALOG_FADE_MS)),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            CardClearButton(
                                onClick = { requestDismiss() },
                                contentDescription = "Close",
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }

                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        if (!hasOverlayPermission) {
                            FilledTonalButton(
                                onClick = onGrantOverlay,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Display over apps")
                            }
                        }

                        if (needsNotif) {
                            FilledTonalButton(
                                onClick = onGrantNotifications,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Notifications")
                            }
                        }
                    }
                }
            }
        }
    }
}

