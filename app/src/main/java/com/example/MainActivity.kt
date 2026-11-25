package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.DownloadEntity
import com.example.ui.components.VideoPreviewDialog
import com.example.ui.components.YouTubeAuthDialog
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LinkSaveTheme
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.VividCoral
import com.example.ui.viewmodel.DownloaderViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_DOWNLOADS = "downloads"

class MainActivity : ComponentActivity() {

    private val viewModel: DownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            LinkSaveTheme(darkTheme = isDarkTheme) {
                LinkSaveApp(
                    viewModel = viewModel,
                    onOpenExternal = ::openMediaExternally,
                    onShareMedia = ::shareMediaFile
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleIncomingSharedText(sharedText)
            }
        }
    }

    private fun openMediaExternally(item: DownloadEntity) {
        val uri = item.localUri?.let { Uri.parse(it) } ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "Buka dengan pemutar"))
        }.onFailure {
            viewModel.emitMessage("Tidak ada aplikasi pemutar media yang tersedia.")
        }
    }

    private fun shareMediaFile(item: DownloadEntity) {
        val uri = item.localUri?.let { Uri.parse(it) } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "Bagikan media"))
        }.onFailure {
            viewModel.emitMessage("Gagal membuka dialog berbagi.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSaveApp(
    viewModel: DownloaderViewModel,
    onOpenExternal: (DownloadEntity) -> Unit,
    onShareMedia: (DownloadEntity) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME

    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val detectedPlatform by viewModel.detectedPlatform.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val selectedFormatId by viewModel.selectedFormatId.collectAsStateWithLifecycle()
    val downloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val totalDownloadCount by viewModel.totalDownloadCount.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var previewItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var showYouTubeAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(ElectricCyan, VividCoral))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showYouTubeAuthDialog = true },
                            modifier = Modifier.testTag("youtube_auth_button")
                        ) {
                            Icon(
                                imageVector = if (isYouTubeLoggedIn) {
                                    Icons.Rounded.VerifiedUser
                                } else {
                                    Icons.Rounded.AccountCircle
                                },
                                contentDescription = if (isYouTubeLoggedIn) {
                                    "Akun YouTube terhubung"
                                } else {
                                    "Login YouTube"
                                },
                                tint = if (isYouTubeLoggedIn) {
                                    SuccessEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleDarkTheme,
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = if (isDarkTheme) {
                                    "Mode terang"
                                } else {
                                    "Mode gelap"
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = { navigateTo(ROUTE_HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) {
                                        Icons.Rounded.DownloadForOffline
                                    } else {
                                        Icons.Outlined.DownloadForOffline
                                    },
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_DOWNLOADS,
                            onClick = { navigateTo(ROUTE_DOWNLOADS) },
                            icon = {
                                DownloadsNavIcon(
                                    selected = currentRoute == ROUTE_DOWNLOADS,
                                    activeCount = activeDownloadCount,
                                    totalCount = totalDownloadCount
                                )
                            },
                            label = { Text(stringResource(R.string.nav_downloads)) },
                            modifier = Modifier.testTag("nav_downloads_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = { navigateTo(ROUTE_HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) {
                                        Icons.Rounded.DownloadForOffline
                                    } else {
                                        Icons.Outlined.DownloadForOffline
                                    },
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_home_rail")
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_DOWNLOADS,
                            onClick = { navigateTo(ROUTE_DOWNLOADS) },
                            icon = {
                                DownloadsNavIcon(
                                    selected = currentRoute == ROUTE_DOWNLOADS,
                                    activeCount = activeDownloadCount,
                                    totalCount = totalDownloadCount
                                )
                            },
                            label = { Text(stringResource(R.string.nav_downloads)) },
                            modifier = Modifier.testTag("nav_downloads_rail")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            urlInput = urlInput,
                            detectedPlatform = detectedPlatform,
                            extractionState = extractionState,
                            selectedFormatId = selectedFormatId,
                            onUrlChange = viewModel::updateUrlInput,
                            onPasteAndAnalyze = viewModel::pasteUrlAndAnalyze,
                            onClearInput = viewModel::clearInput,
                            onAnalyzeClick = viewModel::analyzeCurrentLink,
                            onSelectFormat = viewModel::selectFormat,
                            onDownloadSelected = {
                                viewModel.startSelectedDownload(
                                    onStarted = { navigateTo(ROUTE_DOWNLOADS) }
                                )
                            },
                            onYouTubeAuthClick = { showYouTubeAuthDialog = true }
                        )
                    }
                    composable(ROUTE_DOWNLOADS) {
                        DownloadsScreen(
                            downloads = downloads,
                            selectedFilter = selectedFilter,
                            onFilterSelected = viewModel::setFilter,
                            onPlayItem = { item ->
                                if (item.isAudioOnly) {
                                    onOpenExternal(item)
                                } else {
                                    previewItem = item
                                }
                            },
                            onShareItem = onShareMedia,
                            onPauseItem = viewModel::pauseDownload,
                            onCancelItem = viewModel::cancelDownload,
                            onResumeOrRetryItem = viewModel::retryDownload,
                            onDeleteItem = viewModel::deleteDownload,
                            onClearHistory = viewModel::clearCompletedHistory,
                            onGoToHome = { navigateTo(ROUTE_HOME) }
                        )
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        VideoPreviewDialog(
            item = item,
            onDismiss = { previewItem = null },
            onOpenExternal = {
                previewItem = null
                onOpenExternal(item)
            },
            onShare = {
                previewItem = null
                onShareMedia(item)
            }
        )
    }

    if (showYouTubeAuthDialog) {
        YouTubeAuthDialog(
            isAlreadyLoggedIn = isYouTubeLoggedIn,
            onAuthCompleted = {
                showYouTubeAuthDialog = false
                viewModel.onYouTubeAuthSuccess()
            },
            onLogout = viewModel::logoutYouTube,
            onDismiss = {
                showYouTubeAuthDialog = false
                viewModel.refreshYouTubeAuthStatus()
            }
        )
    }
}

@Composable
private fun DownloadsNavIcon(
    selected: Boolean,
    activeCount: Int,
    totalCount: Int
) {
    val badgeCount = if (activeCount > 0) activeCount else totalCount
    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = if (activeCount > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (activeCount > 0) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                ) {
                    Text(text = if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            }
        }
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Rounded.FolderCopy
            } else {
                Icons.Outlined.FolderCopy
            },
            contentDescription = stringResource(R.string.nav_downloads)
        )
    }
}
