package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ExtractedMediaInfo
import com.example.data.model.ExtractionUiState
import com.example.data.model.MediaPlatform
import com.example.data.model.VideoFormatOption
import com.example.ui.components.PlatformBadge
import com.example.ui.components.SupportedPlatformsStrip
import com.example.ui.components.brandColor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.VividCoral
import com.example.ui.util.formatDuration
import com.example.ui.util.formatFileSize

@Composable
fun HomeScreen(
    urlInput: String,
    detectedPlatform: MediaPlatform,
    extractionState: ExtractionUiState,
    selectedFormatId: String?,
    onUrlChange: (String) -> Unit,
    onPasteAndAnalyze: (String?) -> Unit,
    onClearInput: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownloadSelected: () -> Unit,
    onYouTubeAuthClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroLinkCard(
                urlInput = urlInput,
                detectedPlatform = detectedPlatform,
                isLoading = extractionState is ExtractionUiState.Loading,
                onUrlChange = onUrlChange,
                onPasteClick = {
                    focusManager.clearFocus()
                    val clip = clipboardManager.getText()?.text
                    onPasteAndAnalyze(clip)
                },
                onClearClick = onClearInput,
                onAnalyzeClick = {
                    focusManager.clearFocus()
                    onAnalyzeClick()
                }
            )

            SupportedPlatformsStrip(activePlatform = detectedPlatform)

            AnimatedContent(
                targetState = extractionState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "extraction_state"
            ) { state ->
                when (state) {
                    is ExtractionUiState.Idle -> HowToUseCard()
                    is ExtractionUiState.Loading -> AnalyzingCard(platform = state.platform)
                    is ExtractionUiState.Error -> ErrorExtractionCard(
                        message = state.message,
                        platform = state.platform,
                        requiresYouTubeAuth = state.requiresYouTubeAuth,
                        onRetry = onAnalyzeClick,
                        onYouTubeAuthClick = onYouTubeAuthClick
                    )
                    is ExtractionUiState.Success -> MediaPreviewCard(
                        media = state.media,
                        selectedFormatId = selectedFormatId,
                        onSelectFormat = onSelectFormat,
                        onDownloadClick = onDownloadSelected
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroLinkCard(
    urlInput: String,
    detectedPlatform: MediaPlatform,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onAnalyzeClick: () -> Unit
) {
    val primaryGlow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        color = primaryGlow.copy(alpha = 0.11f),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.9f, 0f)
                    )
                    drawCircle(
                        color = secondaryGlow.copy(alpha = 0.09f),
                        radius = size.width * 0.35f,
                        center = Offset(size.width * 0.1f, size.height)
                    )
                }
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Multi-Platform Downloader",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (urlInput.isNotBlank()) {
                    PlatformBadge(platform = detectedPlatform)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.hero_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.url_input_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = stringResource(R.string.url_input_label),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(
                            onClick = onClearClick,
                            modifier = Modifier.testTag("clear_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onAnalyzeClick() }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("paste_button"),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_paste),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onAnalyzeClick,
                    enabled = !isLoading && urlInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("analyze_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLoading) {
                            stringResource(R.string.btn_analyzing)
                        } else {
                            stringResource(R.string.btn_analyze)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingCard(platform: MediaPlatform) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, platform.brandColor().copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlatformBadge(platform = platform)
                Text(
                    text = "Mengambil info & kualitas video…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = platform.brandColor()
            )
        }
    }
}

@Composable
private fun ErrorExtractionCard(
    message: String,
    platform: MediaPlatform,
    requiresYouTubeAuth: Boolean,
    onRetry: () -> Unit,
    onYouTubeAuthClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Gagal Menganalisis ${platform.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (requiresYouTubeAuth) {
                    Button(
                        onClick = onYouTubeAuthClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("youtube_auth_cta_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Login YouTube")
                    }
                }
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("retry_analyze_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_retry))
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewCard(
    media: ExtractedMediaInfo,
    selectedFormatId: String?,
    onSelectFormat: (String) -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    ElectricCyan.copy(alpha = 0.5f),
                    VividCoral.copy(alpha = 0.4f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformBadge(platform = media.platform)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stringResource(R.string.video_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!media.thumbnailUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val durationText = formatDuration(media.durationSeconds)
                    if (durationText.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.78f)
                        ) {
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = media.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.choose_quality),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                media.formats.forEach { format ->
                    FormatOptionRow(
                        option = format,
                        isSelected = format.id == selectedFormatId,
                        onClick = { onSelectFormat(format.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("download_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_download_selected),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    option: VideoFormatOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("format_option_${option.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (option.isAudioOnly) Icons.Rounded.AudioFile else Icons.Rounded.Movie,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sizeText = formatFileSize(option.fileSizeBytes)
            if (sizeText.isNotEmpty()) {
                Text(
                    text = "Ukuran: $sizeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = option.qualityBadge,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Dipilih" else "Pilih format",
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HowToUseCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.how_to_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            StepItem(number = "1", text = stringResource(R.string.how_to_step_1))
            StepItem(number = "2", text = stringResource(R.string.how_to_step_2))
            StepItem(number = "3", text = stringResource(R.string.how_to_step_3))
        }
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
