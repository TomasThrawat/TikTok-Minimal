
package com.tiktokminimal.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TikTokRepository(private val context: Context) {
    private val supabase = SupabaseProvider.client

    val currentUserId: String?
        get() = supabase.auth.currentSessionOrNull()?.user?.id

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, username: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = kotlinx.serialization.json.buildJsonObject {
                put("user_name", username.trim())
                put("name", username.trim())
            }
        }
    }

    suspend fun signInWithGoogle() {
        supabase.auth.signInWith(Google)
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    suspend fun feed(offset: Int, pageSize: Int): List<FeedVideo> =
        supabase.from("feed_videos").select {
            order("created_at", Order.DESCENDING)
            order("id", Order.DESCENDING)
            range(offset, offset + pageSize - 1)
        }.decodeList()

    suspend fun search(query: String): SearchResult {
        val q = query.trim()
        if (q.isBlank()) return SearchResult()

        val profiles = supabase.from("profiles").select {
            filter { ilike("username", "%" + q.replace("%", "") + "%") }
            order("username", Order.ASCENDING)
            limit(20)
        }.decodeList<Profile>()

        val videos = if (q.startsWith("#")) {
            val tag = q.removePrefix("#").trim().lowercase()
            supabase.from("feed_videos").select {
                filter { contains("hashtags", listOf(tag)) }
                order("created_at", Order.DESCENDING)
                limit(20)
            }.decodeList()
        } else {
            supabase.from("feed_videos").select {
                filter { ilike("caption", "%" + q.replace("%", "") + "%") }
                order("created_at", Order.DESCENDING)
                limit(20)
            }.decodeList()
        }

        return SearchResult(profiles, videos)
    }

    suspend fun isLiked(videoId: String): Boolean {
        val uid = currentUserId ?: return false
        return supabase.from("likes").select {
            filter {
                eq("user_id", uid)
                eq("video_id", videoId)
            }
            limit(1)
        }.decodeList<LikeRow>().isNotEmpty()
    }

    suspend fun toggleLike(videoId: String, currentlyLiked: Boolean) {
        val uid = currentUserId ?: error("You must be signed in.")
        if (currentlyLiked) {
            supabase.from("likes").delete {
                filter {
                    eq("user_id", uid)
                    eq("video_id", videoId)
                }
            }
        } else {
            supabase.from("likes").insert(LikeRow(uid, videoId))
        }
    }

    suspend fun isFollowing(userId: String): Boolean {
        val uid = currentUserId ?: return false
        return supabase.from("follows").select {
            filter {
                eq("follower_id", uid)
                eq("following_id", userId)
            }
            limit(1)
        }.decodeList<FollowRow>().isNotEmpty()
    }

    suspend fun toggleFollow(userId: String, currentlyFollowing: Boolean) {
        val uid = currentUserId ?: error("You must be signed in.")
        if (currentlyFollowing) {
            supabase.from("follows").delete {
                filter {
                    eq("follower_id", uid)
                    eq("following_id", userId)
                }
            }
        } else {
            supabase.from("follows").insert(FollowRow(uid, userId))
        }
    }

    suspend fun comments(videoId: String): List<CommentFeedRow> =
        supabase.from("video_comment_feed").select {
            filter { eq("video_id", videoId) }
            order("created_at", Order.ASCENDING)
            limit(100)
        }.decodeList()

    suspend fun addComment(videoId: String, body: String) {
        val uid = currentUserId ?: error("You must be signed in.")
        supabase.from("comments").insert(
            CommentInsert(video_id = videoId, user_id = uid, body = body.trim())
        )
    }

    suspend fun recordShare(videoId: String) {
        val uid = currentUserId ?: error("You must be signed in.")
        supabase.from("shares").insert(ShareInsert(uid, videoId))
    }

    suspend fun notifications(): List<NotificationRow> {
        val uid = currentUserId ?: return emptyList()
        return supabase.from("notifications").select {
            filter { eq("recipient_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList()
    }

    suspend fun markNotificationsRead() {
        val uid = currentUserId ?: return
        supabase.from("notifications").update(
            NotificationUpdate(readAt = java.time.Instant.now().toString())
        ) {
            filter { eq("recipient_id", uid) }
        }
    }

    suspend fun profile(userId: String): Profile? =
        supabase.from("profiles").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<Profile>().firstOrNull()

    suspend fun updateProfile(username: String, bio: String) {
        val uid = currentUserId ?: error("You must be signed in.")
        supabase.from("profiles").update(ProfileUpdate(username.trim(), bio)) {
            filter { eq("id", uid) }
        }
    }

    suspend fun uploadVideo(uri: Uri, path: String, onProgress: (Float) -> Unit) {
        val workingFile = copyUriToCache(uri, "video-" + UUID.randomUUID() + ".upload")
        try {
            val upload = supabase.storage
                .from("videos")
                .resumable
                .createOrContinueUpload(path, workingFile)

            coroutineScope {
                val observer = launch {
                    upload.stateFlow.collect {
                        onProgress(it.progress.coerceIn(0f, 1f))
                    }
                }
                try {
                    upload.startOrResumeUploading()
                    onProgress(1f)
                } finally {
                    observer.cancel()
                }
            }
        } finally {
            workingFile.delete()
        }
    }

    suspend fun finalizeUploadedVideo(
        path: String,
        caption: String,
        hashtags: List<String>,
        durationMs: Long?,
        width: Int?,
        height: Int?
    ) {
        val uid = currentUserId ?: error("You must be signed in.")
        supabase.from("videos").insert(
            VideoInsert(
                user_id = uid,
                storage_path = path,
                caption = caption,
                hashtags = hashtags,
                duration_ms = durationMs,
                width = width,
                height = height
            )
        )
    }

    suspend fun uploadProfileAvatar(uri: Uri): String {
        val uid = currentUserId ?: error("You must be signed in.")
        val ext = mediaExtension(uri, fallback = "jpg")
        val file = copyUriToCache(uri, "avatar-" + UUID.randomUUID() + "." + ext)
        return try {
            val path = uid + "/avatar." + ext
            supabase.storage.from("avatars").upload(path, file) {
                upsert = true
            }
            val url = supabase.storage.from("avatars").publicUrl(path)
            supabase.from("profiles").update(mapOf("avatar_url" to url)) {
                filter { eq("id", uid) }
            }
            url
        } finally {
            file.delete()
        }
    }

    fun mediaExtension(uri: Uri, fallback: String = "mp4"): String {
        val mime = context.contentResolver.getType(uri)
        return when (mime) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> fallback
        }
    }

    fun selectedFileName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun copyUriToCache(uri: Uri, name: String): File {
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
        } ?: error("Could not open the selected file.")
        return file
    }
}
