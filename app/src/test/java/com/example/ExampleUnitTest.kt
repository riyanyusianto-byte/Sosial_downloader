package com.example

import com.example.data.model.MediaPlatform
import com.example.ui.util.formatDuration
import com.example.ui.util.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `extractFirstUrl parses link from shared social text`() {
        val shared = "Tonton video keren ini! https://vt.tiktok.com/ZS2abc123/ #fyp"
        assertEquals("https://vt.tiktok.com/ZS2abc123/", MediaPlatform.extractFirstUrl(shared))
        assertNull(MediaPlatform.extractFirstUrl("tidak ada tautan di sini"))
    }

    @Test
    fun `detectFromUrl identifies youtube tiktok and instagram`() {
        assertEquals(MediaPlatform.YOUTUBE, MediaPlatform.detectFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(MediaPlatform.YOUTUBE, MediaPlatform.detectFromUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(MediaPlatform.TIKTOK, MediaPlatform.detectFromUrl("https://vt.tiktok.com/ZS2abc123/"))
        assertEquals(MediaPlatform.INSTAGRAM, MediaPlatform.detectFromUrl("https://www.instagram.com/reel/Cx12345/"))
        assertEquals(MediaPlatform.OTHER, MediaPlatform.detectFromUrl("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun `formatters format file size and duration cleanly`() {
        assertEquals("512 KB", formatFileSize(512 * 1024L))
        assertEquals("12.5 MB", formatFileSize((12.5 * 1024 * 1024).toLong()))
        assertEquals("1:05", formatDuration(65))
        assertEquals("1:01:05", formatDuration(3665))
    }
}
