package com.example.data.remote

import com.example.data.model.ExtractedMediaInfo
import com.example.data.model.MediaPlatform
import com.example.data.model.VideoFormatOption
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class YouTubeBotCheckException(message: String) : IllegalStateException(message)

class MediaExtractorService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    private val mirrorClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun extractMedia(rawInput: String): Result<ExtractedMediaInfo> = withContext(Dispatchers.IO) {
        val extractedUrl = MediaPlatform.extractFirstUrl(rawInput)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Tautan tidak valid. Pastikan dimulai dengan https://")
            )

        val resolvedUrl = resolveRedirectsIfShortLink(extractedUrl)
        val platform = MediaPlatform.detectFromUrl(resolvedUrl)

        return@withContext runCatching {
            when (platform) {
                MediaPlatform.TIKTOK -> extractTikTok(resolvedUrl)
                MediaPlatform.YOUTUBE -> extractYouTube(resolvedUrl)
                MediaPlatform.INSTAGRAM -> extractInstagram(resolvedUrl)
                MediaPlatform.OTHER -> extractDirectOrOpenGraph(resolvedUrl, platform)
            }
        }
    }

    private fun resolveRedirectsIfShortLink(url: String): String {
        val lower = url.lowercase()
        val isShort = lower.contains("vt.tiktok.com") ||
            lower.contains("vm.tiktok.com") ||
            lower.contains("youtu.be") ||
            lower.contains("instagr.am") ||
            lower.contains("tiktok.com/t/")
        if (!isShort) return url

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .get()
                .build()
            client.newCall(req).execute().use { response ->
                response.request.url.toString()
            }
        }.getOrDefault(url)
    }

    private fun extractTikTok(url: String): ExtractedMediaInfo {
        val tikwmGet = runCatching { fetchTikWmGet(url) }.getOrNull()
        if (tikwmGet != null && tikwmGet.formats.isNotEmpty()) return tikwmGet

        val tikwmPost = runCatching { fetchTikWmPost(url) }.getOrNull()
        if (tikwmPost != null && tikwmPost.formats.isNotEmpty()) return tikwmPost

        val htmlResult = runCatching { fetchTikTokHtml(url) }.getOrNull()
        if (htmlResult != null && htmlResult.formats.isNotEmpty()) return htmlResult

        throw IllegalStateException(
            "Video TikTok tidak dapat diambil. Pastikan akun tidak privat dan tautan masih aktif."
        )
    }

    private fun fetchTikWmGet(url: String): ExtractedMediaInfo? {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val request = Request.Builder()
            .url("https://www.tikwm.com/api/?url=$encodedUrl&hd=1")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/json")
            .get()
            .build()
        return parseTikWmResponse(request, url)
    }

    private fun fetchTikWmPost(url: String): ExtractedMediaInfo? {
        val formBody = FormBody.Builder()
            .add("url", url)
            .add("hd", "1")
            .build()
        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/json")
            .post(formBody)
            .build()
        return parseTikWmResponse(request, url)
    }

    private fun parseTikWmResponse(request: Request, url: String): ExtractedMediaInfo? {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0) return null
            val data = root.optJSONObject("data") ?: return null

            val title = data.optString("title").ifBlank { "Video TikTok" }
            val authorObj = data.optJSONObject("author")
            val author = authorObj?.optString("nickname")?.ifBlank {
                authorObj.optString("unique_id")
            } ?: "Kreator TikTok"
            val duration = data.optInt("duration", 0).takeIf { it > 0 }
            val cover = normalizeTikWmUrl(
                data.optString("origin_cover").ifBlank { data.optString("cover") }
            )

            val formats = mutableListOf<VideoFormatOption>()
            val hdPlay = normalizeTikWmUrl(data.optString("hdplay"))
            val sdPlay = normalizeTikWmUrl(data.optString("play"))
            val wmPlay = normalizeTikWmUrl(data.optString("wmplay"))
            val musicUrl = normalizeTikWmUrl(data.optString("music"))

            val hdSize = data.optLong("hd_size", -1L).takeIf { it > 0 }
            val sdSize = data.optLong("size", -1L).takeIf { it > 0 }
            val wmSize = data.optLong("wm_size", -1L).takeIf { it > 0 }

            if (!hdPlay.isNullOrBlank()) {
                formats += VideoFormatOption(
                    id = "tt_hd",
                    label = "MP4 HD (Tanpa Watermark)",
                    qualityBadge = "HD",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = hdPlay,
                    fileSizeBytes = hdSize,
                    referer = "https://www.tikwm.com/",
                    userAgent = DESKTOP_UA
                )
            }
            if (!sdPlay.isNullOrBlank() && sdPlay != hdPlay) {
                formats += VideoFormatOption(
                    id = "tt_sd",
                    label = "MP4 Standar (Tanpa Watermark)",
                    qualityBadge = "SD",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = sdPlay,
                    fileSizeBytes = sdSize,
                    referer = "https://www.tikwm.com/",
                    userAgent = DESKTOP_UA
                )
            }
            if (!wmPlay.isNullOrBlank() && wmPlay != sdPlay) {
                formats += VideoFormatOption(
                    id = "tt_wm",
                    label = "MP4 (Dengan Watermark)",
                    qualityBadge = "WM",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = wmPlay,
                    fileSizeBytes = wmSize,
                    referer = "https://www.tikwm.com/",
                    userAgent = DESKTOP_UA
                )
            }
            if (!musicUrl.isNullOrBlank()) {
                formats += VideoFormatOption(
                    id = "tt_audio",
                    label = "Audio Musik (MP3)",
                    qualityBadge = "MP3",
                    mimeType = "audio/mpeg",
                    extension = "mp3",
                    downloadUrl = musicUrl,
                    isAudioOnly = true,
                    referer = "https://www.tikwm.com/",
                    userAgent = DESKTOP_UA
                )
            }

            if (formats.isEmpty()) return null
            return ExtractedMediaInfo(
                originalUrl = url,
                platform = MediaPlatform.TIKTOK,
                title = title,
                author = author,
                durationSeconds = duration,
                thumbnailUrl = cover,
                formats = formats
            )
        }
    }

    private fun normalizeTikWmUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("/")) "https://www.tikwm.com$trimmed" else trimmed
    }

    private fun fetchTikTokHtml(url: String): ExtractedMediaInfo? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string().orEmpty()
            val scriptRegex = Regex(
                """<script[^>]*id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val jsonText = scriptRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
            val root = JSONObject(jsonText)
            val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__") ?: return null
            val detail = defaultScope.optJSONObject("webapp.video-detail")
                ?.optJSONObject("itemInfo")
                ?.optJSONObject("itemStruct") ?: return null

            val desc = detail.optString("desc").ifBlank { "Video TikTok" }
            val author = detail.optJSONObject("author")?.optString("nickname").orEmpty()
                .ifBlank { "Kreator TikTok" }
            val videoObj = detail.optJSONObject("video") ?: return null
            val duration = videoObj.optInt("duration", 0).takeIf { it > 0 }
            val cover = videoObj.optString("cover").ifBlank { null }
            val playAddr = videoObj.optString("playAddr")
            val downloadAddr = videoObj.optString("downloadAddr")

            val formats = mutableListOf<VideoFormatOption>()
            if (playAddr.startsWith("http")) {
                formats += VideoFormatOption(
                    id = "tt_html_play",
                    label = "MP4 Video",
                    qualityBadge = "MP4",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = playAddr,
                    referer = "https://www.tiktok.com/",
                    userAgent = DESKTOP_UA
                )
            }
            if (downloadAddr.startsWith("http") && downloadAddr != playAddr) {
                formats += VideoFormatOption(
                    id = "tt_html_dl",
                    label = "MP4 Unduhan Asli",
                    qualityBadge = "HD",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = downloadAddr,
                    referer = "https://www.tiktok.com/",
                    userAgent = DESKTOP_UA
                )
            }
            if (formats.isEmpty()) return null
            return ExtractedMediaInfo(
                originalUrl = url,
                platform = MediaPlatform.TIKTOK,
                title = desc,
                author = author,
                durationSeconds = duration,
                thumbnailUrl = cover,
                formats = formats
            )
        }
    }

    private fun extractYouTube(url: String): ExtractedMediaInfo {
        val videoId = parseYouTubeVideoId(url)
            ?: throw IllegalArgumentException("ID video YouTube tidak ditemukan pada tautan.")

        val savedCookies = YouTubeCookieHelper.getYouTubeCookies()
        val sapisidAuth = YouTubeCookieHelper.buildSapisidHashAuth(savedCookies)
        val embedSession = runCatching { fetchYouTubeVisitorSession(videoId, savedCookies) }.getOrNull()

        val effectiveCookies = savedCookies ?: embedSession?.cookies
        val visitorData = embedSession?.visitorData

        val clients = buildList {
            // TVHTML5_SIMPLY_EMBEDDED_PLAYER often works even without login and works great with auth
            add(
                InnertubeClientConfig(
                    clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                    clientVersion = "2.0",
                    clientIdNum = "85",
                    userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
                    deviceMake = null,
                    deviceModel = null,
                    osName = null,
                    osVersion = null,
                    androidSdkVersion = null,
                    isEmbedded = true,
                    supportsAuth = true
                )
            )
            if (sapisidAuth != null) {
                add(
                    InnertubeClientConfig(
                        clientName = "MWEB",
                        clientVersion = "2.20241120.01.00",
                        clientIdNum = "2",
                        userAgent = MOBILE_UA,
                        deviceMake = null,
                        deviceModel = null,
                        osName = "Android",
                        osVersion = "14",
                        androidSdkVersion = null,
                        isEmbedded = false,
                        supportsAuth = true
                    )
                )
            }
            add(
                InnertubeClientConfig(
                    clientName = "ANDROID_VR",
                    clientVersion = "1.60.19",
                    clientIdNum = "28",
                    userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 14; en_US; Quest 3; Build/UP1A.231005.007.A1; Cronet/129.0.6668.50)",
                    deviceMake = "Oculus",
                    deviceModel = "Quest 3",
                    osName = "Android",
                    osVersion = "14",
                    androidSdkVersion = 34,
                    isEmbedded = false,
                    supportsAuth = false
                )
            )
            add(
                InnertubeClientConfig(
                    clientName = "IOS",
                    clientVersion = "19.45.4",
                    clientIdNum = "5",
                    userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                    deviceMake = "Apple",
                    deviceModel = "iPhone16,2",
                    osName = "iPhone",
                    osVersion = "18.1.0.22B83",
                    androidSdkVersion = null,
                    isEmbedded = false,
                    supportsAuth = false
                )
            )
        }

        var sawBotCheck = false
        var lastError: String? = null

        for (cfg in clients) {
            val result = runCatching {
                fetchYouTubeInnertube(
                    originalUrl = url,
                    videoId = videoId,
                    cfg = cfg,
                    visitorData = visitorData,
                    cookies = if (cfg.supportsAuth) effectiveCookies else embedSession?.cookies,
                    sapisidAuth = if (cfg.supportsAuth) sapisidAuth else null
                )
            }
            val info = result.getOrNull()
            if (info != null && info.formats.isNotEmpty()) {
                return info
            }
            val err = result.exceptionOrNull()
            if (err is YouTubeBotCheckException) {
                sawBotCheck = true
            }
            err?.message?.let { lastError = it }
        }

        // Fallback to public Piped & Invidious mirrors
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.adminforge.de"
        )
        for (instance in pipedInstances) {
            val piped = runCatching { fetchFromPiped(url, videoId, instance) }.getOrNull()
            if (piped != null && piped.formats.isNotEmpty()) {
                return piped
            }
        }

        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://iv.datura.network"
        )
        for (instance in invidiousInstances) {
            val invidious = runCatching { fetchFromInvidious(url, videoId, instance) }.getOrNull()
            if (invidious != null && invidious.formats.isNotEmpty()) {
                return invidious
            }
        }

        if (sawBotCheck || (lastError?.contains("bot", ignoreCase = true) == true) ||
            (lastError?.contains("Sign in", ignoreCase = true) == true)
        ) {
            throw YouTubeBotCheckException(
                "YouTube meminta verifikasi/login untuk memastikan bukan bot. Ketuk tombol 'Login YouTube' di bawah, masuk ke akun Google/YouTube Anda, lalu coba ulang."
            )
        }

        throw IllegalStateException(
            lastError ?: "Video YouTube tidak dapat diunduh langsung saat ini. Coba login YouTube atau ulangi beberapa saat lagi."
        )
    }

    private data class VisitorSession(
        val visitorData: String?,
        val cookies: String?
    )

    private fun fetchYouTubeVisitorSession(videoId: String, existingCookies: String?): VisitorSession? {
        val reqBuilder = Request.Builder()
            .url("https://www.youtube.com/embed/$videoId")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
            .header("Cookie", existingCookies ?: "SOCS=CAI;")
            .get()

        mirrorClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val setCookies = response.headers("Set-Cookie")
                .map { it.substringBefore(";") }
                .filter { it.contains("=") }
            val cookieHeader = if (setCookies.isNotEmpty()) {
                (setCookies + "SOCS=CAI").distinct().joinToString("; ")
            } else {
                "SOCS=CAI"
            }
            val html = response.body?.string().orEmpty()
            val visitorData = Regex(""""visitorData"\s*:\s*"([^"]+)"""")
                .find(html)?.groupValues?.getOrNull(1)
            return VisitorSession(visitorData = visitorData, cookies = cookieHeader)
        }
    }

    private data class InnertubeClientConfig(
        val clientName: String,
        val clientVersion: String,
        val clientIdNum: String,
        val userAgent: String,
        val deviceMake: String?,
        val deviceModel: String?,
        val osName: String?,
        val osVersion: String?,
        val androidSdkVersion: Int?,
        val isEmbedded: Boolean,
        val supportsAuth: Boolean
    )

    private fun fetchYouTubeInnertube(
        originalUrl: String,
        videoId: String,
        cfg: InnertubeClientConfig,
        visitorData: String?,
        cookies: String?,
        sapisidAuth: String?
    ): ExtractedMediaInfo? {
        val clientJson = JSONObject().apply {
            put("clientName", cfg.clientName)
            put("clientVersion", cfg.clientVersion)
            cfg.deviceMake?.let { put("deviceMake", it) }
            cfg.deviceModel?.let { put("deviceModel", it) }
            cfg.osName?.let { put("osName", it) }
            cfg.osVersion?.let { put("osVersion", it) }
            cfg.androidSdkVersion?.let { put("androidSdkVersion", it) }
            visitorData?.let { put("visitorData", it) }
            put("hl", "id")
            put("gl", "ID")
        }
        val contextJson = JSONObject().apply {
            put("client", clientJson)
            if (cfg.isEmbedded) {
                put(
                    "thirdParty",
                    JSONObject().put("embedUrl", "https://www.youtube.com/watch?v=$videoId")
                )
            }
        }
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", contextJson)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val reqBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", cfg.userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", cfg.clientIdNum)
            .header("X-YouTube-Client-Version", cfg.clientVersion)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }
        if (!cookies.isNullOrBlank()) {
            reqBuilder.header("Cookie", cookies)
        }
        if (!sapisidAuth.isNullOrBlank()) {
            reqBuilder.header("Authorization", sapisidAuth)
            reqBuilder.header("X-Origin", "https://www.youtube.com")
            reqBuilder.header("X-Goog-AuthUser", "0")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val root = JSONObject(body)
            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            if (status != "OK" && status.isNotEmpty()) {
                val reason = playability?.optString("reason").orEmpty()
                if (status == "LOGIN_REQUIRED" ||
                    reason.contains("bot", ignoreCase = true) ||
                    reason.contains("Sign in", ignoreCase = true)
                ) {
                    throw YouTubeBotCheckException("YouTube: $reason")
                }
                if (reason.isNotBlank()) {
                    throw IllegalStateException("YouTube: $reason")
                }
                return null
            }

            val videoDetails = root.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title").orEmpty().ifBlank { "Video YouTube" }
            val author = videoDetails?.optString("author").orEmpty().ifBlank { "Channel YouTube" }
            val duration = videoDetails?.optString("lengthSeconds")?.toIntOrNull()
            val thumbs = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = thumbs?.let { arr ->
                if (arr.length() > 0) arr.getJSONObject(arr.length() - 1).optString("url") else null
            } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val streamingData = root.optJSONObject("streamingData") ?: return null
            val formats = mutableListOf<VideoFormatOption>()

            val muxedArray = streamingData.optJSONArray("formats") ?: JSONArray()
            for (i in 0 until muxedArray.length()) {
                val item = muxedArray.getJSONObject(i)
                val streamUrl = item.optString("url")
                if (streamUrl.isBlank()) continue
                val mime = item.optString("mimeType").substringBefore(";").ifBlank { "video/mp4" }
                val qualityLabel = item.optString("qualityLabel").ifBlank { "MP4" }
                val contentLength = item.optString("contentLength").toLongOrNull()
                val ext = if (mime.contains("webm")) "webm" else "mp4"
                formats += VideoFormatOption(
                    id = "yt_mux_${item.optInt("itag", i)}",
                    label = "Video $qualityLabel (${ext.uppercase()})",
                    qualityBadge = qualityLabel,
                    mimeType = mime,
                    extension = ext,
                    downloadUrl = streamUrl,
                    fileSizeBytes = contentLength,
                    referer = "https://www.youtube.com/",
                    userAgent = cfg.userAgent
                )
            }

            val adaptiveArray = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
            var bestAudio: VideoFormatOption? = null
            var bestAudioBitrate = 0
            for (i in 0 until adaptiveArray.length()) {
                val item = adaptiveArray.getJSONObject(i)
                val mimeFull = item.optString("mimeType")
                if (!mimeFull.startsWith("audio/")) continue
                val streamUrl = item.optString("url")
                if (streamUrl.isBlank()) continue
                val bitrate = item.optInt("bitrate", 0)
                if (bitrate >= bestAudioBitrate) {
                    bestAudioBitrate = bitrate
                    val mime = mimeFull.substringBefore(";")
                    val ext = if (mime.contains("mp4")) "m4a" else "webm"
                    val kbps = (bitrate / 1000).takeIf { it > 0 }?.let { "${it}kbps" } ?: "AUDIO"
                    bestAudio = VideoFormatOption(
                        id = "yt_audio_${item.optInt("itag", i)}",
                        label = "Audio Saja ($kbps ${ext.uppercase()})",
                        qualityBadge = kbps,
                        mimeType = mime,
                        extension = ext,
                        downloadUrl = streamUrl,
                        fileSizeBytes = item.optString("contentLength").toLongOrNull(),
                        isAudioOnly = true,
                        referer = "https://www.youtube.com/",
                        userAgent = cfg.userAgent
                    )
                }
            }
            bestAudio?.let { formats += it }

            if (formats.isEmpty()) return null
            val sortedFormats = formats.sortedWith(
                compareBy<VideoFormatOption> { it.isAudioOnly }
                    .thenByDescending { parseResolutionNumber(it.qualityBadge) }
            )
            return ExtractedMediaInfo(
                originalUrl = originalUrl,
                platform = MediaPlatform.YOUTUBE,
                title = title,
                author = author,
                durationSeconds = duration,
                thumbnailUrl = thumbUrl,
                formats = sortedFormats
            )
        }
    }

    private fun fetchFromPiped(
        originalUrl: String,
        videoId: String,
        baseUrl: String
    ): ExtractedMediaInfo? {
        val req = Request.Builder()
            .url("$baseUrl/streams/$videoId")
            .header("User-Agent", DESKTOP_UA)
            .get()
            .build()
        mirrorClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val title = root.optString("title").ifBlank { "Video YouTube" }
            val author = root.optString("uploader").ifBlank { "Channel YouTube" }
            val duration = root.optInt("duration", 0).takeIf { it > 0 }
            val thumb = root.optString("thumbnailUrl").ifBlank {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            val formats = mutableListOf<VideoFormatOption>()
            val videos = root.optJSONArray("videoStreams") ?: JSONArray()
            for (i in 0 until videos.length()) {
                val item = videos.getJSONObject(i)
                if (item.optBoolean("videoOnly", true)) continue
                val streamUrl = item.optString("url")
                if (streamUrl.isBlank()) continue
                val quality = item.optString("quality").ifBlank { "MP4" }
                val mime = item.optString("mimeType").substringBefore(";").ifBlank { "video/mp4" }
                val ext = if (mime.contains("webm")) "webm" else "mp4"
                formats += VideoFormatOption(
                    id = "piped_v_$i",
                    label = "Video $quality (${ext.uppercase()})",
                    qualityBadge = quality,
                    mimeType = mime,
                    extension = ext,
                    downloadUrl = streamUrl,
                    fileSizeBytes = item.optLong("contentLength", -1L).takeIf { it > 0 },
                    userAgent = DESKTOP_UA
                )
            }

            val audios = root.optJSONArray("audioStreams") ?: JSONArray()
            if (audios.length() > 0) {
                val audioItem = audios.getJSONObject(0)
                val streamUrl = audioItem.optString("url")
                if (streamUrl.isNotBlank()) {
                    val mime = audioItem.optString("mimeType").substringBefore(";")
                        .ifBlank { "audio/mp4" }
                    val ext = if (mime.contains("mp4")) "m4a" else "webm"
                    val quality = audioItem.optString("quality").ifBlank { "Audio" }
                    formats += VideoFormatOption(
                        id = "piped_a_0",
                        label = "Audio Saja ($quality)",
                        qualityBadge = quality,
                        mimeType = mime,
                        extension = ext,
                        downloadUrl = streamUrl,
                        fileSizeBytes = audioItem.optLong("contentLength", -1L).takeIf { it > 0 },
                        isAudioOnly = true,
                        userAgent = DESKTOP_UA
                    )
                }
            }

            if (formats.isEmpty()) return null
            return ExtractedMediaInfo(
                originalUrl = originalUrl,
                platform = MediaPlatform.YOUTUBE,
                title = title,
                author = author,
                durationSeconds = duration,
                thumbnailUrl = thumb,
                formats = formats
            )
        }
    }

    private fun fetchFromInvidious(
        originalUrl: String,
        videoId: String,
        baseUrl: String
    ): ExtractedMediaInfo? {
        val req = Request.Builder()
            .url("$baseUrl/api/v1/videos/$videoId")
            .header("User-Agent", DESKTOP_UA)
            .get()
            .build()
        mirrorClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val title = root.optString("title").ifBlank { "Video YouTube" }
            val author = root.optString("author").ifBlank { "Channel YouTube" }
            val duration = root.optInt("lengthSeconds", 0).takeIf { it > 0 }
            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val formats = mutableListOf<VideoFormatOption>()
            val formatStreams = root.optJSONArray("formatStreams") ?: JSONArray()
            for (i in 0 until formatStreams.length()) {
                val item = formatStreams.getJSONObject(i)
                val streamUrl = item.optString("url")
                if (streamUrl.isBlank()) continue
                val quality = item.optString("qualityLabel").ifBlank { "MP4" }
                val container = item.optString("container").ifBlank { "mp4" }
                formats += VideoFormatOption(
                    id = "inv_$i",
                    label = "Video $quality (${container.uppercase()})",
                    qualityBadge = quality,
                    mimeType = "video/$container",
                    extension = container,
                    downloadUrl = streamUrl,
                    userAgent = DESKTOP_UA
                )
            }
            if (formats.isEmpty()) return null
            return ExtractedMediaInfo(
                originalUrl = originalUrl,
                platform = MediaPlatform.YOUTUBE,
                title = title,
                author = author,
                durationSeconds = duration,
                thumbnailUrl = thumb,
                formats = formats
            )
        }
    }

    private fun parseYouTubeVideoId(url: String): String? {
        val pattern = Regex(
            """(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/live/|youtube\.com/embed/)([A-Za-z0-9_-]{11})"""
        )
        return pattern.find(url)?.groupValues?.getOrNull(1)
    }

    private fun extractInstagram(url: String): ExtractedMediaInfo {
        val shortcode = Regex("""instagram\.com/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""")
            .find(url)?.groupValues?.getOrNull(1)

        if (!shortcode.isNullOrBlank()) {
            val embedResult = runCatching {
                fetchInstagramHtml("https://www.instagram.com/p/$shortcode/embed/captioned/", url)
            }.getOrNull()
            if (embedResult != null && embedResult.formats.isNotEmpty()) {
                return embedResult
            }

            val graphqlResult = runCatching {
                fetchInstagramGraphQl(shortcode, url)
            }.getOrNull()
            if (graphqlResult != null && graphqlResult.formats.isNotEmpty()) {
                return graphqlResult
            }
        }

        val pageResult = runCatching { fetchInstagramHtml(url, url) }.getOrNull()
        if (pageResult != null && pageResult.formats.isNotEmpty()) {
            return pageResult
        }

        throw IllegalStateException(
            "Video Instagram tidak dapat diakses. Pastikan postingan bersifat publik (bukan akun privat)."
        )
    }

    private fun fetchInstagramGraphQl(shortcode: String, originalUrl: String): ExtractedMediaInfo? {
        val variables = URLEncoder.encode("""{"shortcode":"$shortcode"}""", "UTF-8")
        val gqlUrl =
            "https://www.instagram.com/graphql/query/?query_hash=b3055c01b4b222b1a4e2134b09d5efad&variables=$variables"
        val req = Request.Builder()
            .url(gqlUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/json")
            .get()
            .build()
        mirrorClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val media = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("shortcode_media") ?: return null
            val videoUrl = media.optString("video_url")
            if (videoUrl.isBlank()) return null

            val thumb = media.optString("display_url").ifBlank { null }
            val owner = media.optJSONObject("owner")?.optString("username")
                ?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Instagram Creator"
            val caption = media.optJSONObject("edge_media_to_caption")
                ?.optJSONArray("edges")
                ?.optJSONObject(0)
                ?.optJSONObject("node")
                ?.optString("text")
                ?.take(100)
                ?.ifBlank { "Video Instagram" } ?: "Video Instagram"

            return ExtractedMediaInfo(
                originalUrl = originalUrl,
                platform = MediaPlatform.INSTAGRAM,
                title = caption,
                author = owner,
                thumbnailUrl = thumb,
                formats = listOf(
                    VideoFormatOption(
                        id = "ig_gql_0",
                        label = "MP4 Kualitas Asli",
                        qualityBadge = "HD",
                        mimeType = "video/mp4",
                        extension = "mp4",
                        downloadUrl = videoUrl,
                        referer = "https://www.instagram.com/",
                        userAgent = DESKTOP_UA
                    )
                )
            )
        }
    }

    private fun fetchInstagramHtml(targetUrl: String, originalUrl: String): ExtractedMediaInfo? {
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return null

            val videoUrls = linkedSetOf<String>()
            val videoUrlPatterns = listOf(
                Regex(""""video_url"\s*:\s*"([^"]+)""""),
                Regex(""""url"\s*:\s*"(https?:\\?/\\?/[^"]*?\.mp4[^"]*)"""")
            )
            for (pattern in videoUrlPatterns) {
                pattern.findAll(html).forEach { match ->
                    val cleaned = unescapeJsonUrl(match.groupValues[1])
                    if (cleaned.startsWith("http")) videoUrls += cleaned
                }
            }

            extractMetaContent(html, "og:video:secure_url")?.let { videoUrls += unescapeHtml(it) }
            extractMetaContent(html, "og:video")?.let { videoUrls += unescapeHtml(it) }
            Regex("""<video[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.let { videoUrls += unescapeHtml(it) }

            if (videoUrls.isEmpty()) return null

            val thumb = extractMetaContent(html, "og:image")?.let(::unescapeHtml)
                ?: Regex(""""display_url"\s*:\s*"([^"]+)"""").find(html)
                    ?.groupValues?.getOrNull(1)?.let(::unescapeJsonUrl)

            val rawTitle = extractMetaContent(html, "og:title")?.let(::unescapeHtml)
                ?: Regex("""class="Caption"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                    .find(html)?.groupValues?.getOrNull(1)
                    ?.replace(Regex("<[^>]+>"), " ")
                    ?.trim()
                ?: "Video Instagram Reels"

            val author = Regex("""@([A-Za-z0-9_.]+)""").find(html)?.groupValues?.getOrNull(0)
                ?: "Instagram Creator"

            val formats = videoUrls.mapIndexed { idx, vUrl ->
                VideoFormatOption(
                    id = "ig_$idx",
                    label = if (idx == 0) "MP4 Kualitas Asli" else "MP4 Alternatif ${idx + 1}",
                    qualityBadge = if (idx == 0) "HD" else "MP4",
                    mimeType = "video/mp4",
                    extension = "mp4",
                    downloadUrl = vUrl,
                    referer = "https://www.instagram.com/",
                    userAgent = DESKTOP_UA
                )
            }

            return ExtractedMediaInfo(
                originalUrl = originalUrl,
                platform = MediaPlatform.INSTAGRAM,
                title = rawTitle.take(120).ifBlank { "Video Instagram" },
                author = author,
                thumbnailUrl = thumb,
                formats = formats
            )
        }
    }

    private fun extractDirectOrOpenGraph(
        url: String,
        platform: MediaPlatform
    ): ExtractedMediaInfo {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .get()
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Gagal membuka tautan (HTTP ${response.code}).")
            }
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                val isAudio = contentType.startsWith("audio/")
                val ext = contentType.substringAfter("/").substringBefore(";").ifBlank {
                    if (isAudio) "mp3" else "mp4"
                }
                val length = response.header("Content-Length")?.toLongOrNull()
                return ExtractedMediaInfo(
                    originalUrl = url,
                    platform = platform,
                    title = url.substringAfterLast("/").substringBefore("?").ifBlank { "Media Tautan" },
                    author = "Tautan Langsung",
                    formats = listOf(
                        VideoFormatOption(
                            id = "direct_0",
                            label = "Unduhan Langsung (${ext.uppercase()})",
                            qualityBadge = ext.uppercase(),
                            mimeType = contentType.substringBefore(";"),
                            extension = ext,
                            downloadUrl = url,
                            fileSizeBytes = length,
                            isAudioOnly = isAudio,
                            userAgent = DESKTOP_UA
                        )
                    )
                )
            }

            val html = response.body?.string().orEmpty()
            val ogVideo = extractMetaContent(html, "og:video:secure_url")
                ?: extractMetaContent(html, "og:video")
                ?: Regex("""<video[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)

            if (ogVideo.isNullOrBlank()) {
                throw IllegalStateException(
                    "Tidak ditemukan stream video pada tautan ini. Gunakan tautan YouTube, TikTok, Instagram, atau file MP4 langsung."
                )
            }

            val title = extractMetaContent(html, "og:title")?.let(::unescapeHtml) ?: "Video Tautan"
            val thumb = extractMetaContent(html, "og:image")?.let(::unescapeHtml)
            return ExtractedMediaInfo(
                originalUrl = url,
                platform = platform,
                title = title,
                author = "Web Media",
                thumbnailUrl = thumb,
                formats = listOf(
                    VideoFormatOption(
                        id = "og_0",
                        label = "Video MP4",
                        qualityBadge = "MP4",
                        mimeType = "video/mp4",
                        extension = "mp4",
                        downloadUrl = unescapeHtml(ogVideo),
                        userAgent = DESKTOP_UA
                    )
                )
            )
        }
    }

    private fun extractMetaContent(html: String, propertyName: String): String? {
        val pattern1 = Regex(
            """<meta[^>]+(?:property|name)=["']$propertyName["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val pattern2 = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$propertyName["']""",
            RegexOption.IGNORE_CASE
        )
        return pattern1.find(html)?.groupValues?.getOrNull(1)
            ?: pattern2.find(html)?.groupValues?.getOrNull(1)
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("&amp;", "&")
    }

    private fun unescapeHtml(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun parseResolutionNumber(badge: String): Int {
        return Regex("""(\d+)""").find(badge)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
