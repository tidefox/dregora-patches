package io.github.tidefox.warband.trick;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import io.github.tidefox.warband.Warband;
import io.github.tidefox.warband.core.WarbandConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * "Is this mob announce-worthy?" from vanilla data only (docs/06 §2.2): boss flag, custom name,
 * registry whitelist, NBT markers. No other mod's classes.
 */
public final class AnnouncedMobs {

    public enum Reason { BOSS, NAMED, WHITELIST, NBT_MARKER }

    private static String[] mobsSrc, markersSrc;
    private static Set<String> mobs = new HashSet<>();
    private static List<NbtMarker> markers = NbtMarker.parseAll(new String[0]);

    /** Per-entity verdicts for per-hit callers (03); entries vanish with the entity. */
    private static final Map<Entity, Reason> CACHE = new WeakHashMap<>();

    private AnnouncedMobs() {}

    private static void refreshConfig() {
        String[] m = WarbandConfig.trickshots.mobs;
        if (m != mobsSrc) {
            mobsSrc = m;
            mobs = new HashSet<>(Arrays.asList(m));
        }
        String[] n = WarbandConfig.trickshots.nbtMarkers;
        if (n != markersSrc) {
            markersSrc = n;
            markers = NbtMarker.parseAll(n);
        }
    }

    /** Uncached. Rules 1–3 are cheap; rule 4 serializes the entity, so it runs last. */
    @Nullable
    public static Reason evaluate(EntityLivingBase e) {
        if (e instanceof EntityPlayer) {
            return null;
        }
        refreshConfig();
        if (!e.isNonBoss()) {
            return Reason.BOSS;
        }
        if (e.hasCustomName()) {
            return Reason.NAMED;
        }
        ResourceLocation id = EntityList.getKey(e);
        if (id != null && mobs.contains(id.toString())) {
            return Reason.WHITELIST;
        }
        if (!markers.isEmpty()) {
            NBTTagCompound nbt = serialize(e);
            if (nbt != null) {
                for (NbtMarker mk : markers) {
                    if (mk.matches(nbt)) {
                        return Reason.NBT_MARKER;
                    }
                }
            }
        }
        return null;
    }

    /** Cached per entity (at most one NBT read per mob); for per-hit callers. */
    @Nullable
    public static Reason cached(EntityLivingBase e) {
        if (CACHE.containsKey(e)) {
            return CACHE.get(e);
        }
        Reason r = evaluate(e);
        CACHE.put(e, r); // null = not announced, cached too
        return r;
    }

    public static void forget(Entity e) {
        CACHE.remove(e);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Full entity NBT, including ForgeData and ForgeCaps. Null if a mod's writer throws. */
    @Nullable
    public static NBTTagCompound serialize(Entity e) {
        try {
            return e.writeToNBT(new NBTTagCompound());
        } catch (RuntimeException ex) {
            Warband.LOGGER.warn("writeToNBT failed for {}: {}", e.getClass().getName(), ex.toString());
            return null;
        }
    }
}
