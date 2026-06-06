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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isaguliyev.sudoku_solver_ai.ui.components.BoardEditDialog
import com.isaguliyev.sudoku_solver_ai.ui.components.ExtractionWarningBanner
import com.isaguliyev.sudoku_solver_ai.ui.components.FlippableInputCard
import com.isaguliyev.sudoku_solver_ai.ui.components.SudokuGrid
import com.isaguliyev.sudoku_solver_ai.ui.theme.Sudoku_solver_aiTheme
import com.isaguliyev.sudoku_solver_ai.viewmodel.SudokuState
import com.isaguliyev.sudoku_solver_ai.viewmodel.SudokuViewModel
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
            onConfirm = { edited ->
                viewModel.applyEditedBoard(edited)
                showEditDialog = false
            }
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

                Spacer(modifier = Modifier.height(16.dp))
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = when {
                    state.isSolved -> "Solved Puzzle"
                    state.hasPreviewBoard -> "Detected Puzzle"
                    else -> "Grid Preview"
                },
                style      = if (state.hasPreviewBoard) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = if (state.hasPreviewBoard) FontWeight.Bold else FontWeight.Normal,
                color      = if (state.hasPreviewBoard) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
            )
            if (state.hasPreviewBoard && !state.isSolved) {
                EditBoardButton(onEdit = onEdit)
            }
        }

        ElevatedCard(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
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

        AnimatedVisibility(
            visible = !state.isSolved && state.hasPreviewBoard && !state.isLoading,
            enter   = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            Button(
                onClick  = onSolve,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier           = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text  = "Solve Puzzle",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AnimatedVisibility(
            visible = state.isSolved,
            enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit    = scaleOut() + fadeOut()
        ) {
            SuccessBanner()
        }
    }
}

@Composable
private fun EditBoardButton(onEdit: () -> Unit) {
    var showHitBox by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (showHitBox) 1.15f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "editHitBoxScale"
    )
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .then(
                if (showHitBox) {
                    Modifier.border(2.dp, colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        showHitBox = true
                        tryAwaitRelease()
                        showHitBox = false
                    },
                    onTap = { onEdit() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Edit,
                contentDescription = "Edit board",
                tint               = colorScheme.onSecondaryContainer,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SuccessBanner() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .background(colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    tint               = colorScheme.onPrimary,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text       = "Puzzle Solved!",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = colorScheme.onPrimaryContainer
                )
                Text(
                    text  = "Solved digits are shown in blue",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
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
