package io.github.tidefox.warband.core;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * The {@code warband:shot} compound written on player-fired arrows by {@link ProjectileOrigin}
 * (docs/06 §2.1). Lives in Forge's persistent entity data, so it survives chunk unload/reload
 * and restarts.
 */
public final class ShotTag {

    public static final String KEY = "warband:shot";

    public final double x, y, z;
    /** {@code World#getTotalWorldTime()} at spawn (shared by all dimensions). */
    public final long tick;
    public final String shooter;
    public final ItemStack launcher;
    public final boolean airborne;
    public final boolean mounted;
    public final String mount;

    public ShotTag(double x, double y, double z, long tick, String shooter, ItemStack launcher,
            boolean airborne, boolean mounted, String mount) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.tick = tick;
        this.shooter = shooter;
        this.launcher = launcher;
        this.airborne = airborne;
        this.mounted = mounted;
        this.mount = mount;
    }

    public static boolean has(Entity e) {
        return e.getEntityData().hasKey(KEY, Constants.NBT.TAG_COMPOUND);
    }

    @Nullable
    public static ShotTag read(@Nullable Entity e) {
        if (e == null || !has(e)) {
            return null;
        }
        NBTTagCompound t = e.getEntityData().getCompoundTag(KEY);
        ItemStack launcher = t.hasKey("launcher", Constants.NBT.TAG_COMPOUND)
                ? new ItemStack(t.getCompoundTag("launcher")) : ItemStack.EMPTY;
        return new ShotTag(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getLong("tick"),
                t.getString("shooter"), launcher, t.getBoolean("airborne"), t.getBoolean("mounted"),
                t.getString("mount"));
    }

    public void write(Entity e) {
        NBTTagCompound t = new NBTTagCompound();
        t.setDouble("x", x);
        t.setDouble("y", y);
        t.setDouble("z", z);
        t.setLong("tick", tick);
        t.setString("shooter", shooter);
        if (!launcher.isEmpty()) {
            t.setTag("launcher", launcher.writeToNBT(new NBTTagCompound()));
        }
        t.setBoolean("airborne", airborne);
        t.setBoolean("mounted", mounted);
        if (!mount.isEmpty()) {
            t.setString("mount", mount);
        }
        e.getEntityData().setTag(KEY, t);
    }

    public double distanceTo(Entity e) {
        double dx = e.posX - x, dy = e.posY - y, dz = e.posZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
