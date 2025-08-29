package su.nightexpress.excellentcrates.scheduler;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class for FoliaLib to provide Folia support while maintaining compatibility with Spigot/Paper
 */
public class FoliaScheduler {

    private final FoliaLib foliaLib;

    public FoliaScheduler(@NotNull Plugin plugin) {
        this.foliaLib = new FoliaLib(plugin);
    }

    /**
     * Runs a task on the next tick
     * On Folia: Uses GlobalRegionScheduler (safe for global operations only)
     * On Spigot/Paper: Runs on main thread
     */
    public void runNextTick(@NotNull Runnable task) {
        this.foliaLib.getScheduler().runNextTick(wrappedTask -> task.run());
    }

    /**
     * Runs a task asynchronously
     * Works the same on all platforms
     */
    public void runAsync(@NotNull Runnable task) {
        this.foliaLib.getScheduler().runAsync(wrappedTask -> task.run());
    }

    /**
     * Runs a repeating task
     * On Folia: Uses GlobalRegionScheduler
     * On Spigot/Paper: Runs on main thread
     */
    public void runTimer(@NotNull Runnable task, long delay, long period) {
        this.foliaLib.getScheduler().runTimer(wrappedTask -> task.run(), delay, period);
    }

    /**
     * Runs a repeating task asynchronously
     * Works the same on all platforms
     */
    public void runTimerAsync(@NotNull Runnable task, long delay, long period) {
        this.foliaLib.getScheduler().runTimerAsync(wrappedTask -> task.run(), delay, period);
    }

    /**
     * Runs a repeating task asynchronously with TimeUnit
     * Works the same on all platforms
     */
    public void runTimerAsync(@NotNull Runnable task, long delay, long period, @NotNull TimeUnit timeUnit) {
        this.foliaLib.getScheduler().runTimerAsync(wrappedTask -> task.run(), delay, period, timeUnit);
    }

    /**
     * Runs a task at a specific location
     * On Folia: Uses RegionScheduler for the location
     * On Spigot/Paper: Runs on main thread
     */
    public void runAtLocation(@NotNull Location location, @NotNull Runnable task) {
        this.foliaLib.getScheduler().runAtLocation(location, wrappedTask -> task.run());
    }

    /**
     * Runs a task for a specific entity
     * On Folia: Uses EntityScheduler for the entity
     * On Spigot/Paper: Runs on main thread
     */
    public void runAtEntity(@NotNull Entity entity, @NotNull Runnable task) {
        this.foliaLib.getScheduler().runAtEntity(entity, wrappedTask -> task.run());
    }

    /**
     * Teleports an entity asynchronously
     * On Folia: Uses async teleportation
     * On Paper: Uses async teleportation if supported
     * On Spigot: Falls back to next tick teleportation
     */
    public CompletableFuture<Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location) {
        return this.foliaLib.getScheduler().teleportAsync(entity, location);
    }

    /**
     * Teleports an entity asynchronously with cause
     */
    public CompletableFuture<Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location, @Nullable org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return this.foliaLib.getScheduler().teleportAsync(entity, location, cause);
    }

    /**
     * Cancels all tasks associated with this scheduler instance
     * Should be called in onDisable()
     */
    public void cancelAllTasks() {
        this.foliaLib.getScheduler().cancelAllTasks();
    }

    /**
     * Checks if running on Folia
     */
    public boolean isFolia() {
        return this.foliaLib.isFolia();
    }

    /**
     * Checks if running on Paper
     */
    public boolean isPaper() {
        return this.foliaLib.isPaper();
    }

    /**
     * Checks if running on Spigot
     */
    public boolean isSpigot() {
        return this.foliaLib.isSpigot();
    }

    /**
     * Gets the underlying FoliaLib instance for advanced usage
     */
    @NotNull
    public FoliaLib getFoliaLib() {
        return this.foliaLib;
    }
}
