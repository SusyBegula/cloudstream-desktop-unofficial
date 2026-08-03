package com.lagradost.webclient.client

import com.lagradost.webclient.api.LoadResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Web-client counterpart of desktop-app's DetailsViewModel, backed by ApiClient's /api/load. */
class DetailsViewModel(private val api: ApiClient, private val scope: CoroutineScope) {
    private val _details = MutableStateFlow<LoadResponseDto?>(null)
    val details: StateFlow<LoadResponseDto?> = _details.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(provider: String, url: String) {
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            _error.value = null
            try {
                _details.value = api.load(provider, url)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load"
                _details.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
