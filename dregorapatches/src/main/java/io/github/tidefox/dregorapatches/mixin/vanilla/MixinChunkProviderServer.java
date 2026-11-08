package io.github.tidefox.dregorapatches.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.tidefox.dregorapatches.nc.NcScope;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import io.github.tidefox.dregorapatches.nc.GuardChunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * docs/07 §3 step 3, chunk guard (only while {@link NcScope#guardActive()}):
 * NC code can't load a chunk (gets an {@link GuardChunk}) and can't rescue a chunk queued for unload.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    @Shadow @Final public WorldServer world;

    /** Last guard chunk handed out: NC scans (findController) hit the same unloaded chunk many times in a row. */
    @Unique private GuardChunk dp$lastGuard;

    /**
     * Reached only when the chunk is not loaded (Forge 2860: right after getLoadedChunk returned null).
     * {@code loadChunk(II Runnable)} is added by Forge, so it has no SRG name: {@code remap = false}.
     */
    @Inject(method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeChunkManager;fetchDormantChunk(JLnet/minecraft/world/World;)Lnet/minecraft/world/chunk/Chunk;"),
            cancellable = true, require = 0)
    private void dp$guardLoad(int x, int z, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        if (NcScope.guardActive()) {
            NcScope.recordBlockedLoad();
            GuardChunk g = this.dp$lastGuard;
            if (g == null || g.x != x || g.z != z) this.dp$lastGuard = g = new GuardChunk(this.world, x, z);
            cir.setReturnValue(g);
        }
    }

    /** {@code chunk.unloadQueued = false} in getLoadedChunk: skipped for queued chunks while NC code runs. */
    @WrapOperation(method = "getLoadedChunk",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;unloadQueued:Z", opcode = Opcodes.PUTFIELD),
            require = 0)
    private void dp$keepQueued(Chunk chunk, boolean value, Operation<Void> original) {
        if (!value && chunk.unloadQueued && NcScope.guardActive()) {
            NcScope.recordKeptQueued();
            return;
        }
        original.call(chunk, value);
    }
}
