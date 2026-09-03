package com.playwe.playlist.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.playwe.playlist.model.Chapter
import com.playwe.playlist.ui.theme.AccentAmber
import com.playwe.playlist.ui.theme.AccentEmerald
import com.playwe.playlist.ui.theme.Black
import com.playwe.playlist.ui.theme.EmeraldBg
import com.playwe.playlist.ui.theme.EmeraldBorder
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    chapter: Chapter?,
    chapterIndex: Int,
    totalChapters: Int,
    isPlaying: Boolean,
    isLooping: Boolean,
    isSlowMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onChapterEnd: () -> Unit,
    onPlayerStateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = isPlaying
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(chapter?.videoUrl, chapter?.localPath) {
        val source = chapter?.videoUrl ?: chapter?.localPath
        if (!source.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(source)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (isPlaying) {
                exoPlayer.play()
            }
        }
    }

    // Playback state sync
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Playback speed sync (0.25x or 1.0x)
    LaunchedEffect(isSlowMotion) {
        val speed = if (isSlowMotion) 0.25f else 1.0f
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        exoPlayer.volume = if (isSlowMotion) 0f else 1f
    }

    val currentIsLooping by rememberUpdatedState(isLooping)
    val currentOnChapterEnd by rememberUpdatedState(onChapterEnd)
    val currentOnPlayerStateChange by rememberUpdatedState(onPlayerStateChange)

    // Listen to ExoPlayer playback events & end of media
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (currentIsLooping) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else {
                        currentOnChapterEnd()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                currentOnPlayerStateChange(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Release player on view disposal
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .testTag("video_player_container")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent tap layer for play/pause toggle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTogglePlayPause() }
        )

        // Center pause icon indicator
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Black.copy(alpha = 0.6f))
                    .clickable { onTogglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Left Previous Button overlay
        if (totalChapters > 1 && chapterIndex > 0) {
            IconButton(
                onClick = onPrevChapter,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .testTag("prev_chapter_btn"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Chapter",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right Next Button overlay
        if (totalChapters > 1 && chapterIndex < totalChapters - 1) {
            IconButton(
                onClick = onNextChapter,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .testTag("next_chapter_btn"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Chapter",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Left Chapter Badge
        if (chapter != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Black.copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) AccentEmerald else TextMuted)
                )
                Text(
                    text = chapter.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "(${chapterIndex + 1}/$totalChapters)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Bottom Right Status Badges (Slow Motion / Looping)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSlowMotion) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF78350F).copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Slow Motion (0.25x)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber
                    )
                }
            }

            if (isLooping) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(EmeraldBg.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Looping",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentEmerald
                    )
                }
            }
        }
    }
}
