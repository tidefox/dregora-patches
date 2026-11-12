package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.trick.AnnouncedMobs;
import io.github.tidefox.dregorapatches.util.Fmt;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

/**
 * Mod stats of a killed mob, read from vanilla NBT only (docs/03 §2.6, keys verified against the
 * pack's jars): Scaling Health Blight, Champions tier, InfernalMobs rank, Ice and Fire dragon stage.
 * Shown as a name prefix ({@code Blight ★★★ Defiled Slime}) plus a hover; {@link #elite} mobs are
 * notable and never collapsed into a streak.
 */
final class Badges {

    static final String INFERNAL_KEY = "InfernalMobsMod";
    static final String CHAMPION_CAP = "champions:championship";

    final String prefix;
    final boolean elite, blight, boss;
    final TextFormatting color;
    /** Lines for the mob's hover, without the name. */
    final List<String> details;

    private Badges(String prefix, boolean elite, boolean blight, boolean boss, TextFormatting color, List<String> details) {
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
        StringBuilder prefix = new StringBuilder();
        boolean elite = false;

        boolean blight = e.getEntityData().getBoolean(AnnouncedMobs.BLIGHT_KEY);
        if (blight) {
            prefix.append("Blight ");
        }

        NBTTagCompound nbt = e.writeToNBT(new NBTTagCompound());
        NBTTagCompound champ = nbt.getCompoundTag("ForgeCaps").getCompoundTag(CHAMPION_CAP);
        int tier = champ.getInteger("tier");
        if (tier > 0) {
            prefix.append(stars(tier)).append(' ');
            elite |= c.championsMinTier > 0 && tier >= c.championsMinTier;
            List<String> affixes = new ArrayList<>();
            NBTTagList list = champ.getTagList("affixes", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                affixes.add(list.getCompoundTagAt(i).getString("identifier"));
            }
            String name = champ.getString("name");
            details.add("Champion tier " + tier + (name.isEmpty() ? "" : " \"" + name + "\"")
                    + (affixes.isEmpty() ? "" : ": " + String.join(", ", affixes)));
        }

        String mods = e.getEntityData().getString(INFERNAL_KEY).trim();
        if (!mods.isEmpty()) {
            String[] m = mods.split(" +");
            prefix.append(infernalRank(m.length)).append(' ');
            elite |= c.infernalMinModifiers > 0 && m.length >= c.infernalMinModifiers;
            details.add(infernalRank(m.length) + " Infernal, " + m.length + " modifiers: " + String.join(", ", m));
        }

        if (Arrays.asList(c.dragonMobs).contains(rid)) {
            int days = nbt.getInteger("AgeTicks") / 24000;
            int stage = dragonStage(days);
            prefix.append("Stage ").append(stage).append(' ');
            elite |= c.dragonMinStage > 0 && stage >= c.dragonMinStage;
            details.add("Dragon stage " + stage + ", " + days + " days old");
        }

        boolean boss = !e.isNonBoss();
        TextFormatting color = boss ? TextFormatting.GOLD : elite ? TextFormatting.RED
                : blight ? TextFormatting.LIGHT_PURPLE : TextFormatting.WHITE;
        return new Badges(prefix.toString(), elite, blight, boss, color, details);
    }

    static String stars(int tier) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(tier, 10); i++) {
            sb.append('★');
        }
        return sb.toString();
    }

    /** InfernalMobs' own classes ({@code MobModifier.getEntityDisplayName}): ≤ 5 Rare, ≤ 10 Ultra, else Infernal. */
    static String infernalRank(int modifiers) {
        return modifiers <= 5 ? "Rare" : modifiers <= 10 ? "Ultra" : "Infernal";
    }

    /** Ice and Fire {@code EntityDragonBase.getDragonStage}: 1, then +1 every 25 days up to 5 at 100. */
    static int dragonStage(int days) {
        return days >= 100 ? 5 : days >= 75 ? 4 : days >= 50 ? 3 : days >= 25 ? 2 : 1;
    }
}
