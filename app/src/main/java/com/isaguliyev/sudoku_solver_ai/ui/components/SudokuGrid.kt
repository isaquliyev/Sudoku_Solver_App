package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a 9×9 Sudoku grid with:
 *  - Alternating 3×3 box background tints (checkerboard) for readability
 *  - Proper border hierarchy: outer (3dp) > box (2dp) > cell (0.5dp hairline)
 *  - Original digits shown bold in onSurface; solved digits animate in using primary colour
 *  - Font size derived from cell width so text always fits
 */
@Composable
fun SudokuGrid(
    digits: List<Int>,
    originalIndices: Set<Int>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val boxTintA    = colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val boxTintB    = colorScheme.surface
    val outerColor  = colorScheme.onSurface
    val boxColor    = colorScheme.onSurface.copy(alpha = 0.75f)
    val cellBorder  = colorScheme.onSurface.copy(alpha = 0.12f)

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val gridSize   = maxWidth
        val cellSizePx = with(LocalDensity.current) { (gridSize / 9).toPx() }
        val fontSizeSp = (gridSize.value / 9 * 0.55f).coerceIn(10f, 22f)

        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until 9) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 9) {
                        val index      = row * 9 + col
                        val digit      = digits.getOrNull(index) ?: 0
                        val isOriginal = index in originalIndices
                        val tint       = if ((row / 3 + col / 3) % 2 == 0) boxTintA else boxTintB

                        SudokuCell(
                            digit      = digit,
                            isOriginal = isOriginal,
                            boxTint    = tint,
                            onSurface  = colorScheme.onSurface,
                            primary    = colorScheme.primary,
                            cellBorder = cellBorder,
                            fontSizeSp = fontSizeSp,
                            modifier   = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        // Thick outer border + box dividers drawn on top via Canvas
        GridLines(gridSize, cellSizePx, outerColor, boxColor)
    }
}

@Composable
private fun SudokuCell(
    digit: Int,
    isOriginal: Boolean,
    boxTint: Color,
    onSurface: Color,
    primary: Color,
    cellBorder: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue    = if (isOriginal) onSurface else primary,
        animationSpec  = tween(durationMillis = 400),
        label          = "digitColor"
    )

    Box(
        modifier         = modifier
            .background(boxTint)
            .drawBehind {
                val s = 0.5.dp.toPx()
                drawLine(cellBorder, Offset(0f, 0f), Offset(size.width, 0f), s)
                drawLine(cellBorder, Offset(0f, size.height), Offset(size.width, size.height), s)
                drawLine(cellBorder, Offset(0f, 0f), Offset(0f, size.height), s)
                drawLine(cellBorder, Offset(size.width, 0f), Offset(size.width, size.height), s)
            },
        contentAlignment = Alignment.Center
    ) {
        if (digit != 0) {
            Text(
                text       = digit.toString(),
                fontSize   = fontSizeSp.sp,
                fontWeight = if (isOriginal) FontWeight.ExtraBold else FontWeight.SemiBold,
                color      = animatedColor,
                textAlign  = TextAlign.Center,
                maxLines   = 1
            )
        }
    }
}

@Composable
private fun GridLines(
    gridSize: Dp,
    cellSizePx: Float,
    outerColor: Color,
    boxColor: Color
) {
    Canvas(modifier = Modifier.size(gridSize)) {
        val outerStroke = 3.dp.toPx()
        val boxStroke   = 2.dp.toPx()

        // Outer border
        drawLine(outerColor, Offset(0f, 0f), Offset(size.width, 0f), outerStroke)
        drawLine(outerColor, Offset(0f, size.height), Offset(size.width, size.height), outerStroke)
        drawLine(outerColor, Offset(0f, 0f), Offset(0f, size.height), outerStroke)
        drawLine(outerColor, Offset(size.width, 0f), Offset(size.width, size.height), outerStroke)

        // Box dividers at columns/rows 3 and 6
        listOf(3, 6).forEach { i ->
            val x = i * cellSizePx
            val y = i * cellSizePx
            drawLine(boxColor, Offset(x, 0f), Offset(x, size.height), boxStroke)
            drawLine(boxColor, Offset(0f, y), Offset(size.width, y), boxStroke)
        }
    }
}

/**
 * Faint empty 9×9 preview grid shown before any image is loaded.
 */
@Composable
fun EmptySudokuGrid(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val cellBorder  = colorScheme.onSurface.copy(alpha = 0.10f)
    val boxColor    = colorScheme.onSurface.copy(alpha = 0.25f)
    val boxTintA    = colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val boxTintB    = colorScheme.surface

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val gridSize   = maxWidth
        val cellSizePx = with(LocalDensity.current) { (gridSize / 9).toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until 9) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 9) {
                        val tint = if ((row / 3 + col / 3) % 2 == 0) boxTintA else boxTintB
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(tint)
                                .drawBehind {
                                    val s = 0.5.dp.toPx()
                                    drawLine(cellBorder, Offset(0f, 0f), Offset(size.width, 0f), s)
                                    drawLine(cellBorder, Offset(0f, size.height), Offset(size.width, size.height), s)
                                    drawLine(cellBorder, Offset(0f, 0f), Offset(0f, size.height), s)
                                    drawLine(cellBorder, Offset(size.width, 0f), Offset(size.width, size.height), s)
                                }
                        )
                    }
                }
            }
        }

        GridLines(gridSize, cellSizePx, boxColor, boxColor.copy(alpha = 0.6f))
    }
}
