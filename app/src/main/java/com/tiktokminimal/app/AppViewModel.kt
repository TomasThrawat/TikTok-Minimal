
package com.tiktokminimal.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TikTokRepository(application)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _feed = MutableStateFlow<List<FeedVideo>>(emptyList())
    val feed: StateFlow<List<FeedVideo>> = _feed.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentFeedRow>>(emptyList())
    val comments: StateFlow<List<CommentFeedRow>> = _comments.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationRow>>(emptyList())
    val notifications: StateFlow<List<NotificationRow>> = _notifications.asStateFlow()

    private val _search = MutableStateFlow(SearchResult())
    val search: StateFlow<SearchResult> = _search.asStateFlow()

    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    private var feedOffset = 0
    private val pageSize = 6
    private var uploadJob: Job? = null

    init {
        viewModelScope.launch {
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                _authenticated.value = status is SessionStatus.Authenticated
                if (_authenticated.value) {
                    refreshProfile()
                    refreshFeed()
                } else {
                    _profile.value = null
                    _feed.value = emptyList()
                    _liked.value = emptySet()
                    _following.value = emptySet()
                }
            }
        }
    }

    fun handleDeepLink(intent: Intent) {
        runCatching {
            SupabaseProvider.client.handleDeeplinks(intent)
        }.onFailure {
            setError(it.message ?: "Authentication callback failed.")
        }
    }

    fun signIn(email: String, password: String) = action {
        require(email.contains("@")) { "Enter a valid email." }
        require(password.isNotBlank()) { "Enter your password." }
        repo.signIn(email, password)
    }

    fun signUp(email: String, password: String, username: String) = action {
        require(username.matches(Regex("^[A-Za-z0-9_.]{3,30}$"))) {
            "Username must be 3-30 characters using letters, numbers, dot, or underscore."
        }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        repo.signUp(email, password, username)
    }

    fun signInWithGoogle() = action {
        repo.signInWithGoogle()
    }

    fun signOut() = action {
        repo.signOut()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                feedOffset = 0
                val page = repo.feed(feedOffset, pageSize)
                _feed.value = page
                _hasMore.value = page.size == pageSize
                feedOffset += page.size
                hydrateLikeState(page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Could not load the feed.")
            } finally {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    fun loadMoreFeed() {
        if (!_hasMore.value || _loadingMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val page = repo.feed(feedOffset, pageSize)
                if (page.isEmpty()) {
                    _hasMore.value = false
                } else {
                    _feed.value = _feed.value + page
                    feedOffset += page.size
                    _hasMore.value = page.size == pageSize
                    hydrateLikeState(page)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Could not load more videos.")
            } finally {
                _loadingMore.value = false
            }
        }
    }

    private suspend fun hydrateLikeState(items: List<FeedVideo>) {
        val existingIds = items.map { it.id }.toSet()
        val likedIds = items.filter { repo.isLiked(it.id) }.map { it.id }.toSet()
        _liked.value = (_liked.value - existingIds) + likedIds
    }

    fun toggleLike(videoId: String) {
        viewModelScope.launch {
            val nowLiked = videoId in _liked.value
            try {
                repo.toggleLike(videoId, nowLiked)
                _liked.value =
                    if (nowLiked) _liked.value - videoId else _liked.value + videoId
                _feed.value = _feed.value.map {
                    if (it.id == videoId) it.copy(
                        likeCount = (it.likeCount + if (nowLiked) -1 else 1).coerceAtLeast(0)
                    ) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Like failed.")
            }
        }
    }

    fun checkFollowing(userId: String) {
        viewModelScope.launch {
            try {
                if (repo.isFollowing(userId)) {
                    _following.value += userId
                } else {
                    _following.value -= userId
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun toggleFollow(userId: String) {
        viewModelScope.launch {
            val nowFollowing = userId in _following.value
            try {
                repo.toggleFollow(userId, nowFollowing)
                _following.value =
                    if (nowFollowing) _following.value - userId
                    else _following.value + userId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Follow failed.")
            }
        }
    }

    fun openComments(videoId: String) {
        viewModelScope.launch {
            try {
                _comments.value = repo.comments(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Could not load comments.")
            }
        }
    }

    fun addComment(videoId: String, body: String, after: () -> Unit = {}) {
        if (body.isBlank()) return
        viewModelScope.launch {
            try {
                repo.addComment(videoId, body)
                _feed.value = _feed.value.map {
                    if (it.id == videoId) it.copy(commentCount = it.commentCount + 1) else it
                }
                _comments.value = repo.comments(videoId)
                after()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Comment failed.")
            }
        }
    }

    fun recordShare(videoId: String) {
        viewModelScope.launch {
            try {
                repo.recordShare(videoId)
                _feed.value = _feed.value.map {
                    if (it.id == videoId) it.copy(shareCount = it.shareCount + 1) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Share tracking failed.")
            }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                _notifications.value = repo.notifications()
                repo.markNotificationsRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Could not load notifications.")
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _search.value = SearchResult()
                return@launch
            }
            try {
                _search.value = repo.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Search failed.")
            }
        }
    }

    fun refreshProfile(userId: String? = repo.currentUserId) {
        if (userId == null) return
        viewModelScope.launch {
            try {
                _profile.value = repo.profile(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Could not load profile.")
            }
        }
    }

    fun updateProfile(username: String, bio: String) = action {
        require(username.matches(Regex("^[A-Za-z0-9_.]{3,30}$"))) {
            "Username must be 3-30 characters using letters, numbers, dot, or underscore."
        }
        repo.updateProfile(username, bio)
        refreshProfile()
    }

    fun uploadAvatar(uri: Uri) = action {
        repo.uploadProfileAvatar(uri)
        refreshProfile()
    }

    fun uploadVideo(
        uri: Uri,
        caption: String,
        hashtags: List<String>,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onFinished: () -> Unit
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null, success = null)
            try {
                val uid = repo.currentUserId ?: error("Sign in before uploading.")
                val ext = repo.mediaExtension(uri)
                val path = uid + "/" + UUID.randomUUID() + "." + ext

                repo.uploadVideo(uri, path, onProgress)

                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(getApplication(), uri)
                val durationMs = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()
                val width = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull()
                val height = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull()
                retriever.release()

                repo.finalizeUploadedVideo(
                    path = path,
                    caption = caption.trim(),
                    hashtags = hashtags,
                    durationMs = durationMs,
                    width = width,
                    height = height
                )

                setSuccess("Video published.")
                refreshFeed()
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onProgress(0f)
                setError(e.message ?: "Video upload failed.")
            } finally {
                onFinished()
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, success = null)
            try {
                block()
                setSuccess("Done.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setError(e.message ?: "Something went wrong.")
            } finally {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    private fun setError(message: String) {
        _ui.value = _ui.value.copy(error = message, success = null)
    }

    private fun setSuccess(message: String) {
        _ui.value = _ui.value.copy(success = message, error = null)
    }

    fun clearMessages() {
        _ui.value = _ui.value.copy(error = null, success = null)
    }

    fun selectedFileName(uri: Uri): String = repo.selectedFileName(uri)
}
