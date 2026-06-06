package com.isaguliyev.sudoku_solver_ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isaguliyev.sudoku_solver_ai.ui.components.BoardEditDialog
import com.isaguliyev.sudoku_solver_ai.ui.components.ExtractionWarningBanner
import com.isaguliyev.sudoku_solver_ai.ui.components.FlippableInputCard
import com.isaguliyev.sudoku_solver_ai.ui.components.SudokuGrid
import com.isaguliyev.sudoku_solver_ai.ui.theme.Sudoku_solver_aiTheme
import com.isaguliyev.sudoku_solver_ai.viewmodel.SudokuState
import com.isaguliyev.sudoku_solver_ai.viewmodel.SudokuViewModel
import com.isaguliyev.sudoku_solver_ai.viewmodel.boardHasClues
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_SCREENSHOT = "com.isaguliyev.sudoku_solver_ai.ACTION_SCREENSHOT"
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"

        init {
            if (OpenCVLoader.initLocal()) {
                Log.i(TAG, "OpenCV loaded successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed")
            }
        }
    }

    private val sudokuViewModel: SudokuViewModel by lazy {
        ViewModelProvider(this)[SudokuViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sudokuViewModel.initializeExtractor(this)
        handleScreenshotIntent(intent)
        setContent {
            Sudoku_solver_aiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SudokuSolverScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScreenshotIntent(intent)
    }

    private fun handleScreenshotIntent(intent: Intent?) {
        if (intent?.action == ACTION_SCREENSHOT) {
            val path = intent.getStringExtra(EXTRA_SCREENSHOT_PATH) ?: return
            val bitmap = android.graphics.BitmapFactory.decodeFile(path)
            if (bitmap != null) {
                sudokuViewModel.onBitmapSelected(this, bitmap)
            }
        }
    }
}

@Composable
fun SudokuSolverScreen(
    viewModel: SudokuViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.initializeExtractor(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onImageSelected(context, it) } }

    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (showEditDialog) {
        BoardEditDialog(
            initialDigits = state.extractedDigits,
            onDismiss = { showEditDialog = false },
            onConfirm = { edited -> viewModel.applyEditedBoard(edited) }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape        = RoundedCornerShape(50),
                    modifier     = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                FlippableInputCard(
                    bitmap       = state.imageBitmap,
                    showClear    = state.hasPreviewBoard || state.imageBitmap != null,
                    onImageClick = { imagePickerLauncher.launch("image/*") },
                    onClear      = { viewModel.clearState() }
                )

                GridPreviewSection(
                    state   = state,
                    onSolve = { viewModel.solveSudoku() },
                    onEdit  = { showEditDialog = true }
                )
            }

            AnimatedVisibility(
                visible = state.isLoading,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                LoadingOverlay()
            }

            ExtractionWarningBanner(
                visible   = state.showExtractionWarning,
                onDismiss = { viewModel.dismissExtractionWarning() },
                modifier  = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun GridPreviewSection(
    state: SudokuState,
    onSolve: () -> Unit,
    onEdit: () -> Unit
) {
    val canSolve = state.hasPreviewBoard &&
        !state.isSolved &&
        !state.isLoading &&
        boardHasClues(state.extractedDigits)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        ElevatedCard(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                val displayDigits = when {
                    state.isSolved -> state.solvedDigits
                    state.hasPreviewBoard -> state.extractedDigits.map { it ?: 0 }
                    else -> List(81) { 0 }
                }
                SudokuGrid(
                    digits          = displayDigits,
                    originalIndices = state.originalDigits,
                    isEmptyPreview  = !state.hasPreviewBoard,
                    modifier        = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier              = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GridActionBadge(
                icon                = Icons.Default.Edit,
                contentDescription  = "Edit board",
                onClick             = onEdit,
                enabled             = !state.isLoading
            )
            SolveActionBadge(
                isSolved  = state.isSolved,
                canSolve  = canSolve,
                onSolve   = onSolve
            )
        }
    }
}

@Composable
private fun GridActionBadge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val highlightAlpha by animateFloatAsState(
        targetValue   = if (isPressed && enabled) 0.14f else 0f,
        animationSpec = tween(durationMillis = 120),
        label         = "badgePressAlpha"
    )
    val highlightScale by animateFloatAsState(
        targetValue   = if (isPressed && enabled) 1f else 0.88f,
        animationSpec = tween(durationMillis = 120),
        label         = "badgePressScale"
    )
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pointerInput(enabled, onClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        shape            = CircleShape,
        color            = containerColor,
        shadowElevation  = 4.dp,
        tonalElevation   = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(highlightScale)
                    .background(colorScheme.onSurface.copy(alpha = highlightAlpha), CircleShape)
            )
            Icon(
                imageVector        = icon,
                contentDescription = contentDescription,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SolveActionBadge(
    isSolved: Boolean,
    canSolve: Boolean,
    onSolve: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var popScale by remember { mutableStateOf(false) }

    LaunchedEffect(isSolved) {
        if (isSolved) {
            popScale = true
            delay(300)
            popScale = false
        }
    }

    val containerColor by animateColorAsState(
        targetValue   = if (isSolved) colorScheme.primaryContainer else colorScheme.surface,
        animationSpec = tween(durationMillis = 300),
        label         = "solveBadgeContainer"
    )
    val iconTint by animateColorAsState(
        targetValue   = if (isSolved) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300),
        label         = "solveBadgeIcon"
    )
    val successScale by animateFloatAsState(
        targetValue   = if (popScale) 1.08f else 1f,
        animationSpec = tween(durationMillis = 300),
        label         = "solveSuccessScale"
    )

    AnimatedContent(
        targetState = isSolved,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "solveBadgeContent"
    ) { solved ->
        if (solved) {
            GridActionBadge(
                icon               = Icons.Default.Check,
                contentDescription = "Puzzle solved",
                onClick            = {},
                enabled            = false,
                containerColor     = containerColor,
                iconTint           = iconTint,
                modifier           = Modifier.scale(successScale)
            )
        } else {
            GridActionBadge(
                icon               = Icons.Default.PlayArrow,
                contentDescription = "Solve puzzle",
                onClick            = onSolve,
                enabled            = canSolve,
                containerColor     = containerColor,
                iconTint           = iconTint,
                modifier           = Modifier.scale(successScale)
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape         = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier      = Modifier.width(220.dp)
        ) {
            Column(
                modifier            = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "S",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text      = "Detecting digits…",
                    style     = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    modifier        = Modifier.fillMaxWidth(),
                    trackColor      = MaterialTheme.colorScheme.surfaceVariant,
                    color           = MaterialTheme.colorScheme.primary,
                    strokeCap       = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
