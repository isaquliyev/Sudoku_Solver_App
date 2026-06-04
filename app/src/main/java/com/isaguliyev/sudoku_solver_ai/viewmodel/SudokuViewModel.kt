package com.isaguliyev.sudoku_solver_ai.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaguliyev.sudoku_solver_ai.extractor.SudokuBoxExtractor
import com.isaguliyev.sudoku_solver_ai.solver.SudokuSolver
import com.isaguliyev.sudoku_solver_ai.storage.SudokuExtractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class SavedCellFileUi(
    val fileName: String
)

data class SavedScanFolderUi(
    val folderName: String,
    val displayDate: String,
    val files: List<SavedCellFileUi>
)

data class ShareCellFileEvent(
    val scanFolderName: String,
    val fileName: String,
    val uri: Uri
)

data class SudokuState(
    val imageUri: Uri? = null,
    val imageBitmap: Bitmap? = null,
    val extractedDigits: List<Int?> = emptyList(),
    val solvedDigits: List<Int> = emptyList(),
    val originalDigits: Set<Int> = emptySet(), // Indices of originally detected digits
    val modelInputCellBitmaps: List<Bitmap> = emptyList(), // 81 preprocessed cell images passed to model (28×28)
    val savedScanFolders: List<SavedScanFolderUi> = emptyList(),
    val shareCellFileEvent: ShareCellFileEvent? = null,
    val isLoading: Boolean = false,
    val isSolved: Boolean = false,
    val errorMessage: String? = null
)

class SudokuViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(SudokuState())
    val state: StateFlow<SudokuState> = _state.asStateFlow()
    
    private var extractor: SudokuBoxExtractor? = null
    
    fun initializeExtractor(context: Context) {
        if (extractor == null) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                try {
                    // Load model on main thread to avoid SIGBUS (BUS_ADRALN) from
                    // unaligned access in PyTorch native code when loading on background threads.
                    extractor = SudokuBoxExtractor(context)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to load model: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun onImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                isSolved = false,
                solvedDigits = emptyList()
            )
            
            try {
                // Load bitmap from URI
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        android.graphics.BitmapFactory.decodeStream(inputStream)
                    }
                }
                
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }
                
                _state.value = _state.value.copy(
                    imageUri = uri,
                    imageBitmap = bitmap
                )
                
                // Extract digits
                val imageBytes = withContext(Dispatchers.IO) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }
                
                val (digits, modelBitmaps, originalCellBitmaps) = withContext(Dispatchers.IO) {
                    val bitmaps = mutableListOf<Bitmap>()
                    val originalBitmaps = mutableListOf<Bitmap>()
                    val result = extractor?.extract(imageBytes, bitmaps, originalBitmaps) ?: emptyList()
                    Triple(result, bitmaps, originalBitmaps)
                }
                
                if (extractor == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Model is still loading. Please try again in a moment."
                    )
                    return@launch
                }
                
                if (digits.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "No Sudoku board detected in image"
                    )
                    return@launch
                }
                
                // Track which cells had original digits
                val originalIndices = digits.mapIndexedNotNull { index, value ->
                    if (value != null) index else null
                }.toSet()
                
                // Save to external storage immediately after extraction (non-blocking; failures are logged only)
                withContext(Dispatchers.IO) {
                    val repo = SudokuExtractionRepository(context.applicationContext)
                    repo.saveExtraction(digits, originalCellBitmaps)
                        .onFailure { e -> android.util.Log.w("SudokuViewModel", "Could not save extraction: ${e.message}") }
                }
                
                _state.value = _state.value.copy(
                    extractedDigits = digits,
                    originalDigits = originalIndices,
                    modelInputCellBitmaps = modelBitmaps,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error processing image: ${e.message}"
                )
            }
        }
    }
    
    fun solveSudoku() {
        val digits = _state.value.extractedDigits
        if (digits.isEmpty()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val solved = withContext(Dispatchers.Default) {
                    val board = SudokuSolver.listToBoard(digits)
                    val success = SudokuSolver.solve(board)
                    if (success) SudokuSolver.boardToList(board) else null
                }
                
                if (solved != null) {
                    _state.value = _state.value.copy(
                        solvedDigits = solved,
                        isSolved = true,
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "No solution found for this puzzle"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error solving puzzle: ${e.message}"
                )
            }
        }
    }
    
    fun onBitmapSelected(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                isSolved = false,
                solvedDigits = emptyList(),
                imageUri = null,
                imageBitmap = bitmap
            )

            try {
                val imageBytes = withContext(Dispatchers.IO) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }

                val (digits, modelBitmaps, originalCellBitmaps) = withContext(Dispatchers.IO) {
                    val bitmaps = mutableListOf<Bitmap>()
                    val originalBitmaps = mutableListOf<Bitmap>()
                    val result = extractor?.extract(imageBytes, bitmaps, originalBitmaps) ?: emptyList()
                    Triple(result, bitmaps, originalBitmaps)
                }

                if (extractor == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Model is still loading. Please try again in a moment."
                    )
                    return@launch
                }

                if (digits.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "No Sudoku board detected in image"
                    )
                    return@launch
                }

                val originalIndices = digits.mapIndexedNotNull { index, value ->
                    if (value != null) index else null
                }.toSet()

                withContext(Dispatchers.IO) {
                    val repo = SudokuExtractionRepository(context.applicationContext)
                    repo.saveExtraction(digits, originalCellBitmaps)
                        .onFailure { e ->
                            android.util.Log.w("SudokuViewModel", "Could not save extraction: ${e.message}")
                        }
                }

                _state.value = _state.value.copy(
                    extractedDigits = digits,
                    originalDigits = originalIndices,
                    modelInputCellBitmaps = modelBitmaps,
                    isLoading = false
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error processing image: ${e.message}"
                )
            }
        }
    }

    fun clearState() {
        _state.value = SudokuState()
    }
    
    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun loadSavedCellHistory(context: Context) {
        viewModelScope.launch {
            val folders = withContext(Dispatchers.IO) {
                val repo = SudokuExtractionRepository(context.applicationContext)
                repo.listScanFoldersNewestFirst().map { scanFolder ->
                    val files = repo.listCellFiles(scanFolder.folderName).map { file ->
                        SavedCellFileUi(fileName = file.fileName)
                    }
                    SavedScanFolderUi(
                        folderName = scanFolder.folderName,
                        displayDate = scanFolder.displayDate,
                        files = files
                    )
                }
            }
            _state.value = _state.value.copy(savedScanFolders = folders)
        }
    }

    fun renameSavedCellFile(
        context: Context,
        scanFolderName: String,
        currentFileName: String,
        requestedFileName: String
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val repo = SudokuExtractionRepository(context.applicationContext)
                repo.renameCellFile(scanFolderName, currentFileName, requestedFileName)
            }

            result.onSuccess {
                loadSavedCellHistory(context)
            }.onFailure { error ->
                _state.value = _state.value.copy(errorMessage = "Rename failed: ${error.message}")
            }
        }
    }

    fun requestShareSavedCellFile(context: Context, scanFolderName: String, fileName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val repo = SudokuExtractionRepository(context.applicationContext)
                repo.getShareUri(scanFolderName, fileName)
            }
            result.onSuccess { uri ->
                _state.value = _state.value.copy(
                    shareCellFileEvent = ShareCellFileEvent(
                        scanFolderName = scanFolderName,
                        fileName = fileName,
                        uri = uri
                    )
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(errorMessage = "Share failed: ${error.message}")
            }
        }
    }

    fun requestShareSavedFolderArchive(context: Context, scanFolderName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val repo = SudokuExtractionRepository(context.applicationContext)
                repo.createFolderArchiveAndGetShareUri(scanFolderName)
            }
            result.onSuccess { (uri, archiveName) ->
                _state.value = _state.value.copy(
                    shareCellFileEvent = ShareCellFileEvent(
                        scanFolderName = scanFolderName,
                        fileName = archiveName,
                        uri = uri
                    )
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(errorMessage = "Archive share failed: ${error.message}")
            }
        }
    }

    fun consumeShareCellFileEvent() {
        _state.value = _state.value.copy(shareCellFileEvent = null)
    }
}
