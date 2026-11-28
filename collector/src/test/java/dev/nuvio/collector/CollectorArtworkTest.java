package dev.nuvio.collector;

import android.app.Application;
import android.media.MediaMetadata;
import dev.nuvio.artwork.SessionArtwork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public final class CollectorArtworkTest {
    @Test public void privateArtworkIsRejectedBeforePublicMediaIdFallback() {
        MediaMetadata metadata = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ART_URI, "https://192.168.1.5/poster.jpg")
            .build();
        SessionArtwork.Candidate cover = SessionArtwork.resolve(metadata, "com.nuvio.tv", "tt0111161");
        assertTrue(cover.rejected);
        assertEquals("identifier", cover.source);
        assertEquals("https://images.metahub.space/poster/medium/tt0111161/img", cover.url);
    }
}
