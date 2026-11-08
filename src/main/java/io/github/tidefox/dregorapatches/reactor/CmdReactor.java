package io.github.tidefox.dregorapatches.reactor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import io.github.tidefox.dregorapatches.PatchesConfig;
import io.github.tidefox.dregorapatches.nc.FrozenTypes;
import io.github.tidefox.dregorapatches.nc.NcCompat;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * {@code /reactor} (docs/07 §3 "Commands"). Everyone: list, claim, unclaim own.
 * Ops: {@code -exact}, {@code admin list|unclaim|keeploaded}. All text literal.
 */
public class CmdReactor extends CommandBase {

    private static final String USAGE = "/reactor list | claim [token] | unclaim <id>"
            + " | admin list [player] [-exact] | admin unclaim <id> | admin keeploaded <id> on|off";
    private static final double REACH = 5.0;

    @Override
    public String getName() {
        return "reactor";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    private static boolean isOp(ICommandSender sender) {
        return sender.canUseCommand(2, "reactor");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!ReactorClaims.isLoaded()) ReactorClaims.load(server.getEntityWorld());
        if (args.length == 0) throw new WrongUsageException(USAGE);
        List<String> rest = new ArrayList<String>(Arrays.asList(args).subList(1, args.length));
        boolean exact = rest.remove("-exact");
        if (exact && !isOp(sender)) {
            send(sender, TextFormatting.RED + "-exact is for operators only.");
            return;
        }
        switch (args[0]) {
            case "list":
                if (!rest.isEmpty()) throw new WrongUsageException(USAGE);
                list(sender, ownedBy(sender), exact, false);
                return;
            case "claim":
                if (rest.size() > 1) throw new WrongUsageException(USAGE);
                claim(sender, rest.isEmpty() ? null : rest.get(0));
                return;
            case "unclaim":
                if (rest.size() != 1) throw new WrongUsageException(USAGE);
                unclaim(sender, parseInt(rest.get(0)), false);
                return;
            case "admin":
                if (!isOp(sender)) {
                    send(sender, TextFormatting.RED + "/reactor admin is for operators only.");
                    return;
                }
                admin(server, sender, rest, exact);
                return;
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private static List<Claim> ownedBy(ICommandSender sender) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        return ReactorClaims.ownedBy(p.getUniqueID());
    }

    private void admin(MinecraftServer server, ICommandSender sender, List<String> rest, boolean exact) throws CommandException {
        if (rest.isEmpty()) throw new WrongUsageException(USAGE);
        String sub = rest.get(0);
        if ("list".equals(sub) && rest.size() <= 2) {
            List<Claim> claims = ReactorClaims.all();
            if (rest.size() == 2) {
                String name = rest.get(1);
                List<Claim> filtered = new ArrayList<Claim>();
                for (Claim c : claims) {
                    if (name.equalsIgnoreCase(c.ownerName)) filtered.add(c);
                }
                claims = filtered;
            }
            list(sender, claims, exact, true);
        } else if ("unclaim".equals(sub) && rest.size() == 2) {
            unclaim(sender, parseInt(rest.get(1)), true);
        } else if ("keeploaded".equals(sub) && rest.size() == 3) {
            Claim c = ReactorClaims.byId(parseInt(rest.get(1)));
            if (c == null) {
                send(sender, TextFormatting.RED + "No reactor #" + rest.get(1) + ".");
                return;
            }
            String mode = rest.get(2);
            if ("on".equals(mode)) {
                if (!PatchesConfig.reactor.allowKeepLoaded) {
                    send(sender, TextFormatting.RED + "Keep-loaded is disabled (reactor.allowKeepLoaded = false).");
                    return;
                }
                c.keepLoaded = true;
                ReactorClaims.markDirty();
                send(sender, ReactorEvents.PREFIX + "Reactor #" + c.id + ": " + ReactorChunkLoader.enable(c));
            } else if ("off".equals(mode)) {
                c.keepLoaded = false;
                ReactorClaims.markDirty();
                ReactorChunkLoader.disable(c);
                send(sender, ReactorEvents.PREFIX + "Reactor #" + c.id + ": chunks released.");
            } else {
                throw new WrongUsageException(USAGE);
            }
        } else {
            throw new WrongUsageException(USAGE);
        }
    }

    // ---- list

    private static void list(ICommandSender sender, List<Claim> claims, boolean exact, boolean showOwner) {
        if (claims.isEmpty()) {
            send(sender, ReactorEvents.PREFIX + "No claimed reactors.");
            return;
        }
        send(sender, ReactorEvents.PREFIX + claims.size() + " claimed reactor(s):");
        for (Claim c : claims) {
            send(sender, line(c, exact, showOwner));
        }
    }

    static String line(Claim c, boolean exact, boolean showOwner) {
        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.YELLOW).append('#').append(c.id).append(TextFormatting.RESET).append(' ');
        if (showOwner) sb.append(c.ownerName).append(", ");
        sb.append(dimName(c.dim)).append(' ');
        sb.append(exact ? CoordFormat.exact(c.pos.getX(), c.pos.getY(), c.pos.getZ())
                : CoordFormat.estimated(c.pos.getX(), c.pos.getZ(), PatchesConfig.reactor.coordEstimate));
        sb.append(" - ");
        World w = DimensionManager.getWorld(c.dim);
        boolean loaded = w != null && w.isBlockLoaded(c.pos);
        if (c.statusTime == 0) {
            sb.append(TextFormatting.GRAY).append("no status yet");
        } else {
            if (!c.complete) {
                sb.append(TextFormatting.RED).append("incomplete");
                String p = NcCompat.problemText(c.problem);
                if (!p.isEmpty()) sb.append(" (").append(p).append(')');
            } else if (c.processing) {
                sb.append(TextFormatting.GREEN).append("running");
            } else {
                sb.append(TextFormatting.AQUA).append("idle");
                String p = NcCompat.problemText(c.problem);
                if (!p.isEmpty() && !"no problem".equals(p)) sb.append(" (").append(p).append(')');
            }
            sb.append(TextFormatting.RESET).append(", heat ").append(c.heatPercent).append("%, ")
                    .append(c.energy).append(" RF");
        }
        sb.append(TextFormatting.RESET).append(" - ");
        if (loaded) {
            sb.append("loaded");
        } else {
            sb.append(TextFormatting.GRAY).append("not loaded");
            if (c.statusTime != 0) sb.append(", last seen ").append(ago(c.statusTime)).append(" ago");
        }
        if (c.keepLoaded) sb.append(TextFormatting.RESET).append(" [kept loaded]");
        return sb.toString();
    }

    static String dimName(int dim) {
        try {
            return DimensionType.getById(dim).getName();
        } catch (RuntimeException e) {
            return "dim " + dim;
        }
    }

    static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 120) return s + " s";
        if (s < 7200) return (s / 60) + " min";
        if (s < 172800) return (s / 3600) + " h";
        return (s / 86400) + " d";
    }

    // ---- claim

    private static void claim(ICommandSender sender, @Nullable String token) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        int dim;
        BlockPos pos;
        if (token != null) {
            TokenStore.Pending[] out = new TokenStore.Pending[1];
            switch (ReactorClaims.TOKENS.check(token, player.getUniqueID(), System.currentTimeMillis(), out)) {
                case UNKNOWN:
                    send(sender, TextFormatting.RED + "Unknown or already used claim link. Look at the controller and run /reactor claim.");
                    return;
                case EXPIRED:
                    send(sender, TextFormatting.RED + "This claim link has expired. Look at the controller and run /reactor claim.");
                    return;
                case NOT_YOURS:
                    send(sender, TextFormatting.RED + "This claim link belongs to another player.");
                    return;
                default:
                    break;
            }
            dim = out[0].dim;
            pos = new BlockPos(out[0].x, out[0].y, out[0].z);
            if (tryClaim(player, dim, pos)) ReactorClaims.TOKENS.consume(token);
            return;
        }
        World w = player.world;
        Vec3d eyes = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3d end = eyes.add(player.getLookVec().scale(REACH));
        RayTraceResult hit = w.rayTraceBlocks(eyes, end, false, false, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK
                || !FrozenTypes.isController(w.getTileEntity(hit.getBlockPos()))) {
            send(sender, TextFormatting.RED + "Look at a fission controller (within 5 blocks) and try again.");
            return;
        }
        tryClaim(player, w.provider.getDimension(), hit.getBlockPos());
    }

    private static boolean tryClaim(EntityPlayerMP player, int dim, BlockPos pos) {
        World w = DimensionManager.getWorld(dim);
        if (w == null || !w.isBlockLoaded(pos) || !FrozenTypes.isController(w.getTileEntity(pos))) {
            send(player, TextFormatting.RED + "There is no fission controller there any more.");
            return false;
        }
        UUID placer = ReactorClaims.placer(dim, pos);
        if (placer == null) {
            send(player, TextFormatting.RED + "This controller was not placed by a player. Break it and place it again to claim the reactor.");
            return false;
        }
        boolean op = isOp(player);
        if (!placer.equals(player.getUniqueID()) && !op) {
            send(player, TextFormatting.RED + "Only the player who placed this controller can claim it.");
            return false;
        }
        Claim existing = ReactorClaims.at(dim, pos);
        if (existing != null) {
            send(player, TextFormatting.RED + "This reactor is already claimed (#" + existing.id + ", " + existing.ownerName + ").");
            return false;
        }
        int max = PatchesConfig.reactor.maxClaimsPerPlayer;
        if (max > 0 && ReactorClaims.ownedBy(player.getUniqueID()).size() >= max) {
            send(player, TextFormatting.RED + "You already have " + max + " claimed reactor(s). Unclaim one first (/reactor list).");
            return false;
        }
        Claim c = ReactorClaims.create(dim, pos, player.getUniqueID(), player.getName());
        send(player, ReactorEvents.PREFIX + "Reactor #" + c.id + " claimed near "
                + CoordFormat.estimated(pos.getX(), pos.getZ(), PatchesConfig.reactor.coordEstimate)
                + ". Its parts now run. Breaking the controller ends the claim.");
        return true;
    }

    // ---- unclaim

    private static void unclaim(ICommandSender sender, int id, boolean admin) throws CommandException {
        Claim c = ReactorClaims.byId(id);
        if (c == null) {
            send(sender, TextFormatting.RED + "No reactor #" + id + ".");
            return;
        }
        if (!admin) {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            if (!c.owner.equals(p.getUniqueID())) {
                send(sender, TextFormatting.RED + "Reactor #" + id + " is not yours.");
                return;
            }
        }
        ReactorEvents.end(c, admin ? "unclaimed by an operator" : "you unclaimed it");
        if (admin) send(sender, ReactorEvents.PREFIX + "Reactor #" + id + " unclaimed.");
    }

    private static void send(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return isOp(sender) ? getListOfStringsMatchingLastWord(args, "list", "claim", "unclaim", "admin")
                    : getListOfStringsMatchingLastWord(args, "list", "claim", "unclaim");
        }
        if (args.length == 2 && "admin".equals(args[0]) && isOp(sender)) {
            return getListOfStringsMatchingLastWord(args, "list", "unclaim", "keeploaded");
        }
        if (args.length == 4 && "admin".equals(args[0]) && "keeploaded".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        return Collections.emptyList();
    }
}
