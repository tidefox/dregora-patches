package io.github.tidefox.warband.recap;

import java.util.HashMap;
import java.util.Map;

import io.github.tidefox.warband.core.Fmt;

/** Environment labels for damage with no entity behind it. Pure, unit-tested. */
public final class DamageNames {

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        NAMES.put("inFire", "Fire");
        NAMES.put("onFire", "Burning");
        NAMES.put("lightningBolt", "Lightning");
        NAMES.put("lava", "Lava");
        NAMES.put("hotFloor", "Magma block");
        NAMES.put("inWall", "Suffocation");
        NAMES.put("cramming", "Cramming");
        NAMES.put("drown", "Drowning");
        NAMES.put("starve", "Starvation");
        NAMES.put("cactus", "Cactus");
        NAMES.put("fall", "Fall");
        NAMES.put("flyIntoWall", "Flew into a wall");
        NAMES.put("outOfWorld", "Void");
        NAMES.put("generic", "Generic");
        NAMES.put("magic", "Magic");
        NAMES.put("wither", "Wither effect");
        NAMES.put("anvil", "Falling anvil");
        NAMES.put("fallingBlock", "Falling block");
        NAMES.put("dragonBreath", "Dragon breath");
        NAMES.put("fireworks", "Fireworks");
        NAMES.put("thorns", "Thorns");
        NAMES.put("explosion", "Explosion");
        NAMES.put("explosion.player", "Explosion");
    }

    private DamageNames() {}

    /**
     * @param poisoned victim has Poison active: vanilla poison ticks are plain {@code magic}
     *        damage, so this is the only way to tell them apart
     */
    public static String environment(String damageType, boolean poisoned) {
        if ("magic".equals(damageType) && poisoned) {
            return "Poison";
        }
        String n = NAMES.get(damageType);
        return n != null ? n : Fmt.humanize(damageType);
    }
}
