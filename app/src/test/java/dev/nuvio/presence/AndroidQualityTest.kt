package dev.nuvio.presence

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.nuvio.presence.ui.eclipse.EclipseNotifications
import dev.nuvio.presence.updater.UpdateClient
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidQualityTest {
    @Test
    fun updateManifestSelectsCompatibleAbiAndRejectsWrongPackage() {
        val digest = "a".repeat(64)
        val body = """
            {"schema":1,"packageName":"dev.nuvio.presence","versionName":"2.1.3","versionCode":1200,
             "build":200,"files":{"arm64-v8a":{"size":1024,"sha256":"$digest"}}}
        """.trimIndent()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body))
            server.start()
            val request = Request.Builder().url(server.url("/update.json")).build()
            val manifest = OkHttpClient().newCall(request).execute().use {
                JSONObject(requireNotNull(it.body).string())
            }
            fun parse(pkg: String, version: Long) = UpdateClient.parseManifest(
                manifest, "v2.1.3-build.200", setOf("SukiRPC-arm64-v8a.apk"), pkg, version, listOf("arm64-v8a"), ""
            )
            assertEquals("arm64-v8a", parse("dev.nuvio.presence", 1199).release?.abi)
            assertNull(parse("dev.nuvio.presence", 1200).release)
            assertThrows(IllegalArgumentException::class.java) { parse("dev.nuvio.other", 1199) }
        }
    }

    @Test
    fun foregroundNotificationHasPrivateVisibilityAndPublicPlaceholder() = runTest {
        val control = Robolectric.buildService(PresenceService::class.java)
        val service = control.get()
        val notification = EclipseNotifications(service, this, "quality", 1, PresenceService.STOP).starting()
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertEquals("SukiRPC", notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun foregroundNotificationRedactsMediaWhenPrivacyChanges() = runTest {
        val service = Robolectric.buildService(PresenceService::class.java).get()
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("quality", "Quality", NotificationManager.IMPORTANCE_LOW))
        val prefs = Settings.prefs(service)
        val original = Monitor.state.value
        val player = EclipseNotifications(service, backgroundScope, "quality", 91, PresenceService.STOP)
        val p = Playback("Example · s01:e02 SECRET", "S01:E02 Secret", "id", "", "paused", 30000, 90000, 0.0, 0)
        fun current() = manager.activeNotifications.single { it.id == 91 }.notification.extras
        try {
            Monitor.update { Dashboard(running = true, playback = p) }
            player.start()
            runCurrent()
            assertTrue(current().getString(Notification.EXTRA_TITLE).orEmpty().contains("SECRET"))
            assertEquals(33, current().getInt(Notification.EXTRA_PROGRESS))
            MediaPrivacy(setOf(Hidden.Episode, Hidden.Progress)).save(prefs)
            // A playback update tests the rebuilt frame even if the preference callback is queued.
            Monitor.update { it.copy(discord = "Atualizado") }
            runCurrent()
            assertEquals("Example", current().getString(Notification.EXTRA_TITLE))
            assertFalse(current().getString(Notification.EXTRA_TEXT).orEmpty().contains("Secret"))
            assertEquals(0, current().getInt(Notification.EXTRA_PROGRESS_MAX))

            MediaPrivacy(setOf(Hidden.Disabled)).save(prefs)
            Monitor.update { it.copy(discord = "Bloqueado") }
            runCurrent()
            assertEquals("SukiRPC", current().getString(Notification.EXTRA_TITLE))
        } finally {
            player.stop()
            Monitor.update { original }
            prefs.edit().clear().apply()
        }
    }
}
