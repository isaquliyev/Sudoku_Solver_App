package com.isaguliyev.sudoku_solver_ai.scan

import android.content.Context
import android.graphics.Bitmap
import com.isaguliyev.sudoku_solver_ai.extractor.SudokuBoxExtractor
import com.isaguliyev.sudoku_solver_ai.solver.SudokuSolver
import com.isaguliyev.sudoku_solver_ai.viewmodel.boardHasClues
import com.isaguliyev.sudoku_solver_ai.viewmodel.emptySudokuBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class ScanPhase {
    EXTRACTING,
    SOLVING
}

enum class ScanFailureReason {
    EXTRACTION_FAILED,
    NO_CLUES,
    UNSOLVABLE
}

sealed class ScanResult {
    abstract val extractedDigits: List<Int?>
    abstract val screenshotPath: String

    data class Success(
        override val extractedDigits: List<Int?>,
        val solvedDigits: List<Int>,
        override val screenshotPath: String
    ) : ScanResult()

    data class Failure(
        val reason: ScanFailureReason,
        override val extractedDigits: List<Int?>,
        override val screenshotPath: String
    ) : ScanResult()
}

class SudokuScanProcessor(context: Context) {

    private val appContext = context.applicationContext
    private var extractor: SudokuBoxExtractor? = null

    private suspend fun ensureExtractor(): SudokuBoxExtractor {
        extractor?.let { return it }
        return withContext(Dispatchers.IO) {
            extractor ?: SudokuBoxExtractor(appContext).also { extractor = it }
        }
    }

    suspend fun process(
        bitmap: Bitmap,
        onProgress: (ScanPhase) -> Unit
    ): ScanResult = withContext(Dispatchers.IO) {
        val screenshotPath = saveScreenshot(bitmap)

        onProgress(ScanPhase.EXTRACTING)

        val imageBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

        val digits = try {
            ensureExtractor().extract(imageBytes)
        } catch (_: Exception) {
            emptyList()
        }

        if (digits.isEmpty()) {
            return@withContext ScanResult.Failure(
                reason = ScanFailureReason.EXTRACTION_FAILED,
                extractedDigits = emptySudokuBoard(),
                screenshotPath = screenshotPath
            )
        }

        if (!boardHasClues(digits)) {
            return@withContext ScanResult.Failure(
                reason = ScanFailureReason.NO_CLUES,
                extractedDigits = digits,
                screenshotPath = screenshotPath
            )
        }

        onProgress(ScanPhase.SOLVING)

        val board = SudokuSolver.listToBoard(digits)
        val success = SudokuSolver.solve(board)

        if (success) {
            ScanResult.Success(
                extractedDigits = digits,
                solvedDigits = SudokuSolver.boardToList(board),
                screenshotPath = screenshotPath
            )
        } else {
            ScanResult.Failure(
                reason = ScanFailureReason.UNSOLVABLE,
                extractedDigits = digits,
                screenshotPath = screenshotPath
            )
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): String {
        val file = File(appContext.cacheDir, "sudoku_scan.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file.absolutePath
    }
}

fun List<Int?>.toDigitIntArray(): IntArray =
    IntArray(81) { index -> this[index] ?: -1 }

fun IntArray.toDigitList(): List<Int?> =
    map { if (it < 0) null else it }
