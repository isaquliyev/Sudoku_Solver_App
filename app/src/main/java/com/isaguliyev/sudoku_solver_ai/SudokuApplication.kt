package com.isaguliyev.sudoku_solver_ai

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class SudokuApplication : Application() {

    companion object {
        private const val TAG = "SudokuApplication"
    }

    override fun onCreate() {
        super.onCreate()
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed")
        }
    }
}
