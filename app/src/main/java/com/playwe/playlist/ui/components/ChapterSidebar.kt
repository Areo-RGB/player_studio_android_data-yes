package com.playwe.playlist.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.graphicsLayer
import com.playwe.playlist.model.Chapter
import com.playwe.playlist.ui.theme.AccentEmerald
import com.playwe.playlist.ui.theme.DarkBackground
import com.playwe.playlist.ui.theme.SurfaceBorder
import com.playwe.playlist.ui.theme.SurfaceCard
import com.playwe.playlist.ui.theme.SurfaceDark
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ChapterSidebar(
    isOpen: Boolean,
    playlistTitle: String,
    chapters: List<Chapter>,
    activeChapterIndex: Int,
    isPlaying: Boolean,
    onSelectChapter: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onClose() }
                .testTag("chapter_backdrop")
        )

        // Chapter panel on the right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder)
                .testTag("chapter_sidebar_panel")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(DarkBackground)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${playlistTitle.uppercase()} (${chapters.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_chapter_sidebar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close chapters",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                // Chapters list
                ChapterListContent(
                    chapters = chapters,
                    activeChapterIndex = activeChapterIndex,
                    isPlaying = isPlaying,
                    onSelectChapter = onSelectChapter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clip(CircleShape)
            .background(AccentEmerald)
    )
}

@Composable
fun ChapterListContent(
    chapters: List<Chapter>,
    activeChapterIndex: Int,
    isPlaying: Boolean,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        itemsIndexed(
            items = chapters,
            key = { index, chapter -> "${chapter.name}_${chapter.videoUrl ?: ""}_$index" }
        ) { index, chapter ->
            val isSelected = index == activeChapterIndex
            val itemBg = if (isSelected) SurfaceCard else Color.Transparent

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(itemBg)
                    .clickable { onSelectChapter(index) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("chapter_item_$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Indicator line
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(42.dp)
                        .background(if (isSelected) AccentEmerald else Color.Transparent)
                )

                // Chapter thumbnail
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbUrl = chapter.thumbnailUrl
                        ?: "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=300&q=80"

                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = chapter.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isSelected && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PulsingDot(modifier = Modifier.size(10.dp))
                        }
                    }
                }

                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 18.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val duration = chapter.durationSeconds
                        val durationText = String.format(
                            Locale.US,
                            "%d:%02d",
                            duration / 60,
                            duration % 60
                        )

                        Text(
                            text = durationText,
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        if (isSelected) {
                            Text(
                                text = "• Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = SurfaceBorder.copy(alpha = 0.4f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
