package io.github.tidefox.dregorapatches.trick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class NbtMarkerTest {

    private static NBTTagCompound mob() {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("Variant", "boss");
        NBTTagCompound data = new NBTTagCompound();
        data.setByte("IsBlight", (byte) 1);
        root.setTag("ForgeData", data);
        NBTTagCompound caps = new NBTTagCompound();
        NBTTagCompound champ = new NBTTagCompound();
        champ.setInteger("tier", 0);
        caps.setTag("champions:championship", champ);
        root.setTag("ForgeCaps", caps);
        return root;
    }

    @Test
    void keyAnywhere() {
        assertTrue(NbtMarker.parse("IsBlight").matches(mob()));
        assertTrue(NbtMarker.parse("champions:championship").matches(mob()));
        assertFalse(NbtMarker.parse("Missing").matches(mob()));
    }

    @Test
    void pathsAndValues() {
        assertTrue(NbtMarker.parse("ForgeData/IsBlight=1").matches(mob()));
        assertTrue(NbtMarker.parse("IsBlight=1").matches(mob()));
        assertFalse(NbtMarker.parse("IsBlight=0").matches(mob()));
        assertTrue(NbtMarker.parse("Variant=boss").matches(mob()));
        assertFalse(NbtMarker.parse("champions:championship/tier!=0").matches(mob()));
        assertTrue(NbtMarker.parse("champions:championship/tier=0").matches(mob()));
        NBTTagCompound tiered = mob();
        tiered.getCompoundTag("ForgeCaps").getCompoundTag("champions:championship").setInteger("tier", 3);
        assertTrue(NbtMarker.parse("ForgeCaps/champions:championship/tier!=0").matches(tiered));
    }

    @Test
    void blankIsNull() {
        assertNull(NbtMarker.parse("  "));
        assertNull(NbtMarker.parse(null));
        assertNull(NbtMarker.parse("//"));
    }
}
