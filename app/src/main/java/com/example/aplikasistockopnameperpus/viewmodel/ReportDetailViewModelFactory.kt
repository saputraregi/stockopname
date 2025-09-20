package com.example.aplikasistockopnameperpus.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ReportDetailViewModelFactory(
    private val application: Application,
    private val reportId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportDetailViewModel::class.java)) {
            return ReportDetailViewModel(application, reportId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
