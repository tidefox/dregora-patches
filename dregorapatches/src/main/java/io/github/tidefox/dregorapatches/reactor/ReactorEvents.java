package io.github.tidefox.dregorapatches.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.nc.FrozenTypes;
import io.github.tidefox.dregorapatches.nc.NcCompat;
import io.github.tidefox.dregorapatches.nc.NcScope;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Controller place → token + chat prompt; controller break → end claim; periodic status
 * snapshots and the lazy "controller gone" check (docs/07 §3 step 2). Never loads a chunk.
 */
public final class ReactorEvents {

    public static final String PREFIX = TextFormatting.GOLD + "[Reactor] " + TextFormatting.RESET;
    private static final int STATUS_INTERVAL = 100;

    private final List<Object[]> pendingChecks = new ArrayList<Object[]>();
    private int ticks;

    static boolean isControllerBlock(IBlockState state) {
        ResourceLocation id = state.getBlock().getRegistryName();
        return id != null && "nuclearcraft".equals(id.getNamespace()) && id.getPath().startsWith("fission_controller");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldLoad(WorldEvent.Load e) {
        World w = e.getWorld();
        if (w.isRemote) return;
        ReactorClaims.load(w);
        ReactorChunkLoader.onWorldLoad(w);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        World w = e.getWorld();
        if (w.isRemote || !(e.getEntity() instanceof EntityPlayerMP) || e.getEntity() instanceof FakePlayer) return;
        if (!isControllerBlock(e.getPlacedBlock())) return;
        EntityPlayerMP player = (EntityPlayerMP) e.getEntity();
        ReactorClaims.load(w);
        int dim = w.provider.getDimension();
        BlockPos pos = e.getPos().toImmutable();
        ReactorClaims.recordPlaced(dim, pos, player.getUniqueID());
        String token = ReactorClaims.TOKENS.issue(player.getUniqueID(), dim, pos.getX(), pos.getY(), pos.getZ(),
                System.currentTimeMillis());
        player.sendMessage(placedPrompt(pos, token));
    }

    static ITextComponent placedPrompt(BlockPos pos, String token) {
        TextComponentString msg = new TextComponentString(PREFIX + "Fission controller placed near "
                + CoordFormat.estimated(pos.getX(), pos.getZ(), PatchesConfig.reactor.coordEstimate) + ". ");
        TextComponentString link = new TextComponentString("[Claim this reactor]");
        link.setStyle(new Style().setColor(TextFormatting.GREEN).setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reactor claim " + token))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(
                        "Reactor parts near this controller only run once it is claimed. Valid for 5 minutes."))));
        msg.appendSibling(link);
        return msg;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent e) {
        World w = e.getWorld();
        if (w.isRemote) return;
        int dim = w.provider.getDimension();
        BlockPos pos = e.getPos();
        if (ReactorClaims.at(dim, pos) != null || ReactorClaims.placer(dim, pos) != null) {
            // Checked at the end of the tick: another mod may still cancel the break.
            pendingChecks.add(new Object[] {dim, pos.toImmutable()});
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            NcScope.reset();
            return;
        }
        if (!pendingChecks.isEmpty()) {
            for (Object[] p : pendingChecks) checkPresent((Integer) p[0], (BlockPos) p[1], "the controller was broken");
            pendingChecks.clear();
        }
        if (++ticks % STATUS_INTERVAL != 0 || !ReactorClaims.isLoaded()) return;
        for (Claim c : ReactorClaims.all()) {
            World w = DimensionManager.getWorld(c.dim);
            if (w == null || !w.isBlockLoaded(c.pos)) continue;
            TileEntity te = w.getTileEntity(c.pos);
            if (!NcCompat.snapshot(te, c)) {
                end(c, "the controller is gone");
            }
        }
        ReactorClaims.markDirty();
        for (Object[] p : ReactorClaims.placedRecords()) {
            checkPresent((Integer) p[0], (BlockPos) p[1], "the controller is gone");
        }
    }

    /** If the chunk is loaded and no controller tile entity is at pos: drop the placed record and end any claim. */
    private static void checkPresent(int dim, BlockPos pos, String reason) {
        World w = DimensionManager.getWorld(dim);
        if (w == null || !w.isBlockLoaded(pos)) return;
        if (FrozenTypes.isController(w.getTileEntity(pos))) return;
        ReactorClaims.removePlaced(dim, pos);
        Claim c = ReactorClaims.at(dim, pos);
        if (c != null) end(c, reason);
    }

    /** Ends a claim, releases its ticket and tells the owner if online. */
    public static void end(Claim c, String reason) {
        if (!ReactorClaims.remove(c)) return;
        ReactorChunkLoader.disable(c);
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        UUID owner = c.owner;
        EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(owner);
        if (p != null) {
            p.sendMessage(new TextComponentString(PREFIX + "Your reactor #" + c.id + " is no longer claimed: " + reason + "."));
        }
    }
}
