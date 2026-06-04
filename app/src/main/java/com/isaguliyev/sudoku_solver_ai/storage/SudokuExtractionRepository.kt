package com.isaguliyev.sudoku_solver_ai.storage

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        private const val SCAN_PREFIX = "scan_"
        private const val CELL_FILENAME_PATTERN = "cell_%02d.png"
        private const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
        private val TIMESTAMP_PATTERN = Pattern.compile("^${SCAN_PREFIX}(\\d{8}_\\d{6})(?:_\\d+)?$")
        private val TIMESTAMP_DISPLAY_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    data class ScanFolder(
        val folderName: String,
        val folderPath: String,
        val displayDate: String,
        val sortTimestampMillis: Long
    )

    data class CellImageFile(
        val fileName: String,
        val filePath: String
    )

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
                val cellsDir = buildUniqueScanFolder(sudokuDir, timestamp)
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

    fun listScanFoldersNewestFirst(): List<ScanFolder> {
        val sudokuDir = resolveSudokuDir() ?: return emptyList()
        val folders = sudokuDir.listFiles { file -> file.isDirectory && file.name.startsWith(SCAN_PREFIX) }
            ?.toList()
            ?: emptyList()

        return folders
            .map { dir ->
                val timestampMillis = parseScanFolderTimestamp(dir.name) ?: dir.lastModified()
                ScanFolder(
                    folderName = dir.name,
                    folderPath = dir.absolutePath,
                    displayDate = TIMESTAMP_DISPLAY_FORMAT.format(Date(timestampMillis)),
                    sortTimestampMillis = timestampMillis
                )
            }
            .sortedByDescending { it.sortTimestampMillis }
    }

    fun listCellFiles(scanFolderName: String): List<CellImageFile> {
        val sudokuDir = resolveSudokuDir() ?: return emptyList()
        val folder = File(sudokuDir, scanFolderName)
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder
            .listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?.map { file -> CellImageFile(fileName = file.name, filePath = file.absolutePath) }
            ?: emptyList()
    }

    fun renameCellFile(scanFolderName: String, currentFileName: String, requestedName: String): Result<String> {
        val sudokuDir = resolveSudokuDir() ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val folder = File(sudokuDir, scanFolderName)
        if (!folder.exists() || !folder.isDirectory) {
            return Result.failure(IllegalArgumentException("Scan folder not found"))
        }

        val source = File(folder, currentFileName)
        if (!source.exists() || !source.isFile) {
            return Result.failure(IllegalArgumentException("Cell file not found"))
        }

        val normalizedName = normalizeCellFileName(requestedName)
            ?: return Result.failure(IllegalArgumentException("Invalid filename"))
        val target = File(folder, normalizedName)
        if (target.exists() && !target.absolutePath.equals(source.absolutePath, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("A file with this name already exists"))
        }

        if (source.absolutePath.equals(target.absolutePath, ignoreCase = true)) {
            return Result.success(normalizedName)
        }

        return if (source.renameTo(target)) {
            Result.success(normalizedName)
        } else {
            Result.failure(IllegalStateException("Could not rename file"))
        }
    }

    fun getShareUri(scanFolderName: String, fileName: String): Result<android.net.Uri> {
        val sudokuDir = resolveSudokuDir() ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val file = File(File(sudokuDir, scanFolderName), fileName)
        if (!file.exists() || !file.isFile) {
            return Result.failure(IllegalArgumentException("File not found"))
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createFolderArchiveAndGetShareUri(scanFolderName: String): Result<Pair<android.net.Uri, String>> {
        val sudokuDir = resolveSudokuDir() ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val sourceFolder = File(sudokuDir, scanFolderName)
        if (!sourceFolder.exists() || !sourceFolder.isDirectory) {
            return Result.failure(IllegalArgumentException("Scan folder not found"))
        }

        val files = sourceFolder.listFiles { file -> file.isFile }?.toList().orEmpty()
        if (files.isEmpty()) {
            return Result.failure(IllegalStateException("Folder has no files to archive"))
        }

        val shareDir = File(context.cacheDir, "shared_archives")
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            return Result.failure(IllegalStateException("Could not prepare share directory"))
        }

        val archiveName = "${scanFolderName}.zip"
        val archiveFile = File(shareDir, archiveName)

        return try {
            if (archiveFile.exists()) {
                archiveFile.delete()
            }
            ZipOutputStream(FileOutputStream(archiveFile)).use { zipOut ->
                files.sortedBy { it.name.lowercase(Locale.US) }.forEach { file ->
                    FileInputStream(file).use { input ->
                        val entry = ZipEntry(file.name)
                        zipOut.putNextEntry(entry)
                        input.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                archiveFile
            )
            Result.success(uri to archiveName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveSudokuDir(): File? {
        val baseDir = resolveBaseDir() ?: return null
        val sudokuDir = File(baseDir, SUDOKU_CELLS_DIR)
        if (!sudokuDir.exists() && !sudokuDir.mkdirs()) return null
        return sudokuDir
    }

    private fun buildUniqueScanFolder(sudokuDir: File, timestamp: String): File {
        val initial = File(sudokuDir, "${SCAN_PREFIX}${timestamp}")
        if (!initial.exists()) return initial
        var suffix = 1
        while (true) {
            val candidate = File(sudokuDir, "${SCAN_PREFIX}${timestamp}_$suffix")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun parseScanFolderTimestamp(folderName: String): Long? {
        val match = TIMESTAMP_PATTERN.matcher(folderName)
        if (!match.matches()) return null
        val timestamp = match.group(1) ?: return null
        val parsedDate = runCatching {
            SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).parse(timestamp)
        }.getOrNull() ?: return null
        return parsedDate.time
    }

    private fun normalizeCellFileName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val base = trimmed.removeSuffix(".png").removeSuffix(".PNG").trim()
        if (base.isEmpty()) return null
        if (base.contains('/') || base.contains('\\')) return null
        val safeBase = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeBase.isBlank()) return null
        return "$safeBase.png"
    }
}
