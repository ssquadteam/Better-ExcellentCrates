package su.nightexpress.excellentcrates.key;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.config.Keys;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.PDCUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-dupe manager that scans for duplicate UUIDs in player inventories
 * and nearby players instead of storing UUIDs in memory/database.
 */
public class AntiDupeManager extends AbstractManager<CratesPlugin> {

    private final Set<UUID> recentlyProcessedUuids;
    private long totalDupeAttempts;
    private long totalKeysProcessed;

    public AntiDupeManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        this.recentlyProcessedUuids = ConcurrentHashMap.newKeySet();
        this.totalDupeAttempts = 0;
        this.totalKeysProcessed = 0;
    }

    @Override
    protected void onLoad() {
        this.plugin.info("Anti-Dupe Manager initialized.");
        
        this.plugin.getFoliaScheduler().runTimerAsync(() -> {
            this.recentlyProcessedUuids.clear();
        }, 600L, 600L);
    }

    @Override
    protected void onShutdown() {
        this.saveData();
        this.recentlyProcessedUuids.clear();
    }

    /**
     * Saves current statistics to database
     */
    private void saveData() {
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().saveAntiDupeStatistics(
                this.totalKeysProcessed,
                this.totalDupeAttempts
            );
        });
    }

    /**
     * Injects a unique UUID into a physical key item
     * @param keyItem The key item to inject UUID into
     * @return The UUID that was injected
     */
    @NotNull
    public UUID injectUuid(@NotNull ItemStack keyItem) {
        UUID keyUuid = UUID.randomUUID();
        PDCUtil.set(keyItem, Keys.keyUuid, keyUuid);
        
        this.plugin.debug("Injected UUID " + keyUuid + " into key item");
        return keyUuid;
    }

    /**
     * Gets the UUID from a key item
     * @param keyItem The key item to get UUID from
     * @return The UUID or null if not found
     */
    @Nullable
    public UUID getKeyUuid(@NotNull ItemStack keyItem) {
        return PDCUtil.getUUID(keyItem, Keys.keyUuid).orElse(null);
    }

    /**
     * Validates a key item by checking for duplicate UUIDs in inventory and proximity
     * @param keyItem The key item to validate
     * @param player The player using the key
     * @return true if key is valid (no duplicates found)
     */
    public boolean validateKeyUuid(@NotNull ItemStack keyItem, @Nullable Player player) {
        if (player == null) return true;

        UUID keyUuid = this.getKeyUuid(keyItem);
        if (keyUuid == null) {
            this.plugin.warn("ANTI-DUPE: Key item has no UUID - " + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            return false;
        }

        this.totalKeysProcessed++;

        if (this.recentlyProcessedUuids.contains(keyUuid)) {
            return true;
        }

        if (this.hasDuplicateInInventory(player, keyUuid)) {
            this.handleDupeDetection(player, keyUuid, "inventory");
            return false;
        }

        if (this.hasDuplicateInProximity(player, keyUuid)) {
            this.handleDupeDetection(player, keyUuid, "proximity");
            return false;
        }

        this.recentlyProcessedUuids.add(keyUuid);
        
        return true;
    }

    /**
     * Checks if player's inventory contains multiple items with the same UUID
     * @param player The player to check
     * @param targetUuid The UUID to look for
     * @return true if duplicates found and removed
     */
    private boolean hasDuplicateInInventory(@NotNull Player player, @NotNull UUID targetUuid) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> duplicates = new ArrayList<>();
        boolean foundFirst = false;

        for (ItemStack item : inventory.getContents()) {
            if (item == null) continue;
            
            UUID itemUuid = this.getKeyUuid(item);
            if (targetUuid.equals(itemUuid)) {
                if (foundFirst) {
                    duplicates.add(item);
                } else {
                    foundFirst = true;
                }
            }
        }

        if (!duplicates.isEmpty()) {
            this.plugin.runAtEntity(player, () -> {
                for (ItemStack duplicate : duplicates) {
                    inventory.remove(duplicate);
                }
            });
            
            this.plugin.debug("Removed " + duplicates.size() + " duplicate key items from " + 
                player.getName() + "'s inventory (UUID: " + targetUuid + ")");
            return true;
        }

        return false;
    }

    /**
     * Checks if nearby players have items with the same UUID
     * @param player The player to check around
     * @param targetUuid The UUID to look for
     * @return true if duplicates found in proximity
     */
    private boolean hasDuplicateInProximity(@NotNull Player player, @NotNull UUID targetUuid) {
        double radius = Config.ANTI_DUPE_PROXIMITY_RADIUS.get();
        Location playerLoc = player.getLocation();
        
        for (Player nearbyPlayer : this.getNearbyPlayers(player, radius)) {
            if (nearbyPlayer.equals(player)) continue;
            
            if (this.hasUuidInInventory(nearbyPlayer, targetUuid)) {
                this.plugin.debug("Found duplicate UUID " + targetUuid + " in nearby player " + 
                    nearbyPlayer.getName() + "'s inventory");
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if a player's inventory contains an item with the specified UUID
     * @param player The player to check
     * @param targetUuid The UUID to look for
     * @return true if UUID found in inventory
     */
    private boolean hasUuidInInventory(@NotNull Player player, @NotNull UUID targetUuid) {
        PlayerInventory inventory = player.getInventory();
        
        for (ItemStack item : inventory.getContents()) {
            if (item == null) continue;
            
            UUID itemUuid = this.getKeyUuid(item);
            if (targetUuid.equals(itemUuid)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Gets nearby players within the specified radius
     * @param player The center player
     * @param radius The radius to search
     * @return List of nearby players
     */
    @NotNull
    private List<Player> getNearbyPlayers(@NotNull Player player, double radius) {
        List<Player> nearby = new ArrayList<>();
        Location playerLoc = player.getLocation();
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) continue;
            if (!onlinePlayer.getWorld().equals(player.getWorld())) continue;
            
            if (onlinePlayer.getLocation().distance(playerLoc) <= radius) {
                nearby.add(onlinePlayer);
            }
        }
        
        return nearby;
    }

    /**
     * Handles dupe detection by removing duplicates and notifying admins
     * @param player The player who attempted to use duped key
     * @param keyUuid The UUID of the duped key
     * @param detectionType Where the dupe was detected (inventory/proximity)
     */
    private void handleDupeDetection(@NotNull Player player, @NotNull UUID keyUuid, @NotNull String detectionType) {
        this.totalDupeAttempts++;
        
        String message = "ANTI-DUPE: Duplicate key UUID detected in " + detectionType + 
            " - UUID: " + keyUuid + " (Player: " + player.getName() + " / " + player.getUniqueId() + ")";
        
        this.plugin.warn(message);
        
        this.notifyAdminsOfDupeAttempt(player, keyUuid, detectionType);
        
        this.plugin.getRedisSyncManager().ifPresent(sync -> 
            sync.publishDupeDetection(player.getUniqueId(), player.getName(), keyUuid, detectionType));
        
        this.recentlyProcessedUuids.add(keyUuid);
    }

    /**
     * Notifies admins of a dupe attempt
     * @param player The player who attempted to use duped key
     * @param keyUuid The UUID of the duped key
     * @param detectionType Where the dupe was detected
     */
    private void notifyAdminsOfDupeAttempt(@NotNull Player player, @NotNull UUID keyUuid, @NotNull String detectionType) {
        String adminMessage = "§c[ExcellentCrates] §fAttempted Dupe Detected!\n" +
            "§7Player: §f" + player.getName() + " §7(" + player.getUniqueId() + ")\n" +
            "§7Key UUID: §f" + keyUuid + "\n" +
            "§7Detection: §f" + detectionType + "\n" +
            "§7Key Removed: §aYes";

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("excellentcrates.admin") || admin.hasPermission("excellentcrates.antidupe.notify")) {
                admin.sendMessage(adminMessage);
            }
        }
    }

    /**
     * Compatibility method - always returns current time since we don't track creation times
     * @param keyUuid The UUID to check (ignored)
     * @return Current timestamp
     */
    public long getCreationTime(@NotNull UUID keyUuid) {
        return System.currentTimeMillis(); // In new system, we don't track creation times
    }

    /**
     * Compatibility method - always returns true since we don't track UUID states
     * @param keyUuid The UUID to check (ignored)
     * @return Always true
     */
    public boolean isValidUnusedUuid(@NotNull UUID keyUuid) {
        return true;
    }

    /**
     * Gets statistics about the anti-dupe system
     * @return Array of statistics [keysProcessed, dupeAttempts, recentlyProcessedCount]
     */
    @NotNull
    public long[] getStatistics() {
        return new long[] {
            this.totalKeysProcessed,
            this.totalDupeAttempts,
            this.recentlyProcessedUuids.size()
        };
    }

    /**
     * Handles external dupe detection from Redis
     * @param playerUuid The UUID of the player who attempted dupe
     * @param playerName The name of the player
     * @param keyUuid The UUID of the duped key
     * @param detectionType Where the dupe was detected
     */
    public void handleExternalDupeDetection(@NotNull UUID playerUuid, @NotNull String playerName, 
                                          @NotNull UUID keyUuid, @NotNull String detectionType) {
        String message = "ANTI-DUPE (Cross-Server): Duplicate key detected on another server - " +
            "Player: " + playerName + " (" + playerUuid + "), UUID: " + keyUuid + ", Type: " + detectionType;
        
        this.plugin.warn(message);
        
        String adminMessage = "§c[ExcellentCrates] §fCross-Server Dupe Detected!\n" +
            "§7Player: §f" + playerName + " §7(" + playerUuid + ")\n" +
            "§7Key UUID: §f" + keyUuid + "\n" +
            "§7Detection: §f" + detectionType + " (other server)\n" +
            "§7Action: §fLogged";

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("excellentcrates.admin") || admin.hasPermission("excellentcrates.antidupe.notify")) {
                admin.sendMessage(adminMessage);
            }
        }
    }
}
