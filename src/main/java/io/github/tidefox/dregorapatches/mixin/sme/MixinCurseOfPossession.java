package io.github.tidefox.dregorapatches.mixin.sme;

import com.shultrea.rin.enchantments.curses.EnchantmentCurseofPossession;
import io.github.tidefox.dregorapatches.PatchesConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 * closing a GUI with it on the cursor, a mob or chest drop with nobody near. Here: only a
 * death drop (closest player is dead) keeps SME's deletion; otherwise the item stays a
 * normal drop (5 min, pickable as soon as there's room).
 */
@Mixin(value = EnchantmentCurseofPossession.class, remap = false)
public abstract class MixinCurseOfPossession {

    @Shadow public abstract boolean isEnabled();

    @Inject(method = "onEntityJoinWorldEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void dp$onJoin(EntityJoinWorldEvent event, CallbackInfo ci) {
        if (!PatchesConfig.sme.possessionNoVoid || !isEnabled() || event.getWorld().isRemote) return;
        if (!(event.getEntity() instanceof EntityItem)) return;
        EntityItem item = (EntityItem) event.getEntity();
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || EnchantmentHelper.getEnchantmentLevel((Enchantment) (Object) this, stack) <= 0) return;
        EntityPlayer p = event.getWorld().getClosestPlayerToEntity(item, 8.0D);
        // Creative: SME does nothing. Dead: a death drop, SME's (configured) deletion.
        if (p != null && (p.isCreative() || !p.isEntityAlive())) return;
        ci.cancel();
        ItemStack left = stack.copy();
        if (p != null) p.addItemStackToInventory(left); // shrinks `left` by what fit
        if (left.isEmpty()) event.setCanceled(true);
        else if (left.getCount() != stack.getCount()) item.setItem(left);
    }
}
