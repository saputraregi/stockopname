// SetupViewModelFactory.kt
/*package com.example.aplikasistockopnameperpus.viewmodel // Sesuaikan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// Terima 'Any?' karena kita belum tahu tipe reader sebenarnya (atau bisa null)
class SetupViewModelFactory(private val readerInstance: Any?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Teruskan instance reader (Any?) ke ViewModel
            return SetupViewModel(readerInstance) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
*/