package io.github.tidefox.dregorapatches.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * {@code world/data/dregorapatches_reactors.dat} (global MapStorage): player-placed controllers,
 * claims, keep-loaded flags, last status. Ignored by the game if our jar is removed.
 */
public final class ReactorClaimsData extends WorldSavedData {

    public static final String NAME = "dregorapatches_reactors";

    /** key: {@link #key(int, BlockPos)} → placer UUID */
    final Map<String, UUID> placed = new LinkedHashMap<String, UUID>();
    final List<Claim> claims = new ArrayList<Claim>();
    int nextId = 1;

    public ReactorClaimsData() {
        super(NAME);
    }

    public ReactorClaimsData(String name) {
        super(name);
    }

    static String key(int dim, BlockPos pos) {
        return dim + ":" + pos.toLong();
    }

    static int keyDim(String key) {
        return Integer.parseInt(key.substring(0, key.indexOf(':')));
    }

    static BlockPos keyPos(String key) {
        return BlockPos.fromLong(Long.parseLong(key.substring(key.indexOf(':') + 1)));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        placed.clear();
        claims.clear();
        nextId = Math.max(1, nbt.getInteger("nextId"));
        NBTTagList pl = nbt.getTagList("placed", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < pl.tagCount(); i++) {
            NBTTagCompound t = pl.getCompoundTagAt(i);
            placed.put(key(t.getInteger("dim"), BlockPos.fromLong(t.getLong("pos"))), t.getUniqueId("placer"));
        }
        NBTTagList cl = nbt.getTagList("claims", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cl.tagCount(); i++) {
            Claim c = Claim.read(cl.getCompoundTagAt(i));
            claims.add(c);
            nextId = Math.max(nextId, c.id + 1);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("nextId", nextId);
        NBTTagList pl = new NBTTagList();
        for (Map.Entry<String, UUID> e : placed.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("dim", keyDim(e.getKey()));
            t.setLong("pos", keyPos(e.getKey()).toLong());
            t.setUniqueId("placer", e.getValue());
            pl.appendTag(t);
        }
        nbt.setTag("placed", pl);
        NBTTagList cl = new NBTTagList();
        for (Claim c : claims) cl.appendTag(c.write());
        nbt.setTag("claims", cl);
        return nbt;
    }
}
