package com.playwe.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playwe.playlist.model.PlaylistType
import com.playwe.playlist.ui.components.ChapterListContent
import com.playwe.playlist.ui.components.ChapterSidebar
import com.playwe.playlist.ui.components.CountdownTimerDialog
import com.playwe.playlist.ui.components.PlaylistSidebar
import com.playwe.playlist.ui.components.VideoPlayerView
import com.playwe.playlist.ui.components.YouTubeWebViewPlayer
import com.playwe.playlist.ui.theme.AccentAmber
import com.playwe.playlist.ui.theme.AccentBlue
import com.playwe.playlist.ui.theme.AccentEmerald
import com.playwe.playlist.ui.theme.AmberBg
import com.playwe.playlist.ui.theme.Black
import com.playwe.playlist.ui.theme.BlueBg
import com.playwe.playlist.ui.theme.DarkBackground
import com.playwe.playlist.ui.theme.EmeraldBg
import com.playwe.playlist.ui.theme.SurfaceBorder
import com.playwe.playlist.ui.theme.SurfaceCard
import com.playwe.playlist.ui.theme.SurfaceDark
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary
import com.playwe.playlist.viewmodel.PlayerViewModel
import com.playwe.playlist.viewmodel.TimerState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMainScreen(
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerUiState by viewModel.timerUiState.collectAsStateWithLifecycle()

    val activePlaylist = uiState.activePlaylist
    val activeChapter = uiState.activeChapter
    val chapters = uiState.chapters
    val playlistName = when {
        uiState.isLoading && uiState.playlists.isEmpty() -> "Loading playlists..."
        activePlaylist != null -> activePlaylist.name
        else -> "No Playlist"
    }

    val timerFormatted = remember(timerUiState.timerRemainingSeconds) {
        String.format(
            Locale.US,
            "%d:%02d",
            timerUiState.timerRemainingSeconds / 60,
            timerUiState.timerRemainingSeconds % 60
        )
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            if (!uiState.isFullscreen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(SurfaceDark)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Playlists button + title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { viewModel.togglePlaylistSidebar() }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .testTag("top_playlist_toggle")
                        ) {
                            IconButton(
                                onClick = { viewModel.togglePlaylistSidebar() },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Toggle Playlists",
                                    tint = if (uiState.isPlaylistSidebarOpen) TextPrimary else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = playlistName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )

                            if (activePlaylist != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (activePlaylist.type == PlaylistType.DIRECT) EmeraldBg else BlueBg)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (activePlaylist.type == PlaylistType.DIRECT) "DIRECT" else "YOUTUBE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activePlaylist.type == PlaylistType.DIRECT) AccentEmerald else AccentBlue,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Right: Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Sync / Refresh button
                            IconButton(
                                onClick = { viewModel.loadPlaylists() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("refresh_playlists_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Remote Data",
                                    tint = if (uiState.isLoading) AccentBlue else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Countdown Timer Button
                            val isTimerActive = timerUiState.timerState != TimerState.IDLE
                            IconButton(
                                onClick = { viewModel.setTimerDialogOpen(true) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("open_timer_btn"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isTimerActive) BlueBg else Color.Transparent,
                                    contentColor = if (isTimerActive) AccentBlue else TextSecondary
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Timer",
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (isTimerActive) {
                                        Text(
                                            text = timerFormatted,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(top = 16.dp)
                                        )
                                    }
                                }
                            }

                            // Slow Motion Button (0.25x)
                            IconButton(
                                onClick = { viewModel.toggleSlowMotion() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("slow_motion_btn"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.isSlowMotion) AmberBg else Color.Transparent,
                                    contentColor = if (uiState.isSlowMotion) AccentAmber else TextSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SlowMotionVideo,
                                    contentDescription = "Slow Motion (0.25x)",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Looping Button
                            IconButton(
                                onClick = { viewModel.toggleLooping() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("loop_btn"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.isLooping) EmeraldBg else Color.Transparent,
                                    contentColor = if (uiState.isLooping) AccentEmerald else TextSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Loop",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Fullscreen Toggle Button
                            IconButton(
                                onClick = { viewModel.toggleFullscreen() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("fullscreen_btn")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Chapters Toggle Button
                            IconButton(
                                onClick = { viewModel.toggleChapterSidebar() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chapter_sidebar_toggle"),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.isChapterSidebarOpen) SurfaceCard else Color.Transparent,
                                    contentColor = if (uiState.isChapterSidebarOpen) TextPrimary else TextSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewSidebar,
                                    contentDescription = "Toggle Chapters",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Black)
        ) {
            if (uiState.isLoading && uiState.playlists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading playlists from CDN...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else if (uiState.error != null && uiState.playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Unable to fetch playlists",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "Unknown network error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.loadPlaylists() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(text = "Retry", color = Color.White)
                    }
                }
            } else {
                // Main video player viewport
                val isDirect = uiState.isDirectVideo
                val currentVideoId = activePlaylist?.videoId ?: ""

                // Single Column layout: The video player Box keeps its composition identity when toggling fullscreen!
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = if (uiState.isFullscreen) {
                            Modifier
                                .fillMaxSize()
                                .background(Black)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Black)
                        }
                    ) {
                        if (isDirect || activeChapter?.videoUrl != null) {
                            VideoPlayerView(
                                chapter = activeChapter,
                                chapterIndex = uiState.activeChapterIndex,
                                totalChapters = chapters.size,
                                isPlaying = uiState.isPlaying,
                                isLooping = uiState.isLooping,
                                isSlowMotion = uiState.isSlowMotion,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onNextChapter = { viewModel.nextChapter() },
                                onPrevChapter = { viewModel.prevChapter() },
                                onChapterEnd = { viewModel.onChapterEnd() },
                                onPlayerStateChange = { playing -> viewModel.setPlaying(playing) }
                            )
                        } else {
                            YouTubeWebViewPlayer(
                                videoId = currentVideoId,
                                chapter = activeChapter,
                                chapterIndex = uiState.activeChapterIndex,
                                totalChapters = chapters.size,
                                isPlaying = uiState.isPlaying,
                                isLooping = uiState.isLooping,
                                isSlowMotion = uiState.isSlowMotion,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onNextChapter = { viewModel.nextChapter() },
                                onPrevChapter = { viewModel.prevChapter() },
                                onChapterEnd = { viewModel.onChapterEnd() },
                                onPlayerStateChange = { playing -> viewModel.setPlaying(playing) }
                            )
                        }
                    }

                    if (!uiState.isFullscreen) {
                        // 2. Exercises / Playlist Section Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(SurfaceDark)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewList,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${playlistName.uppercase()} EXERCISES (${chapters.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                        // 3. Scrollable Exercises / Chapter List
                        ChapterListContent(
                            chapters = chapters,
                            activeChapterIndex = uiState.activeChapterIndex,
                            isPlaying = uiState.isPlaying,
                            onSelectChapter = { idx -> viewModel.selectChapter(idx) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(DarkBackground)
                        )
                    }
                }
            }

            // Floating Fullscreen Exit/Toggle Button when in Fullscreen Mode
            if (uiState.isFullscreen) {
                IconButton(
                    onClick = { viewModel.toggleFullscreen() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("floating_fullscreen_exit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Playlist Drawer (left)
            PlaylistSidebar(
                isOpen = uiState.isPlaylistSidebarOpen,
                playlists = uiState.playlists,
                activeIndex = uiState.activePlaylistIndex,
                onSelectPlaylist = { idx -> viewModel.selectPlaylist(idx) },
                onClose = { viewModel.closeSidebars() }
            )

            // Chapters Drawer (right)
            ChapterSidebar(
                isOpen = uiState.isChapterSidebarOpen,
                playlistTitle = playlistName,
                chapters = chapters,
                activeChapterIndex = uiState.activeChapterIndex,
                isPlaying = uiState.isPlaying,
                onSelectChapter = { idx -> viewModel.selectChapter(idx) },
                onClose = { viewModel.closeSidebars() }
            )

            // Timer Dialog Modal
            CountdownTimerDialog(
                isOpen = timerUiState.isTimerDialogOpen,
                timerState = timerUiState.timerState,
                timerMinutes = timerUiState.timerMinutes,
                timerSeconds = timerUiState.timerSeconds,
                remainingSeconds = timerUiState.timerRemainingSeconds,
                onMinutesChange = { m -> viewModel.setTimerConfig(m, timerUiState.timerSeconds) },
                onSecondsChange = { s -> viewModel.setTimerConfig(timerUiState.timerMinutes, s) },
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onStop = { viewModel.stopTimer() },
                onDismiss = { viewModel.setTimerDialogOpen(false) }
            )
        }
    }
}
