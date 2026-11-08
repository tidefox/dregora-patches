package io.github.tidefox.dregorapatches.util;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.DregoraPatches;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;

/**
 * Vanilla chat component helpers. Everything here is literal text plus vanilla
 * {@code SHOW_ITEM} / {@code SHOW_ENTITY} / {@code SHOW_TEXT} hovers, so clients without
 * this mod render it.
 */
public final class Chat {

    /** {@code SPacketChat} rejects (kicks the receiver) at 32767; keep a margin. */
    public static final int MAX_JSON = 30000;

    /** Above this, an item hover drops all NBT except display name and enchantments. */
    private static final int MAX_ITEM_NBT = 4000;

    private Chat() {}

    public static TextComponentString text(String s, TextFormatting color) {
        TextComponentString c = new TextComponentString(s);
        c.getStyle().setColor(color);
        return c;
    }

    /** Item as stored in NBT (vanilla {@code SHOW_ITEM} payload), trimmed if large. */
    public static String itemNbt(ItemStack stack) {
        NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
        String s = tag.toString();
        if (s.length() <= MAX_ITEM_NBT || !tag.hasKey("tag")) {
            return s;
        }
        NBTTagCompound full = tag.getCompoundTag("tag");
        NBTTagCompound slim = new NBTTagCompound();
        for (String key : new String[] {"display", "ench"}) {
            NBTBase b = full.getTag(key);
            if (b != null) {
                slim.setTag(key, b.copy());
            }
        }
        tag.setTag("tag", slim);
        s = tag.toString();
        if (s.length() > MAX_ITEM_NBT) {
            tag.removeTag("tag");
            s = tag.toString();
        }
        return s;
    }

    /** {@code [Name]} with a {@code SHOW_ITEM} hover built from a stored item NBT string. */
    public static ITextComponent item(String name, @Nullable String itemNbt, TextFormatting color) {
        TextComponentString c = text("[" + name + "]", color);
        if (itemNbt != null) {
            c.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new TextComponentString(itemNbt)));
        }
        return c;
    }

    public static ITextComponent item(ItemStack stack, TextFormatting color) {
        return item(stack.getDisplayName(), itemNbt(stack), color);
    }

    /** {@code SHOW_ENTITY} payload: {@code {name, type, id}} as vanilla's client reads it. */
    @Nullable
    public static String entityNbt(Entity e) {
        String type;
        if (e instanceof EntityPlayerMP) {
            type = "minecraft:player";
        } else {
            ResourceLocation id = EntityList.getKey(e);
            if (id == null) {
                return null;
            }
            type = id.toString();
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", e.getName());
        tag.setString("type", type);
        tag.setString("id", e.getCachedUniqueIdString());
        return tag.toString();
    }

    public static ITextComponent entity(String label, @Nullable String entityNbt, TextFormatting color) {
        TextComponentString c = text(label, color);
        if (entityNbt != null) {
            c.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new TextComponentString(entityNbt)));
        }
        return c;
    }

    public static ITextComponent hoverText(String label, String hover, TextFormatting color) {
        TextComponentString c = text(label, color);
        c.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(hover)));
        return c;
    }

    public static int jsonLength(ITextComponent c) {
        return ITextComponent.Serializer.componentToJson(c).length();
    }

    /** Returns {@code rich} if it fits the packet limit, else {@code plain} (logged). */
    public static ITextComponent fit(ITextComponent rich, ITextComponent plain) {
        int n = jsonLength(rich);
        if (n < MAX_JSON) {
            return rich;
        }
        DregoraPatches.LOGGER.warn("Chat component too large ({} chars), sending without hovers", n);
        return plain;
    }

    public static void send(EntityPlayerMP player, ITextComponent c) {
        player.sendMessage(c);
    }

    public static void broadcast(MinecraftServer server, ITextComponent c) {
        server.getPlayerList().sendMessage(c);
    }
}
