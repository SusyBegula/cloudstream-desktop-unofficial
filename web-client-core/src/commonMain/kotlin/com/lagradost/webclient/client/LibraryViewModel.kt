package com.lagradost.webclient.client

import com.lagradost.webclient.api.BookmarkDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Web-client counterpart of desktop-app's LibraryScreen state — bookmarks list, backed by ApiClient. */
class LibraryViewModel(private val api: ApiClient, private val scope: CoroutineScope) {
    private val _bookmarks = MutableStateFlow<List<BookmarkDto>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkDto>> = _bookmarks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refresh() {
        scope.launch(Dispatchers.Default) {
            _isLoading.value = true
            try {
                _bookmarks.value = api.getBookmarks().bookmarks
            } catch (e: Exception) {
                _bookmarks.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun remove(bookmark: BookmarkDto) {
        _bookmarks.value = _bookmarks.value.filterNot { it.provider == bookmark.provider && it.url == bookmark.url }
        scope.launch(Dispatchers.Default) {
            try {
                api.deleteBookmark(bookmark.provider, bookmark.url)
            } catch (e: Exception) {
                // best-effort
            }
        }
    }
}
