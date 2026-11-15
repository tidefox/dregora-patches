package io.github.tidefox.dregorapatches.mixin.sme;

import com.shultrea.rin.enchantments.curses.EnchantmentCurseofPossession;
import io.github.tidefox.dregorapatches.PatchesConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * docs/10: Curse of Possession items vanishing when they are not a death drop.
 *
 * <p>SME's join handler puts every possessed {@code EntityItem} into the closest player's
 * inventory (8 blocks). When that fails it shortens the item's lifespan to 5 ticks
 * ({@code Curse Of Possession Death Deletion = true}, the pack default) or 10 s, whatever
 * the reason: a full main inventory after dragging cursed armour/offhand out of the window,
 * closing a GUI with it on the cursor, a mob or chest drop with nobody near. Here: not a
 * death drop → into the closest player's inventory or empty offhand, else a normal drop
 * (5 min). Death drop (closest player is dead) → the nearest living teammate within 8 blocks
 * with room (inventory, then offhand); solo or nobody has room → SME's deletion as before.
 */
@Mixin(value = EnchantmentCurseofPossession.class, remap = false)
public abstract class MixinCurseOfPossession {

    @Shadow public abstract boolean isEnabled();

    @Inject(method = "onEntityJoinWorldEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void dp$onJoin(EntityJoinWorldEvent event, CallbackInfo ci) {
        if (!isEnabled() || event.getWorld().isRemote) return;
        if (!(event.getEntity() instanceof EntityItem)) return;
        EntityItem item = (EntityItem) event.getEntity();
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || EnchantmentHelper.getEnchantmentLevel((Enchantment) (Object) this, stack) <= 0) return;
        EntityPlayer p = event.getWorld().getClosestPlayerToEntity(item, 8.0D);
        if (p != null && p.isCreative()) return; // SME does nothing either
        // Dead closest player = a death drop (the dead player is still in the world, at the drop).
        boolean death = p != null && !p.isEntityAlive();
        if (death ? !PatchesConfig.sme.possessionDeathToTeammate : !PatchesConfig.sme.possessionNoVoid) return;

        ItemStack left = stack.copy();
        if (death) {
            List<EntityPlayer> mates = new ArrayList<EntityPlayer>();
            for (EntityPlayer o : event.getWorld().playerEntities) {
                if (o != p && o.isEntityAlive() && !o.isSpectator() && o.getDistanceSq(item) <= 64.0D) mates.add(o);
            }
            mates.sort(Comparator.comparingDouble(o -> o.getDistanceSq(item)));
            for (EntityPlayer o : mates) {
                if (left.isEmpty()) break;
                dp$give(o, left);
            }
        } else if (p != null) {
            dp$give(p, left);
        }
        if (left.isEmpty()) {
            event.setCanceled(true);
            ci.cancel();
            return;
        }
        if (left.getCount() != stack.getCount()) item.setItem(left);
        // Death with no teammate who had room: SME deletes it (its default). Otherwise: a normal drop.
        if (!death) ci.cancel();
    }

    /** Main inventory, then an empty offhand. Shrinks {@code left} by what was taken. */
    @Unique
    private static void dp$give(EntityPlayer o, ItemStack left) {
        o.addItemStackToInventory(left);
        if (!left.isEmpty() && o.getHeldItemOffhand().isEmpty()) {
            o.setHeldItem(EnumHand.OFF_HAND, left.copy());
            left.setCount(0);
        }
    }
}
