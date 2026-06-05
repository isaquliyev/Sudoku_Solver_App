package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun EditableSudokuGrid(
    digits: List<Int?>,
    selectedIndex: Int?,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val boxTintA = colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val boxTintB = colorScheme.surface
    val outerColor = colorScheme.onSurface
    val boxColor = colorScheme.onSurface.copy(alpha = 0.75f)
    val cellBorder = colorScheme.onSurface.copy(alpha = 0.12f)

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val gridSize = maxWidth
        val cellSizePx = with(LocalDensity.current) { (gridSize / 9).toPx() }
        val fontSizeSp = (gridSize.value / 9 * 0.55f).coerceIn(10f, 22f)

        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until 9) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        val digit = digits.getOrNull(index)
                        val isSelected = index == selectedIndex
                        val tint = if ((row / 3 + col / 3) % 2 == 0) boxTintA else boxTintB

                        EditableCell(
                            digit = digit,
                            isSelected = isSelected,
                            boxTint = tint,
                            onSurface = colorScheme.onSurface,
                            primaryContainer = colorScheme.primaryContainer,
                            primary = colorScheme.primary,
                            cellBorder = cellBorder,
                            fontSizeSp = fontSizeSp,
                            onClick = { onCellClick(index) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        EditableGridLines(gridSize, cellSizePx, outerColor, boxColor)
    }
}

@Composable
private fun EditableCell(
    digit: Int?,
    isSelected: Boolean,
    boxTint: Color,
    onSurface: Color,
    primaryContainer: Color,
    primary: Color,
    cellBorder: Color,
    fontSizeSp: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectionBorder by animateColorAsState(
        targetValue = if (isSelected) primary else Color.Transparent,
        animationSpec = tween(200),
        label = "selectionBorder"
    )
    val selectionBg by animateColorAsState(
        targetValue = if (isSelected) primaryContainer.copy(alpha = 0.55f) else boxTint,
        animationSpec = tween(200),
        label = "selectionBg"
    )

    Box(
        modifier = modifier
            .background(selectionBg)
            .border(width = if (isSelected) 2.dp else 0.dp, color = selectionBorder)
            .drawBehind {
                val s = 0.5.dp.toPx()
                drawLine(cellBorder, Offset(0f, 0f), Offset(size.width, 0f), s)
                drawLine(cellBorder, Offset(0f, size.height), Offset(size.width, size.height), s)
                drawLine(cellBorder, Offset(0f, 0f), Offset(0f, size.height), s)
                drawLine(cellBorder, Offset(size.width, 0f), Offset(size.width, size.height), s)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (digit != null) {
            Text(
                text = digit.toString(),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EditableGridLines(
    gridSize: Dp,
    cellSizePx: Float,
    outerColor: Color,
    boxColor: Color
) {
    Canvas(modifier = Modifier.size(gridSize)) {
        val outerStroke = 3.dp.toPx()
        val boxStroke = 2.dp.toPx()

        drawLine(outerColor, Offset(0f, 0f), Offset(size.width, 0f), outerStroke)
        drawLine(outerColor, Offset(0f, size.height), Offset(size.width, size.height), outerStroke)
        drawLine(outerColor, Offset(0f, 0f), Offset(0f, size.height), outerStroke)
        drawLine(outerColor, Offset(size.width, 0f), Offset(size.width, size.height), outerStroke)

        listOf(3, 6).forEach { i ->
            val x = i * cellSizePx
            val y = i * cellSizePx
            drawLine(boxColor, Offset(x, 0f), Offset(x, size.height), boxStroke)
            drawLine(boxColor, Offset(0f, y), Offset(size.width, y), boxStroke)
        }
    }
}
