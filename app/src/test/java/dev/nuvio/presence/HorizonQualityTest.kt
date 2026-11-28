package dev.nuvio.presence

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HorizonQualityTest {
    @Test
    fun hiddenArtworkDoesNotReuseCachedImageOrRequestCatalog() = runTest {
        val lookup = mockk<suspend (String) -> String>()
        val cover = "https://images.metahub.space/poster/medium/tt0111161/img"
        coEvery { lookup("Episode") } returns cover
        val sample = Playback("Episode", "", "mobile:tt0111161", "", "playing", 1000, 5000, 1.0, 0,
            appId = "com.nuvio.app")
        val engine = HorizonArtworkEngine(this, { testScheduler.currentTime }, lookup)
        try {
            engine.resolve(sample, "phone", true, true, null)
            runCurrent()
            assertEquals(cover, engine.resolve(sample, "phone", true, true, null)?.artwork)
            val hidden = engine.resolve(sample, "phone", false, true, null)
            runCurrent()
            assertFalse(hidden!!.artworkPermitted)
            assertEquals("", hidden.artwork)
            assertNull(hidden.localArtwork)
            coVerify(exactly = 1) { lookup("Episode") }
        } finally {
            engine.cancel(clearCache = true)
        }
    }
}
