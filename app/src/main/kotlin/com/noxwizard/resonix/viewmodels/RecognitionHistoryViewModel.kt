package com.noxwizard.resonix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.RecognitionHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecognitionHistoryViewModel @Inject constructor(
    private val database: MusicDatabase
) : ViewModel() {

    val history: StateFlow<List<RecognitionHistory>> = database.getAllRecognitionHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteItem(item: RecognitionHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteRecognitionHistory(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearRecognitionHistory()
        }
    }
}
