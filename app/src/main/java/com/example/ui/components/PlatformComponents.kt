package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DownloadEntity
import com.example.data.model.MediaPlatform
import com.example.ui.theme.InstagramAmber
import com.example.ui.theme.InstagramMagenta
import com.example.ui.theme.TikTokCyan
import com.example.ui.theme.TikTokPink
import com.example.ui.theme.YouTubeRed

fun MediaPlatform.brandColor(): Color = when (this) {
    MediaPlatform.YOUTUBE -> YouTubeRed
    MediaPlatform.TIKTOK -> TikTokCyan
    MediaPlatform.INSTAGRAM -> InstagramMagenta
    MediaPlatform.OTHER -> Color(0xFF38BDF8)
}

fun MediaPlatform.brandIcon(): ImageVector = when (this) {
    MediaPlatform.YOUTUBE -> Icons.Rounded.SmartDisplay
    MediaPlatform.TIKTOK -> Icons.Rounded.MusicNote
    MediaPlatform.INSTAGRAM -> Icons.Rounded.PhotoCamera
    MediaPlatform.OTHER -> Icons.Rounded.Link
}

@Composable
fun PlatformBadge(
    platform: MediaPlatform,
    modifier: Modifier = Modifier
) {
    val accent = platform.brandColor()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = platform.brandIcon(),
                contentDescription = platform.displayName,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SupportedPlatformsStrip(
    activePlatform: MediaPlatform,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlatformMiniCard(
            name = "YouTube",
            subtitle = "Video & Shorts",
            icon = Icons.Rounded.SmartDisplay,
            gradientColors = listOf(YouTubeRed, Color(0xFFFF4D6D)),
            isHighlighted = activePlatform == MediaPlatform.YOUTUBE,
            modifier = Modifier.weight(1f)
        )
        PlatformMiniCard(
            name = "TikTok",
            subtitle = "No Watermark",
            icon = Icons.Rounded.MusicNote,
            gradientColors = listOf(TikTokCyan, TikTokPink),
            isHighlighted = activePlatform == MediaPlatform.TIKTOK,
            modifier = Modifier.weight(1f)
        )
        PlatformMiniCard(
            name = "Instagram",
            subtitle = "Reels & Post",
            icon = Icons.Rounded.PhotoCamera,
            gradientColors = listOf(InstagramAmber, InstagramMagenta),
            isHighlighted = activePlatform == MediaPlatform.INSTAGRAM,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlatformMiniCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val borderBrush = if (isHighlighted) {
        Brush.linearGradient(gradientColors)
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlighted) {
            gradientColors.first().copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VideoPreviewDialog(
    item: DownloadEntity,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlatformBadge(platform = item.platformEnum())
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup pemutar"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                val mediaUri = item.localUri?.let { Uri.parse(it) }
                if (mediaUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setVideoURI(mediaUri)
                                    setOnPreparedListener { mp: MediaPlayer ->
                                        mp.isLooping = false
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "File media tidak ditemukan",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan")
                    }
                    Button(
                        onClick = onOpenExternal,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pemutar Lain")
                    }
                }
            }
        }
    }
}
