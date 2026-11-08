package io.github.tidefox.warband.trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/** Per-player bests + server top list (docs/06 §2.5). Pure, unit-tested; persisted by {@link ShotRecords}. */
public final class RecordBook {

    public static final int TOP = 10;

    public static final class Entry {
        public final String uuid;
        public final String player;
        public final double distance;
        public final String mob;
        /** Epoch millis. */
        public final long date;

        public Entry(String uuid, String player, double distance, String mob, long date) {
            this.uuid = uuid;
            this.player = player;
            this.distance = distance;
            this.mob = mob;
            this.date = date;
        }
    }

    /** What a new shot beat. */
    public static final class Result {
        /** Previous personal best, null if this is the first. */
        @Nullable
        public final Entry previousBest;
        public final boolean personalBest;
        /** Previous server record (top entry), null if none. */
        @Nullable
        public final Entry previousRecord;
        public final boolean serverRecord;
        /** 1-based rank in the top list, 0 if outside it. */
        public final int rank;

        Result(@Nullable Entry previousBest, boolean personalBest, @Nullable Entry previousRecord,
                boolean serverRecord, int rank) {
            this.previousBest = previousBest;
            this.personalBest = personalBest;
            this.previousRecord = previousRecord;
            this.serverRecord = serverRecord;
            this.rank = rank;
        }
    }

    private final Map<String, Entry> best = new HashMap<>();
    /** Every shot that made the list, best first; a player may appear more than once. */
    private final List<Entry> top = new ArrayList<>();

    public Result submit(Entry e) {
        Entry prevBest = best.get(e.uuid);
        boolean pb = prevBest == null || e.distance > prevBest.distance;
        if (pb) {
            best.put(e.uuid, e);
        }
        Entry prevRecord = top.isEmpty() ? null : top.get(0);
        boolean record = prevRecord == null || e.distance > prevRecord.distance;
        int rank = 0;
        int i = 0;
        while (i < top.size() && top.get(i).distance >= e.distance) {
            i++;
        }
        if (i < TOP) {
            top.add(i, e);
            rank = i + 1;
            while (top.size() > TOP) {
                top.remove(top.size() - 1);
            }
        }
        return new Result(prevBest, pb, prevRecord, record, rank);
    }

    @Nullable
    public Entry bestOf(String uuid) {
        return best.get(uuid);
    }

    /** Case-insensitive by last known name. */
    @Nullable
    public Entry bestByName(String name) {
        for (Entry e : best.values()) {
            if (e.player.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    public List<Entry> top() {
        return Collections.unmodifiableList(top);
    }

    public List<Entry> bests() {
        return new ArrayList<>(best.values());
    }

    /** Loading only: put back persisted state without ranking logic. */
    public void restore(List<Entry> bests, List<Entry> topList) {
        best.clear();
        for (Entry e : bests) {
            best.put(e.uuid, e);
        }
        top.clear();
        top.addAll(topList);
        Collections.sort(top, (a, b) -> Double.compare(b.distance, a.distance));
        while (top.size() > TOP) {
            top.remove(top.size() - 1);
        }
    }
}
