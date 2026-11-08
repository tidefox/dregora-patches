package io.github.tidefox.warband.recap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Per-player state: live ring buffer of recent hits, and the last death. In memory only
 * (a recap is about the death that just happened; a restart drops it).
 */
public final class RecapStore {

    /** Frozen at death. */
    public static final class Death {
        public final List<Hit> hits;
        public final long tick;
        public final String cause;
        public boolean delivered;
        public boolean shared;

        Death(List<Hit> hits, long tick, String cause) {
            this.hits = hits;
            this.tick = tick;
            this.cause = cause;
        }
    }

    private final Map<UUID, ArrayDeque<Hit>> live = new HashMap<>();
    private final Map<UUID, Death> deaths = new HashMap<>();

    public void add(UUID id, Hit h, int maxHits) {
        ArrayDeque<Hit> q = live.get(id);
        if (q == null) {
            q = new ArrayDeque<>();
            live.put(id, q);
        }
        q.addLast(h);
        while (q.size() > Math.max(1, maxHits)) {
            q.removeFirst();
        }
    }

    public void clearLive(UUID id) {
        live.remove(id);
    }

    public Death snapshot(UUID id, long tick, String cause) {
        ArrayDeque<Hit> q = live.remove(id);
        Death d = new Death(q == null ? new ArrayList<Hit>() : new ArrayList<>(q), tick, cause);
        deaths.put(id, d);
        return d;
    }

    @Nullable
    public Death lastDeath(UUID id) {
        return deaths.get(id);
    }

    public void clear() {
        live.clear();
        deaths.clear();
    }
}
