package com.isaguliyev.sudoku_solver_ai

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isaguliyev.sudoku_solver_ai.ui.components.EmptySudokuGrid
import com.isaguliyev.sudoku_solver_ai.ui.components.SudokuGrid
import com.isaguliyev.sudoku_solver_ai.ui.theme.Sudoku_solver_aiTheme
import com.isaguliyev.sudoku_solver_ai.ui.bubble.BubbleControlPanel
import com.isaguliyev.sudoku_solver_ai.viewmodel.SavedScanFolderUi
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

@OptIn(ExperimentalMaterial3Api::class)
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
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    var showCellImagesViewer by remember { mutableStateOf(false) }
    var showSavedFilesViewer by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedFilesViewer) {
        if (showSavedFilesViewer) viewModel.loadSavedCellHistory(context)
    }

    LaunchedEffect(state.shareCellFileEvent) {
        val event = state.shareCellFileEvent ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            setDataAndType(event.uri, "*/*")
            putExtra(Intent.EXTRA_STREAM, event.uri)
            putExtra(Intent.EXTRA_SUBJECT, event.fileName)
            putExtra(Intent.EXTRA_TITLE, event.fileName)
            clipData = ClipData.newUri(context.contentResolver, event.fileName, event.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        viewModel.consumeShareCellFileEvent()
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
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Sudoku Solver AI",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor       = MaterialTheme.colorScheme.surface,
                    titleContentColor    = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    if (state.imageUri != null || state.imageBitmap != null) {
                        IconButton(onClick = { viewModel.clearState() }) {
                            Icon(
                                imageVector        = Icons.Default.Clear,
                                contentDescription = "Clear image"
                            )
                        }
                    }
                }
            )
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Image card (4:3) ────────────────────────────────────────
                ImageCard(
                    bitmap = state.imageBitmap,
                    onClick = { imagePickerLauncher.launch("image/*") }
                )

                // ── Action row: pick / change image + overflow menu ─────────
                ActionRow(
                    hasImage      = state.imageUri != null || state.imageBitmap != null,
                    onPickImage   = { imagePickerLauncher.launch("image/*") },
                    onViewCells   = { showCellImagesViewer = true },
                    onViewFiles   = { showSavedFilesViewer = true }
                )

                // ── Sudoku puzzle section ───────────────────────────────────
                AnimatedVisibility(
                    visible = state.extractedDigits.isNotEmpty(),
                    enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit    = fadeOut()
                ) {
                    PuzzleSection(
                        state   = state,
                        onSolve = { viewModel.solveSudoku() }
                    )
                }

                // ── Empty-state preview (no image selected) ─────────────────
                if (state.extractedDigits.isEmpty() && state.imageBitmap == null) {
                    EmptyPreviewSection()
                }

                // ── Floating bubble controls ────────────────────────────────
                BubbleControlPanel()

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Loading overlay ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.isLoading,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                LoadingOverlay()
            }

            // ── Secondary screens ───────────────────────────────────────────
            if (showCellImagesViewer) {
                CellImagesViewer(
                    cellBitmaps = state.modelInputCellBitmaps,
                    onDismiss   = { showCellImagesViewer = false }
                )
            }
            if (showSavedFilesViewer) {
                SavedCellFilesViewer(
                    folders      = state.savedScanFolders,
                    onDismiss    = { showSavedFilesViewer = false },
                    onRenameFile = { folder, cur, new -> viewModel.renameSavedCellFile(context, folder, cur, new) },
                    onShareFile  = { folder, file -> viewModel.requestShareSavedCellFile(context, folder, file) },
                    onShareArchive = { folder -> viewModel.requestShareSavedFolderArchive(context, folder) }
                )
            }
        }
    }
}

// ── Image card ────────────────────────────────────────────────────────────────

@Composable
private fun ImageCard(
    bitmap: android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = "Selected Sudoku image",
                    modifier           = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale       = ContentScale.Fit
                )
            } else {
                // Empty state inside the card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier            = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier          = Modifier
                            .size(80.dp)
                            .background(
                                brush  = Brush.radialGradient(
                                    listOf(
                                        colorScheme.primary.copy(alpha = 0.15f),
                                        colorScheme.primary.copy(alpha = 0.05f)
                                    )
                                ),
                                shape  = CircleShape
                            ),
                        contentAlignment  = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Add,
                            contentDescription = null,
                            modifier           = Modifier.size(40.dp),
                            tint               = colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text      = "Tap to select a Sudoku image",
                        style     = MaterialTheme.typography.titleSmall,
                        color     = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text      = "Or use the floating bubble to scan from any app",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Action row ────────────────────────────────────────────────────────────────

@Composable
private fun ActionRow(
    hasImage: Boolean,
    onPickImage: () -> Unit,
    onViewCells: () -> Unit,
    onViewFiles: () -> Unit
) {
    Row(
        modifier             = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment    = Alignment.CenterVertically
    ) {
        Button(
            onClick  = onPickImage,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape    = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = null,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = if (hasImage) "Change Image" else "Add Sudoku Image",
                style = MaterialTheme.typography.labelLarge
            )
        }

        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            FilledTonalIconButton(
                onClick  = { menuExpanded = true },
                modifier = Modifier.size(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded          = menuExpanded,
                onDismissRequest  = { menuExpanded = false },
                shape             = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text    = { Text("View cell images passed to model") },
                    onClick = { menuExpanded = false; onViewCells() }
                )
                DropdownMenuItem(
                    text    = { Text("View saved cell image files") },
                    onClick = { menuExpanded = false; onViewFiles() }
                )
            }
        }
    }
}

// ── Puzzle section ────────────────────────────────────────────────────────────

@Composable
private fun PuzzleSection(
    state: SudokuState,
    onSolve: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text       = if (state.isSolved) "Solved Puzzle" else "Detected Puzzle",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )

        ElevatedCard(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                val displayDigits = if (state.isSolved) {
                    state.solvedDigits
                } else {
                    state.extractedDigits.map { it ?: 0 }
                }
                SudokuGrid(
                    digits          = displayDigits,
                    originalIndices = state.originalDigits,
                    modifier        = Modifier.fillMaxWidth()
                )
            }
        }

        // Solve button — slides in when puzzle is detected but not yet solved
        AnimatedVisibility(
            visible = !state.isSolved && state.extractedDigits.isNotEmpty(),
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

        // Success banner — scales in with spring
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

// ── Empty preview ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyPreviewSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text  = "Grid Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        EmptySudokuGrid(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
    }
}

// ── Loading overlay ───────────────────────────────────────────────────────────

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

// ── Cell images viewer ────────────────────────────────────────────────────────

@Composable
private fun CellImagesViewer(
    cellBitmaps: List<android.graphics.Bitmap>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTopBar(
                title   = "Cell images passed to model (9×9)",
                onClose = onDismiss
            )
            if (cellBitmaps.isEmpty()) {
                EmptyContent("No cell images yet.\nAdd a Sudoku image and extract first.")
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(9),
                    modifier              = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement   = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(cellBitmaps) { index, bitmap ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            Image(
                                bitmap             = bitmap.asImageBitmap(),
                                contentDescription = "Cell ${index / 9},${index % 9}",
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale       = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Saved files viewer ────────────────────────────────────────────────────────

@Composable
private fun SavedCellFilesViewer(
    folders: List<SavedScanFolderUi>,
    onDismiss: () -> Unit,
    onRenameFile: (folderName: String, currentName: String, newName: String) -> Unit,
    onShareFile: (folderName: String, fileName: String) -> Unit,
    onShareArchive: (folderName: String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameValue  by remember { mutableStateOf("") }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }

    val selectedFolder = folders.firstOrNull { it.folderName == selectedFolderName }
    val inDetail       = selectedFolder != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            SecondaryTopBar(
                title        = if (inDetail) selectedFolder!!.folderName else "Saved scan folders",
                showBack     = inDetail,
                onBack       = { selectedFolderName = null },
                trailingIcon = if (inDetail) {
                    {
                        IconButton(onClick = { onShareArchive(selectedFolder!!.folderName) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share archive")
                        }
                    }
                } else null,
                onClose = { if (inDetail) selectedFolderName = null else onDismiss() }
            )

            when {
                folders.isEmpty() -> EmptyContent("No saved scans found yet.")
                !inDetail -> {
                    LazyColumn(
                        modifier            = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { it.folderName }) { folder ->
                            ElevatedCard(
                                modifier  = Modifier.fillMaxWidth(),
                                shape     = RoundedCornerShape(14.dp),
                                onClick   = { selectedFolderName = folder.folderName },
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier            = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment   = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier            = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text       = folder.folderName,
                                            style      = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text  = folder.displayDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    AssistChip(
                                        onClick = {},
                                        label   = { Text("${folder.files.size} files", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    val folder = selectedFolder!!
                    if (folder.files.isEmpty()) {
                        EmptyContent("No image files in this folder.")
                    } else {
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            modifier              = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement   = Arrangement.spacedBy(10.dp)
                        ) {
                            items(folder.files.size) { i ->
                                val file = folder.files[i]
                                ElevatedCard(
                                    modifier  = Modifier.fillMaxWidth(),
                                    shape     = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(
                                        modifier            = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text     = file.fileName,
                                            style    = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            color    = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier              = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            IconButton(onClick = {
                                                renameTarget = folder.folderName to file.fileName
                                                renameValue  = file.fileName
                                            }) {
                                                Icon(
                                                    imageVector        = Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier           = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = { onShareFile(folder.folderName, file.fileName) }) {
                                                Icon(
                                                    imageVector        = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint               = MaterialTheme.colorScheme.primary,
                                                    modifier           = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape            = RoundedCornerShape(20.dp),
            title            = { Text("Rename file", style = MaterialTheme.typography.titleMedium) },
            text             = {
                OutlinedTextField(
                    value         = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine    = true,
                    label         = { Text("Filename") },
                    shape         = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget?.let { (folder, cur) -> onRenameFile(folder, cur, renameValue) }
                        renameTarget = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun SecondaryTopBar(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    trailingIcon: (@Composable () -> Unit)? = null,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier             = Modifier.weight(1f)
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingIcon?.invoke()
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
