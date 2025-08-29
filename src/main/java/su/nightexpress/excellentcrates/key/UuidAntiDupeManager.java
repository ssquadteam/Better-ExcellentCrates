package su.nightexpress.excellentcrates.key;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Keys;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.PDCUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UUID Anti-Dupe Manager for ExcellentCrates
 */
public class UuidAntiDupeManager extends AbstractManager<CratesPlugin> {

    private final Set<UUID> validKeyUuids;
    private final Set<UUID> usedKeyUuids;
    private final ConcurrentHashMap<UUID, Long> uuidCreationTimes;
    
    private long totalKeysGenerated;
    private long totalDupeAttempts;
    private long totalValidUsages;

    public UuidAntiDupeManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        this.validKeyUuids = ConcurrentHashMap.newKeySet();
        this.usedKeyUuids = ConcurrentHashMap.newKeySet();
        this.uuidCreationTimes = new ConcurrentHashMap<>();
        this.totalKeysGenerated = 0;
        this.totalDupeAttempts = 0;
        this.totalValidUsages = 0;
    }

    @Override
    protected void onLoad() {
        this.loadValidUuids();
        this.plugin.info("UUID Anti-Dupe system loaded with " + this.validKeyUuids.size() + " valid UUIDs.");
    }

    @Override
    protected void onShutdown() {
        this.saveData();
        this.validKeyUuids.clear();
        this.usedKeyUuids.clear();
        this.uuidCreationTimes.clear();
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
        
        this.registerValidUuid(keyUuid);
        
        this.totalKeysGenerated++;
        this.plugin.debug("Injected UUID " + keyUuid + " into key item");
        
        return keyUuid;
    }

    /**
     * Gets the UUID from a key item
     * @param keyItem The key item to extract UUID from
     * @return The UUID if present, null otherwise
     */
    @Nullable
    public UUID getKeyUuid(@NotNull ItemStack keyItem) {
        return PDCUtil.getUUID(keyItem, Keys.keyUuid).orElse(null);
    }

    /**
     * Validates if a key item has a valid UUID
     * @param keyItem The key item to validate
     * @param player The player using the key (for logging)
     * @return true if valid, false if invalid/duped
     */
    public boolean validateKeyUuid(@NotNull ItemStack keyItem, @Nullable Player player) {
        UUID keyUuid = this.getKeyUuid(keyItem);
        
        if (keyUuid == null) {
            this.plugin.warn("Key validation failed: No UUID found in key item" + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            return false;
        }

        if (!this.validKeyUuids.contains(keyUuid)) {
            this.totalDupeAttempts++;
            this.plugin.warn("ANTI-DUPE: Invalid key UUID detected: " + keyUuid + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            
            if (player != null) {
                this.notifyAdminsOfDupeAttempt(player, keyUuid);
            }
            
            return false;
        }

        if (this.usedKeyUuids.contains(keyUuid)) {
            this.totalDupeAttempts++;
            this.plugin.warn("ANTI-DUPE: Already used key UUID detected: " + keyUuid + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            
            if (player != null) {
                this.notifyAdminsOfDupeAttempt(player, keyUuid);
            }
            
            return false;
        }

        this.totalValidUsages++;
        return true;
    }

    /**
     * Marks a key UUID as used (consumed)
     * @param keyUuid The UUID to mark as used
     */
    public void markKeyAsUsed(@NotNull UUID keyUuid) {
        this.usedKeyUuids.add(keyUuid);
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().markKeyUuidAsUsed(keyUuid);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishKeyUuidUsed(keyUuid));
        });
        this.plugin.debug("Marked key UUID as used: " + keyUuid);
    }

    /**
     * Registers a UUID as valid in the system
     * @param keyUuid The UUID to register
     */
    public void registerValidUuid(@NotNull UUID keyUuid) {
        this.validKeyUuids.add(keyUuid);
        this.uuidCreationTimes.put(keyUuid, System.currentTimeMillis());
        
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().insertKeyUuid(keyUuid);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishKeyUuidRegistered(keyUuid));
        });
    }

    /**
     * Checks if a UUID is valid and unused
     * @param keyUuid The UUID to check
     * @return true if valid and unused
     */
    public boolean isValidUnusedUuid(@NotNull UUID keyUuid) {
        return this.validKeyUuids.contains(keyUuid) && !this.usedKeyUuids.contains(keyUuid);
    }

    /**
     * Gets statistics about the anti-dupe system
     * @return Array of statistics [generated, dupeAttempts, validUsages, validUuids, usedUuids]
     */
    @NotNull
    public long[] getStatistics() {
        return new long[] {
            this.totalKeysGenerated,
            this.totalDupeAttempts,
            this.totalValidUsages,
            this.validKeyUuids.size(),
            this.usedKeyUuids.size()
        };
    }

    /**
     * Loads valid UUIDs from database
     */
    private void loadValidUuids() {
        this.plugin.runTaskAsync(() -> {
            Set<UUID> validUuids = this.plugin.getDataHandler().loadValidKeyUuids();
            Set<UUID> usedUuids = this.plugin.getDataHandler().loadUsedKeyUuids();
            
            this.plugin.runTask(task -> {
                this.validKeyUuids.addAll(validUuids);
                this.usedKeyUuids.addAll(usedUuids);
                this.plugin.info("Loaded " + validUuids.size() + " valid UUIDs and " + usedUuids.size() + " used UUIDs");
            });
        });
    }

    /**
     * Saves current data to database
     */
    private void saveData() {
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().saveAntiDupeStatistics(
                this.totalKeysGenerated, 
                this.totalDupeAttempts, 
                this.totalValidUsages
            );
        });
    }

    /**
     * Notifies online admins about a dupe attempt
     * @param player The player who attempted to use duped key
     * @param keyUuid The invalid UUID
     */
    private void notifyAdminsOfDupeAttempt(@NotNull Player player, @NotNull UUID keyUuid) {
        String message = "§c[ANTI-DUPE] §f" + player.getName() + " §cattempted to use invalid/duped key UUID: §e" + keyUuid;
        
        this.plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("excellentcrates.admin"))
            .forEach(admin -> admin.sendMessage(message));
        
        this.plugin.warn("Dupe attempt by " + player.getName() + " with UUID: " + keyUuid);
    }

    public void applyExternalKeyUuidRegistered(@NotNull UUID keyUuid) {
        this.validKeyUuids.add(keyUuid);
        this.uuidCreationTimes.put(keyUuid, System.currentTimeMillis());
    }

    public void applyExternalKeyUuidUsed(@NotNull UUID keyUuid) {
        this.usedKeyUuids.add(keyUuid);
    }
}
