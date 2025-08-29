package su.nightexpress.excellentcrates;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.crate.CrateManager;
import su.nightexpress.excellentcrates.key.KeyManager;
import su.nightexpress.excellentcrates.user.CrateUser;
import su.nightexpress.excellentcrates.user.UserManager;
import java.util.UUID;
import su.nightexpress.excellentcrates.sync.RedisSyncManager;

public class CratesAPI {

    private static CratesPlugin plugin;

    static void load(@NotNull CratesPlugin instance) {
        plugin = instance;
    }

    static void clear() {
        plugin = null;
    }

    @NotNull
    public static CratesPlugin getPlugin() {
        return plugin;
    }

    @NotNull
    public static CrateUser getUserData(@NotNull Player player) {
        return plugin.getUserManager().getOrFetch(player);
    }

    @NotNull
    public static UserManager getUserManager() {
        return plugin.getUserManager();
    }

    @NotNull
    public static CrateManager getCrateManager() {
        return plugin.getCrateManager();
    }

    @NotNull
    public static KeyManager getKeyManager() {
        return plugin.getKeyManager();
    }

//    @NotNull
//    public static MenuManager getMenuManager() {
//        return plugin.getMenuManager();
//    }

    public static void info(@NotNull String msg) {
        plugin.info(msg);
    }

    public static void warn(@NotNull String msg) {
        plugin.warn(msg);
    }

    public static void error(@NotNull String msg) {
        plugin.error(msg);
    }

    // API: Cross-server physical key delivery over Redis.
    // If the player is online on this server, the key is given locally.
    // Otherwise, a Redis request is published and whichever server has the player online will deliver it.
    public static boolean givePhysicalKeyCrossServer(@NotNull UUID playerId, @NotNull String keyId, int amount) {
        return plugin.getKeyManager().givePhysicalKeyCrossServer(keyId, playerId, amount);
    }

    // Convenience overload when you already have a Player object.
    public static boolean givePhysicalKeyCrossServer(@NotNull Player player, @NotNull String keyId, int amount) {
        return plugin.getKeyManager().givePhysicalKeyCrossServer(keyId, player.getUniqueId(), amount);
    }

    // Expose this server's Redis node identifier (used as 'origin' in messages).
    public static String getRedisNodeId() {
        return plugin.getRedisSyncManager().map(RedisSyncManager::getNodeId).orElse(null);
    }
}
