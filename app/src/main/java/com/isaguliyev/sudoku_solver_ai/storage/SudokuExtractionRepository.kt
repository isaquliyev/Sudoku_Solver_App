package com.isaguliyev.sudoku_solver_ai.storage

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves extracted Sudoku cell data to app-specific external storage.
 *
 * Storage location:
 * - Base: [Context.getExternalFilesDir](https://developer.android.com/reference/android/content/Context#getExternalFilesDir(java.lang.String))(null)
 * - On most devices: `/storage/emulated/0/Android/data/com.isaguliyev.sudoku_solver_ai/files/`
 * - Subfolder: `sudoku_cells/`
 * - Digit file: `extraction_<yyyyMMdd_HHmmss>.json` (81 elements: null or 1-9)
 * - Cell images (81 original-size crops, not 28×28): `cells_<timestamp>/cell_00.png` … `cell_80.png`
 *
 * Files are removed when the app is uninstalled. No storage permission required on API 19+.
 */
class SudokuExtractionRepository(private val context: Context) {

    companion object {
        private const val TAG = "SudokuExtractionRepo"
        private const val SUDOKU_CELLS_DIR = "sudoku_cells"
        private const val EXTRACTION_PREFIX = "extraction_"
        private const val CELLS_PREFIX = "cells_"
        private const val CELL_FILENAME_PATTERN = "cell_%02d.png"
        private const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
    }

    /**
     * Saves extracted digits and optional original-size cell bitmaps to external storage.
     * Uses a single timestamp for the extraction so digit file and cell folder are paired.
     *
     * @param digits List of 81 Int? (1-9 or null for empty)
     * @param originalCellBitmaps Optional list of 81 Bitmaps (original cell size). If null or size != 81, only digits are saved.
     * @return Result with the directory path on success, or error message on failure (e.g. external storage unavailable).
     */
    fun saveExtraction(digits: List<Int?>, originalCellBitmaps: List<Bitmap>? = null): Result<String> {
        if (digits.size != 81) {
            return Result.failure(IllegalArgumentException("digits must have exactly 81 elements, got ${digits.size}"))
        }

        val baseDir = resolveBaseDir() ?: run {
            Log.w(TAG, "getExternalFilesDir returned null; external storage may be unavailable")
            return Result.failure(IllegalStateException("External storage unavailable"))
        }

        val sudokuDir = File(baseDir, SUDOKU_CELLS_DIR)
        if (!sudokuDir.exists() && !sudokuDir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: ${sudokuDir.absolutePath}")
            return Result.failure(IllegalStateException("Failed to create sudoku_cells directory"))
        }

        val timestamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(Date())

        return try {
            // Save digits as JSON: [null, 5, null, 3, ...]
            val jsonArray = JSONArray()
            digits.forEach { value -> jsonArray.put(value ?: JSONObject.NULL) }
            val extractionFile = File(sudokuDir, "${EXTRACTION_PREFIX}${timestamp}.json")
            extractionFile.writeText(jsonArray.toString())
            Log.d(TAG, "Saved digits to ${extractionFile.absolutePath}")

            // Save original-size cell images if provided and count is 81
            if (!originalCellBitmaps.isNullOrEmpty() && originalCellBitmaps.size == 81) {
                val cellsDir = File(sudokuDir, "${CELLS_PREFIX}${timestamp}")
                if (cellsDir.mkdirs()) {
                    for (i in 0 until 81) {
                        val cellFile = File(cellsDir, String.format(Locale.US, CELL_FILENAME_PATTERN, i))
                        FileOutputStream(cellFile).use { out ->
                            originalCellBitmaps[i].compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    Log.d(TAG, "Saved 81 cell images to ${cellsDir.absolutePath}")
                } else {
                    Log.w(TAG, "Failed to create cells directory: ${cellsDir.absolutePath}")
                }
            }

            Result.success(sudokuDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save extraction", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves the app-specific external files directory. Returns null if external storage
     * is not mounted or no external files dir is available (e.g. first call on Android 11 can return null).
     * Uses ContextCompat.getExternalFilesDirs for more reliable primary volume resolution.
     */
    private fun resolveBaseDir(): File? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return null
        }
        // Prefer ContextCompat.getExternalFilesDirs: first element is primary; can work when getExternalFilesDir(null) returns null on Android 11
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        val primary = dirs.firstOrNull()
        if (primary != null) return primary
        // Fallback: direct getExternalFilesDir (may be null on first call on Android 11)
        return context.getExternalFilesDir(null)
    }
}
