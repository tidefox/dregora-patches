package io.github.tidefox.dregorapatches.diag;

/**
 * Attributes a chunk load to its cause for {@code /dpatch chunkloads}. Pure logic.
 * Player-driven loads (PlayerChunkMap, async loads finishing in the ChunkIO queue) are skipped;
 * everything else is credited to the first frame outside Minecraft, Forge, the JDK and Mixin.
 */
public final class StackClassifier {

    private static final String[] INFRA = {
            "java.", "javax.", "sun.", "jdk.", "net.minecraft.", "net.minecraftforge.", "org.spongepowered.",
            "com.llamalad7.", "io.github.tidefox.dregorapatches.", "com.google.", "org.apache.",
    };

    private StackClassifier() {}

    /** @return the attributed frame as "Class.method", "(vanilla) Class.method" if there is none, or null to skip. */
    public static String classify(StackTraceElement[] st) {
        boolean sync = false;
        boolean chunkIo = false;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.endsWith("PlayerChunkMapEntry") || c.endsWith("PlayerChunkMap")) return null;
            if (c.endsWith("ChunkIOExecutor") && "syncChunkLoad".equals(e.getMethodName())) sync = true;
            if (c.endsWith("ChunkIOProvider") || c.endsWith("ChunkIOExecutor")) chunkIo = true;
        }
        if (chunkIo && !sync) return null; // async load completing (queued by the player chunk map)
        for (StackTraceElement e : st) {
            if (!isInfra(e.getClassName())) return e.getClassName() + "." + e.getMethodName();
        }
        // Pure vanilla/Forge cause: name the first frame above the chunk provider.
        boolean pastProvider = false;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.endsWith("ChunkProviderServer")) {
                pastProvider = true;
            } else if (pastProvider && !c.startsWith("java.") && !c.endsWith(".World") && !c.endsWith(".WorldServer")) {
                return "(vanilla) " + simple(c) + "." + e.getMethodName();
            }
        }
        return "(vanilla) unknown";
    }

    private static boolean isInfra(String c) {
        for (String p : INFRA) {
            if (c.startsWith(p)) return true;
        }
        return false;
    }

    private static String simple(String c) {
        int i = c.lastIndexOf('.');
        return i < 0 ? c : c.substring(i + 1);
    }
}
