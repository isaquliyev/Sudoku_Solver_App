package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SudokuGrid(
    digits: List<Int>,
    originalIndices: Set<Int>,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    val originalColor = MaterialTheme.colorScheme.onSurface
    val solvedColor = MaterialTheme.colorScheme.primary
    
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .border(3.dp, borderColor)
            .padding(1.dp)
    ) {
        for (boxRow in 0 until 3) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (boxCol in 0 until 3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.5.dp, borderColor)
                    ) {
                        // 3x3 sub-grid
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (cellRow in 0 until 3) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    for (cellCol in 0 until 3) {
                                        val row = boxRow * 3 + cellRow
                                        val col = boxCol * 3 + cellCol
                                        val index = row * 9 + col
                                        val digit = digits.getOrNull(index) ?: 0
                                        val isOriginal = index in originalIndices
                                        
                                        SudokuCell(
                                            digit = digit,
                                            isOriginal = isOriginal,
                                            originalColor = originalColor,
                                            solvedColor = solvedColor,
                                            borderColor = borderColor.copy(alpha = 0.3f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
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

@Composable
private fun SudokuCell(
    digit: Int,
    isOriginal: Boolean,
    originalColor: Color,
    solvedColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(0.5.dp, borderColor)
            .background(
                if (isOriginal) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        if (digit != 0) {
            Text(
                text = digit.toString(),
                fontSize = 18.sp,
                fontWeight = if (isOriginal) FontWeight.Bold else FontWeight.Normal,
                color = if (isOriginal) originalColor else solvedColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptySudokuGrid(
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .border(2.dp, borderColor)
            .padding(1.dp)
    ) {
        for (boxRow in 0 until 3) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (boxCol in 0 until 3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (cellRow in 0 until 3) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    for (cellCol in 0 until 3) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .border(0.5.dp, borderColor.copy(alpha = 0.5f))
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
