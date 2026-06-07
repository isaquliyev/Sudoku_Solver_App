package com.isaguliyev.sudoku_solver_ai.ui.bubble

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaguliyev.sudoku_solver_ai.bubble.BubbleOverlayTheme
import com.isaguliyev.sudoku_solver_ai.bubble.FloatingBubbleService
import com.isaguliyev.sudoku_solver_ai.ui.components.CardClearButton
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
    onClear: () -> Unit = {}
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

    fun startBubbleService() {
        FloatingBubbleService.markRunning()
        val intent = Intent(context, FloatingBubbleService::class.java)
        overlayTheme.putExtras(intent)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopBubbleService() {
        context.stopService(
            Intent(context, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_STOP
            }
        )
    }

    val allGranted = hasOverlayPermission && hasNotifPermission

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
                        startBubbleService()
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
            startBubbleService()
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

        if (showClear || isBubbleRunning) {
            CardClearButton(
                onClick = {
                    if (isBubbleRunning) stopBubbleService()
                    if (showClear) onClear()
                },
                contentDescription = "Clear sudoku",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

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
        append("The floating bubble needs permission to draw over other apps")
        if (needsNotif) append(" and post notifications")
        append(" so you can scan Sudoku puzzles from any app.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Permissions required") },
        text             = { Text(message) },
        confirmButton    = {
            Column {
                if (!hasOverlayPermission) {
                    TextButton(onClick = onGrantOverlay) {
                        Text("Grant overlay")
                    }
                }
                if (needsNotif) {
                    TextButton(onClick = onGrantNotifications) {
                        Text("Grant notifications")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

