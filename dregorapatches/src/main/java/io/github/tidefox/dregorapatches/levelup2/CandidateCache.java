package io.github.tidefox.dregorapatches.levelup2;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-class answer to "can this tile entity class carry Level Up! 2's MACHINE_PROCESSING
 * capability?". Seeded by the base classes the attach handlers check ({@code instanceof}),
 * extended by the learning scan for classes that turn out to carry it anyway.
 * Server thread only. Pure logic (no Minecraft types) so it can be unit tested.
 */
public final class CandidateCache {

    private final Class<?>[] bases;
    private final Map<Class<?>, Boolean> known = new IdentityHashMap<Class<?>, Boolean>();
    private int candidateClasses;
    private int learnedClasses;

    public CandidateCache(Class<?>... bases) {
        this.bases = bases.clone();
    }

    public boolean isCandidate(Class<?> c) {
        Boolean b = known.get(c);
        if (b == null) {
            boolean v = false;
            for (Class<?> base : bases) {
                if (base.isAssignableFrom(c)) {
                    v = true;
                    break;
                }
            }
            known.put(c, v);
            if (v) candidateClasses++;
            return v;
        }
        return b;
    }

    /** A class seen with the capability. Returns true if it was not a candidate before. */
    public boolean learn(Class<?> c) {
        if (isCandidate(c)) return false;
        known.put(c, Boolean.TRUE);
        candidateClasses++;
        learnedClasses++;
        return true;
    }

    public int candidateClasses() {
        return candidateClasses;
    }

    public int learnedClasses() {
        return learnedClasses;
    }
}
