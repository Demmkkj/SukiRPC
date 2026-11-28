package dev.nuvio.quality.microbenchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dev.nuvio.artwork.ArtworkPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class ArtworkBenchmark {
    @Rule public final BenchmarkRule benchmark = new BenchmarkRule();
    // Prevent dead-code elimination without including an assertion in the timed section.
    private String resolved;

    @Test public void deriveVideoThumbnail() {
        BenchmarkState state = benchmark.getState();
        while (state.keepRunning()) {
            resolved = ArtworkPolicy.fromMediaId("https://youtu.be/jNQXAC9IVRw?si=tracking", "com.google.android.youtube");
        }
        assertEquals("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg", resolved);
    }
}
