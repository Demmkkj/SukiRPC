package dev.nuvio.collector;

import dev.nuvio.artwork.ArtworkPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CollectorArtworkPolicyTest {
    @Test public void publicCatalogArtworkCanBePublishedWithoutPrivateUrlParameters() {
        String source = "com.nuvio.tv";
        String cover = "https://images.metahub.space/poster/medium/tt0111161/img";
        assertEquals(cover, ArtworkPolicy.fromMediaId("tt0111161", source));
        assertEquals(cover, ArtworkPolicy.publicUrl(cover, source));
        assertEquals("", ArtworkPolicy.publicUrl(cover + "?token=fixture", source));
        assertEquals("", ArtworkPolicy.publicUrl(cover.replace("https://", "https://user@"), source));
    }
}
