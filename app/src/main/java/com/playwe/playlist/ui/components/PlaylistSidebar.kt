package com.playwe.playlist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.playwe.playlist.R
import com.playwe.playlist.model.Playlist
import com.playwe.playlist.model.PlaylistType
import com.playwe.playlist.ui.theme.AccentEmerald
import com.playwe.playlist.ui.theme.AccentRed
import com.playwe.playlist.ui.theme.Black
import com.playwe.playlist.ui.theme.DarkBackground
import com.playwe.playlist.ui.theme.SurfaceBorder
import com.playwe.playlist.ui.theme.SurfaceCard
import com.playwe.playlist.ui.theme.SurfaceDark
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary

@Composable
fun PlaylistSidebar(
    isOpen: Boolean,
    playlists: List<Playlist>,
    activeIndex: Int,
    onSelectPlaylist: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onClose() }
                .testTag("playlist_backdrop")
        )

        // Sidebar panel
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(320.dp)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder)
                .testTag("playlist_sidebar_panel")
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "PlayWe Logo",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "PLAYLISTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_playlist_sidebar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close playlists",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                // Playlists list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = playlists,
                        key = { index, playlist -> "${playlist.id}_$index" }
                    ) { index, playlist ->
                        val isSelected = index == activeIndex
                        val itemBg = if (isSelected) SurfaceCard else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBg)
                                .clickable { onSelectPlaylist(index) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("playlist_item_$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left border indicator if selected
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(44.dp)
                                    .background(if (isSelected) AccentEmerald else Color.Transparent)
                            )

                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                            ) {
                                val thumbUrl = playlist.thumbnailUrl
                                    ?: playlist.chapters.firstOrNull()?.thumbnailUrl
                                    ?: if (playlist.videoId != null) "https://img.youtube.com/vi/${playlist.videoId}/mqdefault.jpg" else ""

                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = playlist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp)
                                        .background(Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${playlist.chapters.size}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${playlist.chapters.size} exercises",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )

                                    val badgeText = if (playlist.type == PlaylistType.DIRECT) "Direct" else "YouTube"
                                    val badgeColor = if (playlist.type == PlaylistType.DIRECT) AccentEmerald else AccentRed

                                    Box(
                                        modifier = Modifier
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
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
        }
    }
}
