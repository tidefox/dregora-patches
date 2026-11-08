package io.github.tidefox.dregorapatches.reactor;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** One claimed reactor (docs/07 §3 step 2). Mutable status fields are the last snapshot. */
public final class Claim {

    public final int id;
    public final int dim;
    public final BlockPos pos;
    public final UUID owner;
    public String ownerName;
    public final long created;
    public boolean keepLoaded;

    // last status snapshot (statusTime 0 = never seen loaded)
    public long statusTime;
    public boolean complete;
    public boolean processing;
    public String problem = "";
    public int heatPercent;
    public int energy;

    public Claim(int id, int dim, BlockPos pos, UUID owner, String ownerName, long created) {
        this.id = id;
        this.dim = dim;
        this.pos = pos;
        this.owner = owner;
        this.ownerName = ownerName;
        this.created = created;
    }

    NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id);
        t.setInteger("dim", dim);
        t.setLong("pos", pos.toLong());
        t.setUniqueId("owner", owner);
        t.setString("ownerName", ownerName == null ? "" : ownerName);
        t.setLong("created", created);
        t.setBoolean("keepLoaded", keepLoaded);
        t.setLong("statusTime", statusTime);
        t.setBoolean("complete", complete);
        t.setBoolean("processing", processing);
        t.setString("problem", problem == null ? "" : problem);
        t.setInteger("heatPercent", heatPercent);
        t.setInteger("energy", energy);
        return t;
    }

    static Claim read(NBTTagCompound t) {
        Claim c = new Claim(t.getInteger("id"), t.getInteger("dim"), BlockPos.fromLong(t.getLong("pos")),
                t.getUniqueId("owner"), t.getString("ownerName"), t.getLong("created"));
        c.keepLoaded = t.getBoolean("keepLoaded");
        c.statusTime = t.getLong("statusTime");
        c.complete = t.getBoolean("complete");
        c.processing = t.getBoolean("processing");
        c.problem = t.getString("problem");
        c.heatPercent = t.getInteger("heatPercent");
        c.energy = t.getInteger("energy");
        return c;
    }
}
