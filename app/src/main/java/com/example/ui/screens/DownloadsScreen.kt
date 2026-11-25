package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaPlatform
import com.example.ui.components.PlatformBadge
import com.example.ui.components.brandColor
import com.example.ui.theme.SuccessEmerald
import com.example.ui.util.formatFileSize
import com.example.ui.util.formatSpeed

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    selectedFilter: MediaPlatform?,
    onFilterSelected: (MediaPlatform?) -> Unit,
    onPlayItem: (DownloadEntity) -> Unit,
    onShareItem: (DownloadEntity) -> Unit,
    onCancelItem: (Long) -> Unit,
    onRetryItem: (DownloadEntity) -> Unit,
    onDeleteItem: (DownloadEntity) -> Unit,
    onClearHistory: () -> Unit,
    onGoToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasHistoryToClear = downloads.any {
        val status = it.statusEnum()
        status == DownloadStatus.COMPLETED ||
            status == DownloadStatus.FAILED ||
            status == DownloadStatus.CANCELLED
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (hasHistoryToClear) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bersihkan")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions: List<Pair<MediaPlatform?, String>> = listOf(
                    null to stringResource(R.string.filter_all),
                    MediaPlatform.YOUTUBE to stringResource(R.string.platform_youtube),
                    MediaPlatform.TIKTOK to stringResource(R.string.platform_tiktok),
                    MediaPlatform.INSTAGRAM to stringResource(R.string.platform_instagram)
                )
                filterOptions.forEach { (platform, label) ->
                    val selected = selectedFilter == platform
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(platform) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_${platform?.name?.lowercase() ?: "all"}")
                    )
                }
            }

            if (downloads.isEmpty()) {
                EmptyDownloadsState(
                    isFiltered = selectedFilter != null,
                    onResetFilter = { onFilterSelected(null) },
                    onGoToHome = onGoToHome
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onPlay = { onPlayItem(item) },
                            onShare = { onShareItem(item) },
                            onCancel = { onCancelItem(item.id) },
                            onRetry = { onRetryItem(item) },
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val platform = item.platformEnum()
    val status = item.statusEnum()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (status == DownloadStatus.COMPLETED) {
                                Modifier.clickable(onClick = onPlay)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isAudioOnly) {
                                Icons.Rounded.AudioFile
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (status == DownloadStatus.COMPLETED) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.action_play),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlatformBadge(platform = platform)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    val progress = if (item.totalBytes > 0L) {
                        (item.bytesDownloaded.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = platform.brandColor()
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = platform.brandColor()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val downloadedTxt = formatFileSize(item.bytesDownloaded).ifEmpty { "0 KB" }
                        val totalTxt = formatFileSize(item.totalBytes)
                        val speedTxt = formatSpeed(item.speedBytesPerSec)
                        val statusLine = buildString {
                            append(downloadedTxt)
                            if (totalTxt.isNotEmpty()) append(" / $totalTxt")
                            if (speedTxt.isNotEmpty()) append(" • $speedTxt")
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_button_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_cancel),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                DownloadStatus.COMPLETED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SuccessEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            val sizeTxt = formatFileSize(item.bytesDownloaded)
                            Text(
                                text = if (sizeTxt.isNotEmpty()) {
                                    "Tersimpan • $sizeTxt"
                                } else {
                                    stringResource(R.string.status_completed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessEmerald
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onPlay,
                                modifier = Modifier.testTag("play_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.testTag("share_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.action_share)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.errorMessage ?: stringResource(R.string.status_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("retry_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.btn_retry),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(
    isFiltered: Boolean,
    onResetFilter: () -> Unit,
    onGoToHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_downloads),
            contentDescription = stringResource(R.string.empty_downloads_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.8f)
                .height(190.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isFiltered) "Tidak Ada Video di Kategori Ini" else stringResource(R.string.empty_downloads_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFiltered) {
                "Coba pilih filter Semua atau unduh video dari platform ini terlebih dahulu."
            } else {
                stringResource(R.string.empty_downloads_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (isFiltered) {
            OutlinedButton(
                onClick = onResetFilter,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Tampilkan Semua")
            }
        } else {
            Button(
                onClick = onGoToHome,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_go_home_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tempel Link Pertama")
            }
        }
    }
}
