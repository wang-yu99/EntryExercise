package com.example.myapplication


object CaptureVmProvider {
    private var instance: CaptureViewModel? = null

    fun getInstance(): CaptureViewModel {
        if (instance == null) {
            instance = CaptureViewModel()
        }
        return instance!!
    }
}
