package com.playwe.playlist.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.playwe.playlist.model.Chapter
import com.playwe.playlist.ui.theme.AccentAmber
import com.playwe.playlist.ui.theme.AccentEmerald
import com.playwe.playlist.ui.theme.Black
import com.playwe.playlist.ui.theme.EmeraldBg
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary

class WebAppInterface(
    private val onStateChangeCallback: (Int) -> Unit,
    private val onTimeUpdateCallback: (Float) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerStateChange(state: Int) {
        handler.post { onStateChangeCallback(state) }
    }

    @JavascriptInterface
    fun onTimeUpdate(seconds: Float) {
        handler.post { onTimeUpdateCallback(seconds) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebViewPlayer(
    videoId: String,
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    val startSec = chapter?.startSeconds ?: 0L
    val endSec = chapter?.endSeconds ?: 0L

    val jsInterface = remember {
        WebAppInterface(
            onStateChangeCallback = { state ->
                // 1 = playing, 2 = paused, 0 = ended
                onPlayerStateChange(state == 1)
                if (state == 0) {
                    onChapterEnd()
                }
            },
            onTimeUpdateCallback = { time ->
                if (endSec > 0 && time >= endSec) {
                    if (isLooping) {
                        webViewRef?.evaluateJavascript(
                            "if (window.player && window.player.seekTo) { window.player.seekTo($startSec, true); window.player.playVideo(); }",
                            null
                        )
                    } else {
                        onChapterEnd()
                    }
                }
            }
        )
    }

    // Helper to generate the self-contained HTML for YouTube IFrame API
    fun generateHtml(vid: String, initialStart: Long): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                    #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
                </style>
            </head>
            <body>
                <div id="player"></div>
                <script>
                    var tag = document.createElement('script');
                    tag.src = "https://www.youtube.com/iframe_api";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            videoId: '$vid',
                            playerVars: {
                                'playsinline': 1,
                                'autoplay': 1,
                                'controls': 0,
                                'modestbranding': 1,
                                'rel': 0,
                                'fs': 0,
                                'disablekb': 1,
                                'start': $initialStart
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onStateChange': onPlayerStateChange
                            }
                        });
                    }

                    function onPlayerReady(event) {
                        window.AndroidApp.onPlayerStateChange(-1); // Ready
                        setInterval(function() {
                            if (player && player.getCurrentTime) {
                                window.AndroidApp.onTimeUpdate(player.getCurrentTime());
                            }
                        }, 500);
                    }

                    function onPlayerStateChange(event) {
                        window.AndroidApp.onPlayerStateChange(event.data);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    // Load or switch video/chapter
    LaunchedEffect(videoId, chapter) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (videoId.isNotEmpty()) {
            webView.evaluateJavascript(
                "if (window.player && window.player.loadVideoById) { window.player.loadVideoById({'videoId': '$videoId', 'startSeconds': $startSec}); window.player.playVideo(); }",
                null
            )
        }
    }

    // Play/Pause sync
    LaunchedEffect(isPlaying) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (isPlaying) {
            webView.evaluateJavascript("if (window.player && window.player.playVideo) { window.player.playVideo(); }", null)
        } else {
            webView.evaluateJavascript("if (window.player && window.player.pauseVideo) { window.player.pauseVideo(); }", null)
        }
    }

    // Slow motion (0.25x or 1.0x)
    LaunchedEffect(isSlowMotion) {
        val webView = webViewRef ?: return@LaunchedEffect
        val rate = if (isSlowMotion) 0.25 else 1.0
        val mute = if (isSlowMotion) "window.player.mute();" else "window.player.unMute();"
        webView.evaluateJavascript(
            "if (window.player && window.player.setPlaybackRate) { window.player.setPlaybackRate($rate); $mute }",
            null
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .testTag("youtube_player_container")
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(jsInterface, "AndroidApp")
                    loadDataWithBaseURL("https://www.youtube.com", generateHtml(videoId, startSec), "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay clickable area for play/pause toggle
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
                    .testTag("yt_prev_chapter_btn"),
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
                    .testTag("yt_next_chapter_btn"),
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
