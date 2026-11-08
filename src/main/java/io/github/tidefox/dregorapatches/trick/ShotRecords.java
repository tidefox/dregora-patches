package io.github.tidefox.dregorapatches.trick;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/** {@link RecordBook} persisted in {@code world/data/dregorapatches_trickshots.dat} (overworld storage). */
public final class ShotRecords extends WorldSavedData {

    public static final String NAME = "dregorapatches_trickshots";

    public final RecordBook book = new RecordBook();

    public ShotRecords(String name) {
        super(name);
    }

    public static ShotRecords get(MinecraftServer server) {
        World overworld = server.getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        ShotRecords data = (ShotRecords) storage.getOrLoadData(ShotRecords.class, NAME);
        if (data == null) {
            data = new ShotRecords(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        book.restore(readList(nbt.getTagList("best", Constants.NBT.TAG_COMPOUND)),
                readList(nbt.getTagList("top", Constants.NBT.TAG_COMPOUND)));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("best", writeList(book.bests()));
        nbt.setTag("top", writeList(book.top()));
        return nbt;
    }

    private static List<RecordBook.Entry> readList(NBTTagList list) {
        List<RecordBook.Entry> out = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            out.add(new RecordBook.Entry(t.getString("uuid"), t.getString("player"), t.getDouble("distance"),
                    t.getString("mob"), t.getLong("date")));
        }
        return out;
    }

    private static NBTTagList writeList(List<RecordBook.Entry> entries) {
        NBTTagList list = new NBTTagList();
        for (RecordBook.Entry e : entries) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("uuid", e.uuid);
            t.setString("player", e.player);
            t.setDouble("distance", e.distance);
            t.setString("mob", e.mob);
            t.setLong("date", e.date);
            list.appendTag(t);
        }
        return list;
    }
}
