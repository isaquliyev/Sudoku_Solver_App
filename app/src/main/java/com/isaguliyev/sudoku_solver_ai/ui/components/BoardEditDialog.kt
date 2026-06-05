package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.isaguliyev.sudoku_solver_ai.viewmodel.emptySudokuBoard

@Composable
fun BoardEditDialog(
    initialDigits: List<Int?>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int?>) -> Unit
) {
    val startingDigits = if (initialDigits.size == 81) initialDigits else emptySudokuBoard()
    var draftDigits by remember { mutableStateOf(startingDigits) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit board",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                EditableSudokuGrid(
                    digits = draftDigits,
                    selectedIndex = selectedIndex,
                    onCellClick = { index ->
                        if (selectedIndex == index) {
                            draftDigits = draftDigits.toMutableList().also { it[index] = null }
                            selectedIndex = null
                        } else {
                            selectedIndex = index
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 3) {
                                val digit = row * 3 + col + 1
                                FilledTonalButton(
                                    onClick = {
                                        val idx = selectedIndex ?: return@FilledTonalButton
                                        draftDigits = draftDigits.toMutableList().also {
                                            it[idx] = digit
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = selectedIndex != null
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(draftDigits) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
