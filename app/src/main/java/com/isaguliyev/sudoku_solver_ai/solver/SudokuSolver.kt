package com.isaguliyev.sudoku_solver_ai.solver

/**
 * Sudoku solver using backtracking algorithm.
 */
object SudokuSolver {
    
    /**
     * Solves a Sudoku puzzle in-place.
     * 
     * @param board 9x9 array where 0 represents empty cells
     * @return true if solved successfully, false if no solution exists
     */
    fun solve(board: Array<IntArray>): Boolean {
        val emptyCell = findEmptyCell(board) ?: return true
        val (row, col) = emptyCell
        
        for (num in 1..9) {
            if (isValid(board, row, col, num)) {
                board[row][col] = num
                
                if (solve(board)) {
                    return true
                }
                
                board[row][col] = 0
            }
        }
        
        return false
    }
    
    /**
     * Finds the first empty cell (value = 0) in the board.
     */
    private fun findEmptyCell(board: Array<IntArray>): Pair<Int, Int>? {
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board[row][col] == 0) {
                    return Pair(row, col)
                }
            }
        }
        return null
    }
    
    /**
     * Checks if placing a number at the given position is valid.
     */
    private fun isValid(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row
        for (c in 0 until 9) {
            if (board[row][c] == num) return false
        }
        
        // Check column
        for (r in 0 until 9) {
            if (board[r][col] == num) return false
        }
        
        // Check 3x3 box
        val boxRowStart = (row / 3) * 3
        val boxColStart = (col / 3) * 3
        for (r in boxRowStart until boxRowStart + 3) {
            for (c in boxColStart until boxColStart + 3) {
                if (board[r][c] == num) return false
            }
        }
        
        return true
    }
    
    /**
     * Converts a flat list of 81 values to a 9x9 board.
     * Null values are converted to 0.
     */
    fun listToBoard(list: List<Int?>): Array<IntArray> {
        return Array(9) { row ->
            IntArray(9) { col ->
                list[row * 9 + col] ?: 0
            }
        }
    }
    
    /**
     * Converts a 9x9 board to a flat list of 81 values.
     */
    fun boardToList(board: Array<IntArray>): List<Int> {
        return board.flatMap { it.toList() }
    }
    
    /**
     * Creates a deep copy of the board.
     */
    fun copyBoard(board: Array<IntArray>): Array<IntArray> {
        return Array(9) { row -> board[row].copyOf() }
    }
}
