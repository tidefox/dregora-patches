package io.github.tidefox.dregorapatches.mixinsupport;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Shared by all our mixin configs. Only logs and records what was loaded/applied. */
public class PatchesMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("dregorapatches");

    @Override
    public void onLoad(String mixinPackage) {
        MixinState.markLoaded(mixinPackage);
        LOGGER.info("Mixin config loaded: {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MixinState.markApplied(mixinInfo.getConfig().getName(), mixinClassName);
        LOGGER.info("Mixin applied: {} -> {}", mixinClassName, targetClassName);
    }
}
