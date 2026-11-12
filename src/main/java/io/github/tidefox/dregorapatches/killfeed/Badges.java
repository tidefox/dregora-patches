package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.trick.AnnouncedMobs;
import io.github.tidefox.dregorapatches.util.Chat;
import io.github.tidefox.dregorapatches.util.Fmt;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * Mod stats of a killed mob, read from vanilla NBT only (docs/03 §2.6, keys verified against the
 * pack's jars), shown as on the mods' own boss bars: Scaling Health {@code Blight}, Champions
 * {@code ★}×tier in the tier's color + the champion's name, InfernalMobs {@code ★}×rank (Rare,
 * Ultra, Infernal) in its rank color, Ice and Fire {@code Stage N}, SRParasites {@code Phase N}.
 * {@link #elite} mobs are notable and never collapsed into a streak.
 */
final class Badges {

    static final String INFERNAL_KEY = "InfernalMobsMod";
    static final String CHAMPION_CAP = "champions:championship";

    /** Badge pieces before the name, each in its own color. */
    final ITextComponent prefix;
    final boolean elite, blight, boss;
    final TextFormatting color;
    /** Lines for the mob's hover, without the name. */
    final List<String> details;

    private Badges(ITextComponent prefix, boolean elite, boolean blight, boolean boss, TextFormatting color,
            List<String> details) {
        this.prefix = prefix;
        this.elite = elite;
        this.blight = blight;
        this.boss = boss;
        this.color = color;
        this.details = details;
    }

    /** Serializes the entity once (writeToNBT): call per announced kill, not per hit. */
    static Badges of(EntityLivingBase e) {
        PatchesConfig.KillFeed c = PatchesConfig.killfeed;
        ResourceLocation id = EntityList.getKey(e);
        String rid = id == null ? "?" : id.toString();
        List<String> details = new ArrayList<>();
        details.add(rid + " · " + Fmt.num(e.getMaxHealth()) + " max HP");
        TextComponentString prefix = new TextComponentString("");
        boolean elite = false;

        boolean blight = e.getEntityData().getBoolean(AnnouncedMobs.BLIGHT_KEY);
        if (blight) {
            prefix.appendSibling(Chat.text("Blight ", TextFormatting.LIGHT_PURPLE));
        }

        NBTTagCompound nbt = e.writeToNBT(new NBTTagCompound());
        NBTTagCompound champ = nbt.getCompoundTag("ForgeCaps").getCompoundTag(CHAMPION_CAP);
        int tier = champ.getInteger("tier");
        if (tier > 0) {
            String name = champ.getString("name");
            prefix.appendSibling(Chat.text(stars(tier) + " " + (name.isEmpty() ? "" : name + " "), championColor(tier)));
            elite |= c.championsMinTier > 0 && tier >= c.championsMinTier;
            List<String> affixes = new ArrayList<>();
            NBTTagList list = champ.getTagList("affixes", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                affixes.add(list.getCompoundTagAt(i).getString("identifier"));
            }
            details.add("Champion tier " + tier + (affixes.isEmpty() ? "" : ": " + String.join(", ", affixes)));
        }

        String mods = e.getEntityData().getString(INFERNAL_KEY).trim();
        if (!mods.isEmpty()) {
            String[] m = mods.split(" +");
            int rank = infernalRank(m.length);
            prefix.appendSibling(Chat.text(stars(rank) + " ", infernalColor(rank)));
            elite |= c.infernalMinModifiers > 0 && m.length >= c.infernalMinModifiers;
            details.add(infernalName(rank) + " Infernal, " + m.length + " modifiers: " + String.join(", ", m));
        }

        if (Arrays.asList(c.dragonMobs).contains(rid)) {
            int days = nbt.getInteger("AgeTicks") / 24000;
            int stage = dragonStage(days);
            prefix.appendSibling(Chat.text("Stage " + stage + " ", TextFormatting.GOLD));
            elite |= c.dragonMinStage > 0 && stage >= c.dragonMinStage;
            details.add("Dragon stage " + stage + ", " + days + " days old");
        }

        if (id != null && "srparasites".equals(id.getNamespace()) && nbt.hasKey("phasecreat")) {
            int phase = nbt.getByte("phasecreat"); // evolution phase (0–8) the parasite was born in
            prefix.appendSibling(Chat.text("Phase " + phase + " ", TextFormatting.DARK_RED));
            elite |= c.srpMinPhase > 0 && phase >= c.srpMinPhase;
            details.add("Born in evolution phase " + phase + ", " + Fmt.num(nbt.getFloat("parasitekills")) + " kills");
        }

        boolean boss = !e.isNonBoss();
        TextFormatting color = boss ? TextFormatting.GOLD : elite ? TextFormatting.RED
                : blight ? TextFormatting.LIGHT_PURPLE : TextFormatting.WHITE;
        return new Badges(prefix, elite, blight, boss, color, details);
    }

    static String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, 10); i++) {
            sb.append('★');
        }
        return sb.toString();
    }

    /** Champions' bar tints stars and name with the rank color; pack ranks.json, nearest chat color. */
    static TextFormatting championColor(int tier) {
        switch (tier) {
            case 1: return TextFormatting.YELLOW;       // 0xFFFF00
            case 2: return TextFormatting.GOLD;         // 0xFF9900
            case 3: return TextFormatting.AQUA;         // 0x66FFFF
            case 4: return TextFormatting.LIGHT_PURPLE; // 0xCC33FF
            default: return TextFormatting.RED;         // 0xFF0000
        }
    }

    /** InfernalMobs' classes ({@code MobModifier.getEntityDisplayName}): 1 = Rare ≤ 5 mods, 2 = Ultra ≤ 10, 3 = Infernal. */
    static int infernalRank(int modifiers) {
        return modifiers <= 5 ? 1 : modifiers <= 10 ? 2 : 3;
    }

    static String infernalName(int rank) {
        return rank == 1 ? "Rare" : rank == 2 ? "Ultra" : "Infernal";
    }

    /** Infernal's bar colors the class word: Rare aqua, Ultra yellow, Infernal gold. */
    static TextFormatting infernalColor(int rank) {
        return rank == 1 ? TextFormatting.AQUA : rank == 2 ? TextFormatting.YELLOW : TextFormatting.GOLD;
    }

    /** Ice and Fire {@code EntityDragonBase.getDragonStage}: 1, then +1 every 25 days up to 5 at 100. */
    static int dragonStage(int days) {
        return days >= 100 ? 5 : days >= 75 ? 4 : days >= 50 ? 3 : days >= 25 ? 2 : 1;
    }
}
