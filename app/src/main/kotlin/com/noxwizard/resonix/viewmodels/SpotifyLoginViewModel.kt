package com.noxwizard.resonix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.auth.SpotifyAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpotifyLoginViewModel @Inject constructor(
    val authManager: SpotifyAuthManager
) : ViewModel() {
    
    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized = _isAuthorized.asStateFlow()

    init {
        checkAuthStatus()
    }

    fun checkAuthStatus() {
        _isAuthorized.value = authManager.isAuthorized()
    }

    fun updateAuthStatus(authorized: Boolean) {
        _isAuthorized.value = authorized
    }
}
