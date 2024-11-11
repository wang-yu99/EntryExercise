package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.graphics.Bitmap

class CaptureViewModel : ViewModel() {

    // MutableLiveData to hold the captured Bitmap
    private val _capturedBitmap = MutableLiveData<Bitmap>()
    val capturedBitmap: LiveData<Bitmap> get() = _capturedBitmap

    private val _evaluationResult = MutableLiveData<String?>()
    val evaluationResult: LiveData<String?> = _evaluationResult

    fun setCapturedBitmap(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
    }

    fun setEvaluationResult(result: String) {
        _evaluationResult.value = result
    }


}

