package com.isaguliyev.sudoku_solver_ai.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaguliyev.sudoku_solver_ai.extractor.SudokuBoxExtractor
import com.isaguliyev.sudoku_solver_ai.solver.SudokuSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

fun emptySudokuBoard(): List<Int?> = List(81) { null }

fun boardHasClues(digits: List<Int?>): Boolean = digits.any { it != null }

enum class ImageSource { Manual, Scan }

data class SudokuState(
    val imageUri: Uri? = null,
    val imageBitmap: Bitmap? = null,
    val imageSource: ImageSource? = null,
    val extractedDigits: List<Int?> = emptyList(),
    val solvedDigits: List<Int> = emptyList(),
    val originalDigits: Set<Int> = emptySet(),
    val hasPreviewBoard: Boolean = false,
    val showExtractionWarning: Boolean = false,
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
                showExtractionWarning = false,
                isSolved = false,
                solvedDigits = emptyList(),
                imageSource = ImageSource.Manual
            )

            try {
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

                processBitmap(bitmap, uri)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error processing image: ${e.message}"
                )
            }
        }
    }

    fun onBitmapSelected(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                showExtractionWarning = false,
                isSolved = false,
                solvedDigits = emptyList(),
                imageUri = null,
                imageBitmap = bitmap,
                imageSource = ImageSource.Scan
            )

            processBitmap(bitmap, uri = null)
        }
    }

    private fun applyExtractionFailure() {
        _state.value = _state.value.copy(
            extractedDigits = emptySudokuBoard(),
            originalDigits = emptySet(),
            hasPreviewBoard = true,
            showExtractionWarning = true,
            isLoading = false
        )
    }

    private suspend fun processBitmap(bitmap: Bitmap, uri: Uri?) {
        _state.value = _state.value.copy(
            imageUri = uri,
            imageBitmap = bitmap
        )

        if (extractor == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = "Model is still loading. Please try again in a moment."
            )
            return
        }

        val imageBytes = withContext(Dispatchers.IO) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

        val digits = try {
            withContext(Dispatchers.IO) {
                extractor?.extract(imageBytes) ?: emptyList()
            }
        } catch (e: Exception) {
            applyExtractionFailure()
            return
        }

        if (digits.isEmpty()) {
            applyExtractionFailure()
            return
        }

        if (!boardHasClues(digits)) {
            _state.value = _state.value.copy(
                extractedDigits = digits,
                originalDigits = emptySet(),
                hasPreviewBoard = true,
                showExtractionWarning = true,
                isLoading = false
            )
            return
        }

        val originalIndices = digits.mapIndexedNotNull { index, value ->
            if (value != null) index else null
        }.toSet()

        _state.value = _state.value.copy(
            extractedDigits = digits,
            originalDigits = originalIndices,
            hasPreviewBoard = true,
            showExtractionWarning = false,
            isLoading = false
        )
    }

    fun solveSudoku() {
        val digits = _state.value.extractedDigits
        if (digits.isEmpty() || !boardHasClues(digits)) return

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
                        isLoading = false,
                        showExtractionWarning = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        showExtractionWarning = true
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

    fun applyEditedBoard(digits: List<Int?>) {
        if (digits.size != 81) return
        if (digits == _state.value.extractedDigits) return
        val originalIndices = digits.mapIndexedNotNull { index, value ->
            if (value != null) index else null
        }.toSet()
        _state.value = _state.value.copy(
            extractedDigits = digits,
            originalDigits = originalIndices,
            isSolved = false,
            solvedDigits = emptyList(),
            hasPreviewBoard = true,
            showExtractionWarning = false
        )
    }

    fun dismissExtractionWarning() {
        _state.value = _state.value.copy(showExtractionWarning = false)
    }

    fun clearState() {
        _state.value = SudokuState()
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
