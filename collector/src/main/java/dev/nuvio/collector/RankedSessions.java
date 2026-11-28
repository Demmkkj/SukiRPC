package dev.nuvio.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

final class RankedSessions {
    private RankedSessions() {}

    static final class Sample<S, P> {
        final S session;
        final String app;
        final P playback;
        final int priority;
        final int providerOrder;

        Sample(S session, String app, P playback, int priority, int providerOrder) {
            this.session = session;
            this.app = app;
            this.playback = playback;
            this.priority = priority;
            this.providerOrder = providerOrder;
        }
    }

    static <S, P> List<Sample<S, P>> read(
        List<S> sessions, List<String> allow, Function<S, String> packageName,
        Function<S, P> playback, ToIntFunction<P> priority
    ) {
        List<Sample<S, P>> candidates = new ArrayList<>();
        for (S session : sessions) {
            String app = packageName.apply(session);
            int providerOrder = allow.indexOf(app);
            if (providerOrder < 0) continue;
            P state = playback.apply(session);
            if (state == null) continue;
            int rank = priority.applyAsInt(state);
            if (rank >= 0) candidates.add(new Sample<>(session, app, state, rank, providerOrder));
        }
        candidates.sort(Comparator.<Sample<S, P>>comparingInt(sample -> sample.priority)
            .thenComparingInt(sample -> sample.providerOrder));
        return candidates;
    }
}
