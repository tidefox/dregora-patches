package io.github.tidefox.dregorapatches.nc;

/**
 * Duck interface added to {@code nc.tile.NCTile} by {@code mixin.nc.MixinNCTile}.
 * Lives outside the mixin package so vanilla mixins and our code can reference it.
 */
public interface DpNcTile {

    /**
     * True if this tile entity must not tick: a frozen type (docs/07 §3 step 2) outside every
     * claim box, or a just-claimed part whose staggered first tick is still ahead.
     * Cached; re-evaluated only when {@link io.github.tidefox.dregorapatches.reactor.ReactorClaims#epoch()} changes.
     */
    boolean dp$isFrozen();

    /** True if this tile entity is one of the frozen types (controller, port, active cooler). */
    boolean dp$isFrozenType();
}
