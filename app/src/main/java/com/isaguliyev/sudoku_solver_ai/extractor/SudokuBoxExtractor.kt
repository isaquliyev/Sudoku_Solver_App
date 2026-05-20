package com.isaguliyev.sudoku_solver_ai.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Extracts digits from a Sudoku board image.
 * 
 * Ported from Python implementation - performs:
 * 1. Board Detection: Find the largest square (sudoku board) using OpenCV contour detection
 * 2. Perspective Transform: Warp the detected board to a flat view
 * 3. Cell Extraction: Split the board into 81 cells (9x9 grid)
 * 4. Digit Recognition: Use a PyTorch model to classify each cell as digit 1-9 or empty (null)
 */
class SudokuBoxExtractor(context: Context) {
    
    companion object {
        private const val TAG = "SudokuBoxExtractor"
        private const val IMG_SIZE = 64
        private const val MODEL_NAME = "modelv7.ptl"
        private const val CELL_CROP_PX = 0
    }
    
    private val model: Module
    
    // Index to label mapping: 0 is empty ("E"), 1-9 are digits 1-9 (no digit 0 class)
    private val idxToLabel: Map<Int, String> = mapOf(0 to "E") + (1..9).associateWith { it.toString() }
    
    init {
        val modelPath = assetFilePath(context, MODEL_NAME)
        Log.d(TAG, "Loading model from: $modelPath")
        model = Module.load(modelPath)
        Log.i(TAG, "Model loaded successfully")
    }
    
    /**
     * Main entry point - extracts digit values from a Sudoku board image.
     *
     * @param imageBytes Raw image bytes (JPEG, PNG, etc.)
     * @param modelInputCellBitmaps If non-null, filled with the 81 preprocessed cell images
     *   (6px-cropped on each side, grayscale, resized to 64×64) as passed to the model, in row-major order.
     * @param originalCellBitmaps If non-null, filled with the 81 original-size cell crops
     *   (ROI from warped board, before any crop/resize), in row-major order.
     * @return List of 81 integers (1-9) or null for empty cells, in row-major order.
     *         Returns empty list if no board is detected.
     */
    fun extract(
        imageBytes: ByteArray,
        modelInputCellBitmaps: MutableList<Bitmap>? = null,
        originalCellBitmaps: MutableList<Bitmap>? = null
    ): List<Int?> {
        // Decode image bytes to OpenCV Mat
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return emptyList()
        
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        
        // Convert RGBA to BGR (OpenCV default)
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR)
        
        // Find the largest square (sudoku board)
        val square = findBiggestSquare(img) ?: return emptyList()
        
        // Apply perspective transform to get a flat view
        val cropped = fourPointTransform(img, square)
        
        // Extract 81 cells and predict each one
        val labels = mutableListOf<Int?>()
        val cellW = cropped.cols() / 9
        val cellH = cropped.rows() / 9
        
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                val x1 = col * cellW
                val y1 = row * cellH
                val x2 = x1 + cellW
                val y2 = y1 + cellH

                // Extract full cell ROI
                val cellRect = Rect(x1, y1, x2 - x1, y2 - y1)
                val cell = Mat(cropped, cellRect)

                // Optional: original-size cell bitmap (before any crop or resize)
                if (originalCellBitmaps != null) {
                    val cellBitmap = Bitmap.createBitmap(cell.cols(), cell.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(cell, cellBitmap)
                    originalCellBitmaps.add(cellBitmap)
                }

                // Crop 6px from each side to remove grid lines before passing to model
                val croppedCell = if (cell.cols() > CELL_CROP_PX * 2 && cell.rows() > CELL_CROP_PX * 2) {
                    Mat(
                        cell,
                        Rect(
                            CELL_CROP_PX,
                            CELL_CROP_PX,
                            cell.cols() - CELL_CROP_PX * 2,
                            cell.rows() - CELL_CROP_PX * 2
                        )
                    )
                } else {
                    cell
                }

                val preprocessed = preprocessCell(croppedCell)

                // Save the cropped + preprocessed bitmap for the viewer
                if (modelInputCellBitmaps != null) {
                    val bgr = Mat()
                    Imgproc.cvtColor(preprocessed, bgr, Imgproc.COLOR_GRAY2BGR)
                    val cellBmp = Bitmap.createBitmap(IMG_SIZE, IMG_SIZE, Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(bgr, cellBmp)
                    modelInputCellBitmaps.add(cellBmp)
                    bgr.release()
                }

                val prediction = predictFromPreprocessed(preprocessed)
                preprocessed.release()
                labels.add(prediction)

                // Release croppedCell only if it is a distinct Mat (not the same reference as cell)
                if (croppedCell !== cell) croppedCell.release()
                cell.release()
            }
        }
        
        // Clean up
        img.release()
        cropped.release()
        
        return labels
    }
    
    /**
     * Finds the largest quadrilateral contour in the image (the Sudoku board).
     */
    private fun findBiggestSquare(img: Mat): MatOfPoint? {
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY)
        
        // Apply Gaussian blur
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        
        // Edge detection
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        var maxArea = 0.0
        var biggestSquare: MatOfPoint? = null
        
        for (cnt in contours) {
            val epsilon = 0.02 * Imgproc.arcLength(MatOfPoint2f(*cnt.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*cnt.toArray()), approx, epsilon, true)
            
            if (approx.toArray().size == 4) {
                val approxPoints = MatOfPoint(*approx.toArray())
                
                if (Imgproc.isContourConvex(approxPoints)) {
                    val boundingRect = Imgproc.boundingRect(approxPoints)
                    val aspectRatio = boundingRect.width.toFloat() / boundingRect.height
                    
                    if (aspectRatio in 0.5f..2.0f) {
                        val area = Imgproc.contourArea(approxPoints)
                        if (area > maxArea) {
                            maxArea = area
                            biggestSquare = approxPoints
                        }
                    }
                }
            }
        }
        
        // Clean up
        gray.release()
        blurred.release()
        edges.release()
        hierarchy.release()
        
        return biggestSquare
    }
    
    /**
     * Orders 4 points in the order: top-left, top-right, bottom-right, bottom-left.
     */
    private fun orderPoints(pts: MatOfPoint): MatOfPoint2f {
        val points = pts.toArray()
        val rect = arrayOfNulls<Point>(4)
        
        // Sum of coordinates: smallest = top-left, largest = bottom-right
        val sums = points.map { it.x + it.y }
        rect[0] = points[sums.indexOf(sums.minOrNull()!!)]
        rect[2] = points[sums.indexOf(sums.maxOrNull()!!)]
        
        // Difference of coordinates: smallest = top-right, largest = bottom-left
        val diffs = points.map { it.y - it.x }
        rect[1] = points[diffs.indexOf(diffs.minOrNull()!!)]
        rect[3] = points[diffs.indexOf(diffs.maxOrNull()!!)]
        
        return MatOfPoint2f(*rect.map { it!! }.toTypedArray())
    }
    
    /**
     * Applies a perspective transform to warp the detected quadrilateral to a rectangle.
     */
    private fun fourPointTransform(image: Mat, pts: MatOfPoint): Mat {
        val rect = orderPoints(pts)
        val rectArray = rect.toArray()
        
        val tl = rectArray[0]
        val tr = rectArray[1]
        val br = rectArray[2]
        val bl = rectArray[3]
        
        // Compute the width of the new image
        val widthA = sqrt((br.x - bl.x) * (br.x - bl.x) + (br.y - bl.y) * (br.y - bl.y))
        val widthB = sqrt((tr.x - tl.x) * (tr.x - tl.x) + (tr.y - tl.y) * (tr.y - tl.y))
        val maxWidth = max(widthA, widthB).toInt()
        
        // Compute the height of the new image
        val heightA = sqrt((tr.x - br.x) * (tr.x - br.x) + (tr.y - br.y) * (tr.y - br.y))
        val heightB = sqrt((tl.x - bl.x) * (tl.x - bl.x) + (tl.y - bl.y) * (tl.y - bl.y))
        val maxHeight = max(heightA, heightB).toInt()
        
        // Destination points for the transform
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )
        
        // Compute and apply the perspective transform
        val M = Imgproc.getPerspectiveTransform(rect, dst)
        val warped = Mat()
        Imgproc.warpPerspective(image, warped, M, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        
        // Clean up
        M.release()
        rect.release()
        dst.release()
        
        return warped
    }
    
    /**
     * Preprocesses a cell image into the form passed to the model.
     * Matches Python: convert("L") → Resize(64,64) → ToTensor() → Normalize([0.5],[0.5])
     * Caller must release the returned Mat.
     */
    private fun preprocessCell(cellMat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(cellMat, gray, Imgproc.COLOR_BGR2GRAY)
        val resized = Mat()
        Imgproc.resize(gray, resized, Size(IMG_SIZE.toDouble(), IMG_SIZE.toDouble()))
        gray.release()
        return resized
    }

    /**
     * Runs the model on a preprocessed 64×64 grayscale cell Mat.
     * Applies softmax then argmax, matching the Python test script.
     */
    private fun predictFromPreprocessed(preprocessedMat: Mat): Int? {
        val inputTensor = createGrayscaleTensor(preprocessedMat)
        val output = model.forward(IValue.from(inputTensor)).toTensor()
        val scores = output.dataAsFloatArray

        val expScores = scores.map { kotlin.math.exp(it.toDouble()).toFloat() }
        val sumExp = expScores.sum()
        val probs = expScores.map { it / sumExp }

        val predIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val label = idxToLabel[predIdx] ?: "E"
        return if (label == "E") null else label.toIntOrNull()
    }
    
    /**
     * Creates a grayscale tensor from an OpenCV Mat.
     * Applies normalization: (pixel / 255 - 0.5) / 0.5
     */
    private fun createGrayscaleTensor(mat: Mat): Tensor {
        val width = mat.cols()
        val height = mat.rows()
        val floatArray = FloatArray(width * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = mat.get(y, x)[0]
                // Normalize: (pixel/255 - 0.5) / 0.5 = pixel/255 * 2 - 1
                floatArray[y * width + x] = (((pixel / 255.0) - 0.5) / 0.5).toFloat()
            }
        }
        
        // Shape: [1, 1, height, width] - batch, channel, height, width
        return Tensor.fromBlob(floatArray, longArrayOf(1, 1, height.toLong(), width.toLong()))
    }
    
    /**
     * Copies an asset file to internal storage and returns the file path.
     * Uses an 8KB buffer and syncs so the file is fully written before PyTorch
     * loads it (avoids SIGBUS from truncated/corrupt model).
     */
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytes = input.read(buffer)
                    }
                    output.fd.sync()
                }
            }
            if (file.length() == 0L) {
                file.delete()
                throw IllegalStateException("Model asset is empty or failed to copy")
            }
        }
        return file.absolutePath
    }
}
