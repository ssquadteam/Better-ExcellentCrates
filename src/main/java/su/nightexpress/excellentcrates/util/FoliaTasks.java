package su.nightexpress.excellentcrates.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Folia region hops. NightCore {@code runNextTick}/{@code runTask(Runnable)} use the
 * global region and are unsafe for player inventory or world access.
 */
public final class FoliaTasks {

    private FoliaTasks() {
    }

    public static void runAtPlayer(@NotNull CratesPlugin plugin, @NotNull Player player, @NotNull Runnable runnable) {
        plugin.getFoliaScheduler().runAtEntity(player, runnable);
    }

    public static void runAtLocation(@NotNull CratesPlugin plugin, @NotNull Location location, @NotNull Runnable runnable) {
        plugin.getFoliaScheduler().runAtLocation(location, runnable);
    }

    public static void runForOnlinePlayer(@NotNull CratesPlugin plugin, @NotNull UUID playerId, @NotNull Consumer<Player> action) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        plugin.getFoliaScheduler().runAtEntity(player, () -> action.accept(player));
    }

    public static void runForOnlinePlayer(@NotNull CratesPlugin plugin, @Nullable String playerName, @NotNull Consumer<Player> action) {
        if (playerName == null || playerName.isBlank()) return;
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) return;
        plugin.getFoliaScheduler().runAtEntity(player, () -> action.accept(player));
    }
}
