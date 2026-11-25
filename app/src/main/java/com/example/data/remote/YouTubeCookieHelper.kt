package com.example.data.remote

import android.webkit.CookieManager
import java.security.MessageDigest

object YouTubeCookieHelper {

    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

    fun getYouTubeCookies(): String? {
        return runCatching {
            val manager = CookieManager.getInstance()
            val ytCookies = manager.getCookie(YOUTUBE_ORIGIN).orEmpty()
            val mYtCookies = manager.getCookie("https://m.youtube.com").orEmpty()
            mergeCookieStrings(ytCookies, mYtCookies).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun isLoggedIn(): Boolean {
        val cookies = getYouTubeCookies().orEmpty()
        if (cookies.isBlank()) return false
        return cookies.contains("SAPISID=") ||
            cookies.contains("__Secure-3PAPISID=") ||
            cookies.contains("LOGIN_INFO=") ||
            cookies.contains("SID=")
    }

    fun buildSapisidHashAuth(cookieString: String?): String? {
        if (cookieString.isNullOrBlank()) return null
        val sapisid = extractCookieValue(cookieString, "__Secure-3PAPISID")
            ?: extractCookieValue(cookieString, "SAPISID")
            ?: extractCookieValue(cookieString, "__Secure-1PAPISID")
            ?: return null

        val timestamp = System.currentTimeMillis() / 1000L
        val raw = "$timestamp $sapisid $YOUTUBE_ORIGIN"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    fun clearYouTubeCookies(onCleared: () -> Unit = {}) {
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies {
                manager.flush()
                onCleared()
            }
        }.onFailure {
            onCleared()
        }
    }

    private fun extractCookieValue(cookieString: String, key: String): String? {
        return cookieString.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }

    private fun mergeCookieStrings(first: String, second: String): String {
        val map = linkedMapOf<String, String>()
        listOf(first, second).forEach { raw ->
            raw.split(";").forEach { part ->
                val trimmed = part.trim()
                val idx = trimmed.indexOf('=')
                if (idx > 0) {
                    val k = trimmed.substring(0, idx).trim()
                    val v = trimmed.substring(idx + 1).trim()
                    map[k] = v
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
