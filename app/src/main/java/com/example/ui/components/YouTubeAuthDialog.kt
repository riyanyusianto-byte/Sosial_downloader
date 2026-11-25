package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.YouTubeCookieHelper
import com.example.ui.theme.SuccessEmerald

private const val YOUTUBE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fm.youtube.com%2F&hl=id"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeAuthDialog(
    isAlreadyLoggedIn: Boolean,
    onAuthCompleted: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    var detectedSession by remember(isAlreadyLoggedIn) { mutableStateOf(isAlreadyLoggedIn) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Verifikasi / Login YouTube",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    )
                    if (detectedSession) {
                        TextButton(
                            onClick = {
                                onLogout()
                                detectedSession = false
                            },
                            modifier = Modifier.testTag("youtube_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keluar")
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_yt_auth_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup"
                        )
                    }
                }

                if (detectedSession) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessEmerald.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Sesi YouTube terhubung!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Button(
                                onClick = onAuthCompleted,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuccessEmerald,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("yt_auth_done_button")
                            ) {
                                Text("Analisis Ulang")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Masuk ke akun Google/YouTube di bawah agar YouTube tidak mendeteksi permintaan sebagai bot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                // Use standard Chrome UA without '; wv' so Google Sign-In permits login
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        cookieManager.flush()
                                        if (YouTubeCookieHelper.isLoggedIn()) {
                                            detectedSession = true
                                        }
                                    }
                                }

                                loadUrl(
                                    if (isAlreadyLoggedIn) "https://m.youtube.com/" else YOUTUBE_LOGIN_URL
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
