package dev.nuvio.collector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RankedSessionsTest {
    @Test public void pollsEachAllowedSessionOnceAndRanksItsStableSample() {
        List<String> sessions = List.of("paused", "excluded", "playing-low", "stopped", "playing-high", "missing");
        List<String> allow = List.of("paused", "playing-high", "playing-low", "stopped", "missing");
        Map<String, Integer> states = Map.of("paused", 1, "playing-low", 0, "stopped", -1, "playing-high", 0);
        Map<String, Integer> calls = new HashMap<>();
        var ranked = RankedSessions.read(sessions, allow, name -> name, name -> {
            int count = calls.merge(name, 1, Integer::sum);
            return count == 1 ? states.get(name) : null;
        }, state -> state);

        assertEquals(Map.of("paused", 1, "playing-low", 1, "playing-high", 1, "stopped", 1, "missing", 1), calls);
        assertEquals(3, ranked.size());
        assertEquals("playing-high", ranked.get(0).app);
        assertEquals("playing-low", ranked.get(1).app);
        assertEquals("paused", ranked.get(2).app);
        assertEquals(Integer.valueOf(0), ranked.get(0).playback);
    }
}
