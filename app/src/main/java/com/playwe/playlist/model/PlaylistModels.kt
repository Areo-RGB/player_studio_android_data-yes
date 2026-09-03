package com.playwe.playlist.model

enum class PlaylistType {
    DIRECT,
    YOUTUBE
}

data class Chapter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startSeconds: Long,
    val endSeconds: Long,
    val videoUrl: String? = null,
    val localPath: String? = null,
    val thumbnailUrl: String? = null
) {
    val durationSeconds: Long
        get() = maxOf(0L, endSeconds - startSeconds)
}

data class Playlist(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: PlaylistType = PlaylistType.DIRECT,
    val videoId: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val chapters: List<Chapter> = emptyList()
)
