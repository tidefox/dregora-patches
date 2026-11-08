package io.github.tidefox.warband.trick;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;

/**
 * One {@code trickshots.nbtMarkers} entry (docs/06 §2.2 rule 4): {@code key}, {@code a/b/c},
 * optionally {@code =value} or {@code !=value}. The path is tried from the entity's root
 * compound, from {@code ForgeData} and from {@code ForgeCaps}. Read-only.
 */
public final class NbtMarker {

    private enum Op { EXISTS, EQ, NE }

    private final String[] path;
    private final Op op;
    private final String value;

    private NbtMarker(String[] path, Op op, String value) {
        this.path = path;
        this.op = op;
        this.value = value;
    }

    @Nullable
    public static NbtMarker parse(@Nullable String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return null;
        }
        String s = entry.trim();
        Op op = Op.EXISTS;
        String value = "";
        int ne = s.indexOf("!=");
        int eq = s.indexOf('=');
        if (ne > 0) {
            op = Op.NE;
            value = s.substring(ne + 2).trim();
            s = s.substring(0, ne).trim();
        } else if (eq > 0) {
            op = Op.EQ;
            value = s.substring(eq + 1).trim();
            s = s.substring(0, eq).trim();
        }
        List<String> parts = new ArrayList<>();
        for (String p : s.split("/")) {
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        return parts.isEmpty() ? null : new NbtMarker(parts.toArray(new String[0]), op, value);
    }

    public static List<NbtMarker> parseAll(String[] entries) {
        List<NbtMarker> out = new ArrayList<>();
        for (String e : entries) {
            NbtMarker m = parse(e);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    /** @param root the entity's full NBT ({@code writeToNBT}) */
    public boolean matches(NBTTagCompound root) {
        return test(root) || test(root.getCompoundTag("ForgeData")) || test(root.getCompoundTag("ForgeCaps"));
    }

    private boolean test(NBTTagCompound from) {
        NBTBase cur = from;
        for (String key : path) {
            if (!(cur instanceof NBTTagCompound) || !((NBTTagCompound) cur).hasKey(key)) {
                return false;
            }
            cur = ((NBTTagCompound) cur).getTag(key);
        }
        switch (op) {
            case EQ:
                return valueEquals(cur, value);
            case NE:
                return !valueEquals(cur, value);
            default:
                return true;
        }
    }

    /** Numbers compare numerically ({@code 1b} = {@code 1}), strings without quotes. */
    static boolean valueEquals(NBTBase tag, String want) {
        if (tag instanceof NBTTagString) {
            return ((NBTTagString) tag).getString().equals(want);
        }
        if (tag instanceof NBTPrimitive) {
            try {
                return ((NBTPrimitive) tag).getDouble() == Double.parseDouble(want);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return tag.toString().equals(want);
    }
}
