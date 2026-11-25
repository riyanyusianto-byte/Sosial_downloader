package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LinkSave", appName)
  }

  @Test
  fun `detect platform and extract url from shared text`() {
    val shared = "Lihat video ini! https://vt.tiktok.com/ZS2abc123/ keren banget"
    val extracted = com.example.data.model.MediaPlatform.extractFirstUrl(shared)
    assertEquals("https://vt.tiktok.com/ZS2abc123/", extracted)
    assertEquals(
      com.example.data.model.MediaPlatform.TIKTOK,
      com.example.data.model.MediaPlatform.detectFromUrl(extracted!!)
    )
    assertEquals(
      com.example.data.model.MediaPlatform.YOUTUBE,
      com.example.data.model.MediaPlatform.detectFromUrl("https://youtu.be/dQw4w9WgXcQ")
    )
    assertEquals(
      com.example.data.model.MediaPlatform.INSTAGRAM,
      com.example.data.model.MediaPlatform.detectFromUrl("https://www.instagram.com/reel/Cx12345/")
    )
  }
}
