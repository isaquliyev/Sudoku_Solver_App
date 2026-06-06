package com.isaguliyev.sudoku_solver_ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.isaguliyev.sudoku_solver_ai.viewmodel.emptySudokuBoard
import kotlinx.coroutines.delay

private const val FADE_DURATION_MS = 200

@Composable
fun BoardEditDialog(
    initialDigits: List<Int?>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int?>) -> Unit
) {
    val startingDigits = remember(initialDigits) {
        if (initialDigits.size == 81) initialDigits.toList() else emptySudokuBoard()
    }
    var draftDigits by remember(startingDigits) { mutableStateOf(startingDigits) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animatedVisible by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    val hasChanges = draftDigits != startingDigits
    val canClear = selectedIndex != null

    LaunchedEffect(Unit) {
        animatedVisible = true
    }

    fun requestDismiss(confirm: Boolean = false) {
        if (isClosing) return
        pendingConfirm = confirm
        isClosing = true
        animatedVisible = false
    }

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        delay(FADE_DURATION_MS.toLong())
        if (pendingConfirm && hasChanges) {
            onConfirm(draftDigits)
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestDismiss() }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = animatedVisible,
                enter = fadeIn(tween(FADE_DURATION_MS)),
                exit = fadeOut(tween(FADE_DURATION_MS)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = { requestDismiss() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        EditableSudokuGrid(
                            digits = draftDigits,
                            selectedIndex = selectedIndex,
                            onCellClick = { index -> selectedIndex = index },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (digit in 1..9) {
                                DigitChip(
                                    label = digit.toString(),
                                    enabled = selectedIndex != null,
                                    onClick = {
                                        val idx = selectedIndex ?: return@DigitChip
                                        draftDigits = draftDigits.toMutableList().also {
                                            it[idx] = digit
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            DigitChip(
                                label = "⌫",
                                enabled = canClear,
                                onClick = {
                                    val idx = selectedIndex ?: return@DigitChip
                                    draftDigits = draftDigits.toMutableList().also {
                                        it[idx] = null
                                    }
                                },
                                modifier = Modifier.width(44.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    draftDigits = startingDigits.toList()
                                    selectedIndex = null
                                },
                                enabled = hasChanges,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", style = MaterialTheme.typography.labelLarge)
                            }

                            Button(
                                onClick = { requestDismiss(confirm = true) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OK", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
