
package com.tiktokminimal.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class FeedVideo(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("storage_path") val storagePath: String,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("like_count") val likeCount: Long = 0,
    @SerialName("comment_count") val commentCount: Long = 0,
    @SerialName("share_count") val shareCount: Long = 0,
    @SerialName("created_at") val createdAt: String = "",
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class VideoInsert(
    val user_id: String,
    val storage_path: String,
    val caption: String,
    val hashtags: List<String>,
    val duration_ms: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class LikeRow(
    val user_id: String,
    val video_id: String
)

@Serializable
data class FollowRow(
    val follower_id: String,
    val following_id: String
)

@Serializable
data class CommentInsert(
    val video_id: String,
    val user_id: String,
    val body: String
)

@Serializable
data class ShareInsert(
    val user_id: String,
    val video_id: String
)

@Serializable
data class ProfileUpdate(
    val username: String,
    val bio: String
)

@Serializable
data class NotificationUpdate(
    @SerialName("read_at") val readAt: String
)

@Serializable
data class NotificationRow(
    val id: String,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("actor_id") val actorId: String,
    @SerialName("video_id") val videoId: String? = null
)

@Serializable
data class CommentFeedRow(
    val id: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

data class SearchResult(
    val profiles: List<Profile> = emptyList(),
    val videos: List<FeedVideo> = emptyList()
)
