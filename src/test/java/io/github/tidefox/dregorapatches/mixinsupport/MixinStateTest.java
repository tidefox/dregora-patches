package io.github.tidefox.dregorapatches.mixinsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MixinStateTest {

    @BeforeEach
    void reset() {
        MixinState.reset();
    }

    @Test
    void recordsAppliedMixinsPerConfig() {
        MixinState.markApplied("mixins.dregorapatches.vanilla.json", MixinState.MIXIN_PACKAGE + "vanilla.MixinWorld");
        MixinState.markApplied("mixins.dregorapatches.nc.json", MixinState.MIXIN_PACKAGE + "nc.MixinNCTile");
        MixinState.markApplied("mixins.dregorapatches.nc.json", MixinState.MIXIN_PACKAGE + "nc.MixinNCTile");

        assertTrue(MixinState.isApplied("vanilla.MixinWorld"));
        assertTrue(MixinState.isApplied("nc.MixinNCTile"));
        assertFalse(MixinState.isApplied("levelup2.MixinFurnaceEfficiencyBonus"));

        Map<String, Set<String>> snap = MixinState.snapshot();
        assertEquals(2, snap.size());
        assertEquals(1, snap.get("mixins.dregorapatches.nc.json").size());
    }

    @Test
    void configLoadedAcceptsPackageWithOrWithoutTrailingDot() {
        MixinState.markLoaded(MixinState.MIXIN_PACKAGE + "nc");
        MixinState.markLoaded(MixinState.MIXIN_PACKAGE + "levelup2.");
        assertTrue(MixinState.isConfigLoaded("nc"));
        assertTrue(MixinState.isConfigLoaded("levelup2"));
        assertFalse(MixinState.isConfigLoaded("vanilla"));
    }
}
