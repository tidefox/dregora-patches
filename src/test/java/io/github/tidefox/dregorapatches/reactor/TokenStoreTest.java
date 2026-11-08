package io.github.tidefox.dregorapatches.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TokenStoreTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void tokenIsBoundToPlacerAndSingleUse() {
        TokenStore s = new TokenStore();
        String t = s.issue(alice, 0, 1, 2, 3, 1000);
        TokenStore.Pending[] out = new TokenStore.Pending[1];
        assertEquals(TokenStore.Result.NOT_YOURS, s.check(t, bob, 1000, out));
        assertEquals(TokenStore.Result.OK, s.check(t, alice, 2000, out));
        assertEquals(2, out[0].y);
        s.consume(t);
        assertEquals(TokenStore.Result.UNKNOWN, s.check(t, alice, 2000, out));
    }

    @Test
    void tokensExpire() {
        TokenStore s = new TokenStore();
        String t = s.issue(alice, 0, 0, 0, 0, 0);
        TokenStore.Pending[] out = new TokenStore.Pending[1];
        assertEquals(TokenStore.Result.OK, s.check(t, alice, TokenStore.TTL_MS, out));
        assertEquals(TokenStore.Result.EXPIRED, s.check(t, alice, TokenStore.TTL_MS + 1, out));
        assertEquals(TokenStore.Result.UNKNOWN, s.check(t, alice, TokenStore.TTL_MS + 1, out));
    }

    @Test
    void issuePurgesExpiredAndTokensDiffer() {
        TokenStore s = new TokenStore();
        String a = s.issue(alice, 0, 0, 0, 0, 0);
        String b = s.issue(alice, 0, 0, 0, 0, TokenStore.TTL_MS + 1);
        assertNotEquals(a, b);
        assertEquals(1, s.size());
    }
}
