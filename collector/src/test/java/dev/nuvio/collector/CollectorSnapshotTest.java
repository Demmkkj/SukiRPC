package dev.nuvio.collector;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CollectorSnapshotTest {
    @Test public void readingNewSampleDoesNotRefreshAnEarlierCapturedSample() {
        var earlier = new LocalServer.Snapshot("first", 1000);
        var latest = new LocalServer.Snapshot("second", 8000);
        assertEquals("first", earlier.json);
        assertTrue(earlier.age(8000) > 6000);
        assertEquals("second", latest.json);
        assertEquals(0, latest.age(8000));
        assertTrue(new LocalServer.Snapshot("", -1).age(8000) > 6000);
    }
}
