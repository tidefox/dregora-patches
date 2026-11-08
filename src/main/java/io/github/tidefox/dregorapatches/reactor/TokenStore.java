package io.github.tidefox.dregorapatches.reactor;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Single-use claim tokens handed out in the "controller placed" chat prompt.
 * Bound to the placer's UUID, expire after {@link #TTL_MS}. In memory only. Pure logic.
 */
public final class TokenStore {

    public static final long TTL_MS = 5 * 60 * 1000L;

    public static final class Pending {
        public final UUID placer;
        public final int dim;
        public final int x, y, z;
        final long expires;

        Pending(UUID placer, int dim, int x, int y, int z, long expires) {
            this.placer = placer;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expires = expires;
        }
    }

    public enum Result { OK, UNKNOWN, EXPIRED, NOT_YOURS }

    private final Map<String, Pending> tokens = new HashMap<String, Pending>();
    private final SecureRandom random = new SecureRandom();

    public String issue(UUID placer, int dim, int x, int y, int z, long now) {
        purge(now);
        String token;
        do {
            token = Long.toHexString(random.nextLong() & 0xffffffffffL);
        } while (tokens.containsKey(token));
        tokens.put(token, new Pending(placer, dim, x, y, z, now + TTL_MS));
        return token;
    }

    /** Looks a token up for {@code player}. Only an {@link Result#OK} lookup is returned in {@code out[0]}; it is not consumed yet. */
    public Result check(String token, UUID player, long now, Pending[] out) {
        Pending p = tokens.get(token);
        if (p == null) return Result.UNKNOWN;
        if (now > p.expires) {
            tokens.remove(token);
            return Result.EXPIRED;
        }
        if (!p.placer.equals(player)) return Result.NOT_YOURS;
        out[0] = p;
        return Result.OK;
    }

    public void consume(String token) {
        tokens.remove(token);
    }

    public void clear() {
        tokens.clear();
    }

    int size() {
        return tokens.size();
    }

    private void purge(long now) {
        for (Iterator<Pending> it = tokens.values().iterator(); it.hasNext(); ) {
            if (now > it.next().expires) it.remove();
        }
    }
}
